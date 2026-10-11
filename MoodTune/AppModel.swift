import Foundation
import UIKit

@MainActor
@Observable
final class AppModel {
    var messages: [ChatMessage] = []
    var assessment: MoodAssessment = .waiting
    var snapshot: HealthSnapshot?
    var healthStatus: HealthAccessStatus = .unknown
    var playlists: [PlaylistSuggestion] = []
    var spotifyName: String?
    var isSpotifyConnected = false
    var isLoadingPlaylists = false
    var isPlaying = false
    var statusMessage: String?
    var didFinishOnboarding: Bool {
        didSet { defaults.set(didFinishOnboarding, forKey: Keys.onboarding) }
    }
    var useSampleHealthData: Bool {
        didSet { defaults.set(useSampleHealthData, forKey: Keys.sample) }
    }
    var clientID: String {
        didSet { defaults.set(clientID, forKey: Keys.clientID) }
    }

    private let health = HealthService()
    private let spotify = SpotifyClient()
    private let defaults = UserDefaults.standard
    private var didBootstrap = false
    private var playlistMood: Mood?

    init() {
        clientID = defaults.string(forKey: Keys.clientID) ?? ""
        useSampleHealthData = defaults.object(forKey: Keys.sample) as? Bool ?? true
        didFinishOnboarding = defaults.bool(forKey: Keys.onboarding)
        if let data = defaults.data(forKey: Keys.messages),
           let stored = try? JSONDecoder().decode([ChatMessage].self, from: data),
           !stored.isEmpty {
            messages = stored
        } else {
            messages = [
                ChatMessage(
                    role: .assistant,
                    text: "Tell me how you feel. A sentence is enough — tired, wired, calm, stuck — and I’ll pick the music."
                )
            ]
        }
        isSpotifyConnected = spotify.hasStoredSession()
        if useSampleHealthData {
            snapshot = .sample
        }
        recompute(resetPlaylists: true)
    }

    func setUseSampleHealthData(_ enabled: Bool) async {
        useSampleHealthData = enabled
        if !enabled, snapshot?.isSampleData == true {
            snapshot = nil
        }
        await refreshHealth(requestAuthorization: false)
        await refreshMusic()
    }

    func bootstrap() async {
        guard !didBootstrap else { return }
        didBootstrap = true
        await restoreSpotifyProfile()
        await refreshHealth(requestAuthorization: false)
        await refreshMusic()
    }

    func refreshHealth(requestAuthorization: Bool) async {
        let result = await health.loadSnapshot(requestAuthorization: requestAuthorization)
        healthStatus = result.status
        if let snapshot = result.snapshot, snapshot.hasAnySignal {
            self.snapshot = snapshot
        } else if useSampleHealthData {
            snapshot = .sample
            if healthStatus == .unknown {
                healthStatus = .empty
            }
        } else {
            snapshot = result.snapshot
        }
        recompute(resetPlaylists: true)
    }

    func finishOnboarding() async {
        didFinishOnboarding = true
        await refreshHealth(requestAuthorization: true)
        await refreshMusic()
    }

    func skipOnboarding() async {
        didFinishOnboarding = true
        await refreshHealth(requestAuthorization: false)
        await refreshMusic()
    }

    func send(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        messages.append(ChatMessage(role: .user, text: trimmed))
        recompute(resetPlaylists: false)
        messages.append(ChatMessage(role: .assistant, text: MoodEngine.assistantReply(assessment: assessment)))
        persistMessages()
        await refreshMusic()
    }

