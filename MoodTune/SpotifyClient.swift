import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

enum SpotifyError: LocalizedError {
    case missingClientID
    case cancelled
    case authFailed(String)
    case http(Int, String)
    case premiumRequired
    case noPlaybackDevice

    var errorDescription: String? {
        switch self {
        case .missingClientID:
            "Add your Spotify Client ID in settings."
        case .cancelled:
            "Spotify sign-in was cancelled."
        case .authFailed(let message):
            message
        case .http(_, let message):
            message
        case .premiumRequired:
            "Spotify Premium is required to start playback remotely."
        case .noPlaybackDevice:
            "No active Spotify device."
        }
    }
}

@MainActor
final class SpotifyClient: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let redirectURI = "moodtune://callback"

    private let session: URLSession
    private var authSession: ASWebAuthenticationSession?
    private let tokenAccount = "spotify.tokens"

    override init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        session = URLSession(configuration: configuration)
        super.init()
    }

    func hasStoredSession() -> Bool {
        loadTokens() != nil
    }

    func disconnect() {
        KeychainStore.remove(tokenAccount)
    }

    func connect(clientID: String) async throws {
        let trimmed = clientID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw SpotifyError.missingClientID }

        let verifier = Self.randomVerifier()
        let challenge = Self.challenge(for: verifier)
        var components = URLComponents(string: "https://accounts.spotify.com/authorize")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: trimmed),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "redirect_uri", value: Self.redirectURI),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "scope", value: "user-read-private user-read-email user-modify-playback-state user-read-playback-state playlist-read-private"),
        ]
        guard let url = components.url else {
            throw SpotifyError.authFailed("Could not build the Spotify login URL.")
        }

        let callback = try await startWebAuth(url: url)
        guard let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems else {
            throw SpotifyError.authFailed("Spotify did not return a login code.")
        }
        if let error = items.first(where: { $0.name == "error" })?.value {
            throw SpotifyError.authFailed(error)
        }
        guard let code = items.first(where: { $0.name == "code" })?.value else {
            throw SpotifyError.authFailed("Spotify did not return a login code.")
        }

        let tokens = try await exchange(
            form: [
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": Self.redirectURI,
                "client_id": trimmed,
                "code_verifier": verifier,
            ]
        )
        try KeychainStore.set(JSONEncoder().encode(tokens), for: tokenAccount)
    }

    func validAccessToken(clientID: String) async throws -> String {
        guard var tokens = loadTokens() else { throw SpotifyError.authFailed("Connect Spotify first.") }
        if tokens.expiresAt.timeIntervalSinceNow > 60 {
            return tokens.accessToken
        }
        guard let refresh = tokens.refreshToken else {
            throw SpotifyError.authFailed("Spotify session expired. Connect again.")
        }
        let trimmed = clientID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw SpotifyError.missingClientID }
        tokens = try await exchange(
            form: [
                "grant_type": "refresh_token",
                "refresh_token": refresh,
                "client_id": trimmed,
            ],
            previousRefreshToken: refresh
        )
        try KeychainStore.set(JSONEncoder().encode(tokens), for: tokenAccount)
        return tokens.accessToken
    }

    func profileName(token: String) async throws -> String {
        let data = try await request(url: URL(string: "https://api.spotify.com/v1/me")!, method: "GET", token: token, body: nil)
        let profile = try JSONDecoder().decode(ProfileResponse.self, from: data)
        return profile.display_name ?? profile.id
    }

    func searchPlaylists(query: String, token: String) async throws -> [PlaylistSuggestion] {
        var components = URLComponents(string: "https://api.spotify.com/v1/search")!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: "playlist"),
            URLQueryItem(name: "limit", value: "8"),
        ]
        guard let url = components.url else { return [] }
        let data = try await request(url: url, method: "GET", token: token, body: nil)
        let decoded = try JSONDecoder().decode(SearchResponse.self, from: data)
        return decoded.playlists.items.compactMap { item in
            guard let item else { return nil }
            return PlaylistSuggestion(
                id: item.id,
                name: item.name,
                detail: item.owner?.display_name ?? "Spotify",
                uri: item.uri,
                webURL: URL(string: "https://open.spotify.com/playlist/\(item.id)")!,
                imageURL: item.images?.first.flatMap { URL(string: $0.url) },
                trackCount: item.tracks?.total,
                isSearchLink: false
            )
        }
    }

    func startPlayback(playlistURI: String, token: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["context_uri": playlistURI])
        do {
            _ = try await request(
                url: URL(string: "https://api.spotify.com/v1/me/player/play")!,
                method: "PUT",
                token: token,
                body: body,
                acceptEmpty: true
            )
        } catch SpotifyError.noPlaybackDevice {
            let deviceID = try await firstDeviceID(token: token)
            var components = URLComponents(string: "https://api.spotify.com/v1/me/player/play")!
            components.queryItems = [URLQueryItem(name: "device_id", value: deviceID)]
            guard let url = components.url else { throw SpotifyError.noPlaybackDevice }
            _ = try await request(url: url, method: "PUT", token: token, body: body, acceptEmpty: true)
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        if let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow) {
            return window
        }
        if let scene = scenes.first {
            return UIWindow(windowScene: scene)
        }
        return UIWindow()
    }

    private func firstDeviceID(token: String) async throws -> String {
        let data = try await request(
            url: URL(string: "https://api.spotify.com/v1/me/player/devices")!,
            method: "GET",
            token: token,
            body: nil
        )
        let devices = try JSONDecoder().decode(DevicesResponse.self, from: data)
        if let active = devices.devices.first(where: \.is_active) ?? devices.devices.first {
            return active.id
        }
        throw SpotifyError.noPlaybackDevice
    }

    private func startWebAuth(url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: "moodtune") { callback, error in
                if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    continuation.resume(throwing: SpotifyError.cancelled)
                    return
                }
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let callback else {
                    continuation.resume(throwing: SpotifyError.authFailed("Spotify did not return from login."))
                    return
                }
                continuation.resume(returning: callback)
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.authSession = session
            if !session.start() {
                continuation.resume(throwing: SpotifyError.authFailed("Could not open Spotify login."))
            }
        }
    }

    private func exchange(form: [String: String], previousRefreshToken: String? = nil) async throws -> SpotifyTokens {
        var request = URLRequest(url: URL(string: "https://accounts.spotify.com/api/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = form
            .map { key, value in
                "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value)"
            }
            .joined(separator: "&")
            .data(using: .utf8)

        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let message = String(data: data, encoding: .utf8) ?? "Spotify login failed."
            throw SpotifyError.authFailed(message)
        }
        let decoded = try JSONDecoder().decode(TokenResponse.self, from: data)
        return SpotifyTokens(
            accessToken: decoded.access_token,
            refreshToken: decoded.refresh_token ?? previousRefreshToken,
            expiresAt: Date().addingTimeInterval(TimeInterval(decoded.expires_in))
        )
    }

    private func request(url: URL, method: String, token: String, body: Data?, acceptEmpty: Bool = false) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 204 || (acceptEmpty && data.isEmpty && (200..<300).contains(status)) {
            return data
        }
        guard (200..<300).contains(status) else {
            let raw = String(data: data, encoding: .utf8) ?? ""
            if status == 404 {
                throw SpotifyError.noPlaybackDevice
            }
            if status == 403 {
                throw SpotifyError.premiumRequired
            }
            throw SpotifyError.http(status, raw.isEmpty ? "Spotify request failed." : raw)
        }
        return data
    }

    private func loadTokens() -> SpotifyTokens? {
        guard let data = KeychainStore.data(for: tokenAccount) else { return nil }
        return try? JSONDecoder().decode(SpotifyTokens.self, from: data)
    }

    private static func randomVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64URLEncodedString()
    }

    private static func challenge(for verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.utf8))
        return Data(digest).base64URLEncodedString()
    }
}