    func connectSpotify() async {
        statusMessage = nil
        do {
            try await spotify.connect(clientID: clientID)
            isSpotifyConnected = true
            let token = try await spotify.validAccessToken(clientID: clientID)
            spotifyName = try await spotify.profileName(token: token)
            await refreshMusic()
        } catch SpotifyError.cancelled {
            return
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    func disconnectSpotify() {
        spotify.disconnect()
        isSpotifyConnected = false
        spotifyName = nil
        playlists = PlaylistCatalog.fallback(for: assessment.mood)
        playlistMood = assessment.mood
        statusMessage = "Disconnected from Spotify."
    }

    func play(_ playlist: PlaylistSuggestion) async {
        guard !isPlaying else { return }
        isPlaying = true
        defer { isPlaying = false }

        if !playlist.isSearchLink, isSpotifyConnected {
            do {
                let token = try await spotify.validAccessToken(clientID: clientID)
                try await spotify.startPlayback(playlistURI: playlist.uri, token: token)
                statusMessage = "Playing \(playlist.name) in Spotify."
                return
            } catch SpotifyError.premiumRequired {
                let route = await openExternally(playlist)
                statusMessage = "Premium is required to start playback from MoodTune. \(openedPhrase(route))"
                return
            } catch SpotifyError.noPlaybackDevice {
                let route = await openExternally(playlist)
                statusMessage = "No active Spotify device. \(openedPhrase(route))"
                return
            } catch {
                let route = await openExternally(playlist)
                statusMessage = openedPhrase(route)
                return
            }
        }

        let route = await openExternally(playlist)
        statusMessage = openedPhrase(route)
    }

    func refreshMusic() async {
        let mood = assessment.mood
        if playlistMood != mood || playlists.isEmpty {
            playlists = PlaylistCatalog.fallback(for: mood)
            playlistMood = mood
        }

        guard isSpotifyConnected else {
            await enrichArtwork(for: mood)
            return
        }

        isLoadingPlaylists = true
        defer {
            if assessment.mood == mood {
                isLoadingPlaylists = false
            }
        }

        do {
            let token = try await spotify.validAccessToken(clientID: clientID)
            let found = try await spotify.searchPlaylists(query: mood.searchQuery, token: token)
            guard assessment.mood == mood else { return }
            if !found.isEmpty {
                playlists = found
                playlistMood = mood
            }
        } catch {
            guard assessment.mood == mood else { return }
            statusMessage = error.localizedDescription
        }
        await enrichArtwork(for: mood)
    }

    private func recompute(resetPlaylists: Bool) {
        let previous = assessment.mood
        assessment = MoodEngine.assess(health: snapshot, messages: messages)
        if resetPlaylists || previous != assessment.mood {
            playlists = PlaylistCatalog.fallback(for: assessment.mood)
            playlistMood = assessment.mood
        }
    }

    private func restoreSpotifyProfile() async {
        guard isSpotifyConnected else { return }
        do {
            let token = try await spotify.validAccessToken(clientID: clientID)
            spotifyName = try await spotify.profileName(token: token)
        } catch {
            isSpotifyConnected = spotify.hasStoredSession()
        }
    }

    private func openExternally(_ playlist: PlaylistSuggestion) async -> PlaybackRoute {
        if let appURL = playlist.spotifyAppURL, UIApplication.shared.canOpenURL(appURL) {
            await UIApplication.shared.open(appURL)
            return .spotifyApp
        }
        await UIApplication.shared.open(playlist.webURL)
        return .web
    }

    private func openedPhrase(_ route: PlaybackRoute) -> String {
        switch route {
        case .remote: "Playing in Spotify."
        case .spotifyApp: "Opened in the Spotify app."
        case .web: "Opened Spotify on the web."
        }
    }

    private func enrichArtwork(for mood: Mood) async {
        for index in playlists.indices {
            guard assessment.mood == mood else { return }
            guard playlists[index].imageURL == nil, !playlists[index].isSearchLink else { continue }
            if let imageURL = await SpotifyArtwork.thumbnail(for: playlists[index].webURL) {
                guard assessment.mood == mood, playlists.indices.contains(index) else { return }
                var updated = playlists
                updated[index].imageURL = imageURL
                playlists = updated
            }
        }
    }

    private func persistMessages() {
        let recent = Array(messages.suffix(40))
        messages = recent
        if let data = try? JSONEncoder().encode(recent) {
            defaults.set(data, forKey: Keys.messages)
        }
    }

    private enum Keys {
        static let clientID = "spotifyClientID"
        static let sample = "useSampleHealthData"
        static let onboarding = "didFinishOnboarding"
        static let messages = "chatMessages"
    }
}

enum SpotifyArtwork {
    static func thumbnail(for playlistURL: URL) async -> URL? {
        var components = URLComponents(string: "https://open.spotify.com/oembed")
        components?.queryItems = [URLQueryItem(name: "url", value: playlistURL.absoluteString)]
        guard let url = components?.url else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            let decoded = try JSONDecoder().decode(OEmbed.self, from: data)
            return decoded.thumbnail_url.flatMap(URL.init(string:))
        } catch {
            return nil
        }
    }

    private struct OEmbed: Decodable {
        var thumbnail_url: String?
    }
}