private struct SpotifyTokens: Codable {
    var accessToken: String
    var refreshToken: String?
    var expiresAt: Date
}

private struct TokenResponse: Decodable {
    var access_token: String
    var refresh_token: String?
    var expires_in: Int
}

private struct ProfileResponse: Decodable {
    var id: String
    var display_name: String?
}

private struct SearchResponse: Decodable {
    struct Playlists: Decodable {
        var items: [Item?]
    }

    struct Item: Decodable {
        var id: String
        var name: String
        var uri: String
        var images: [Image]?
        var tracks: Tracks?
        var owner: Owner?

        struct Image: Decodable { var url: String }
        struct Tracks: Decodable { var total: Int }
        struct Owner: Decodable { var display_name: String? }
    }

    var playlists: Playlists
}

private struct DevicesResponse: Decodable {
    struct Device: Decodable {
        var id: String
        var is_active: Bool
    }

    var devices: [Device]
}

enum KeychainStore {
    static func set(_ data: Data, for key: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.moodtune.app",
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
        var insert = query
        insert[kSecValueData as String] = data
        let status = SecItemAdd(insert as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SpotifyError.authFailed("Could not save the Spotify session.")
        }
    }

    static func data(for key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.moodtune.app",
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return item as? Data
    }

    static func remove(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.moodtune.app",
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
