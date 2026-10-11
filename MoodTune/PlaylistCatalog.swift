import Foundation

enum PlaylistCatalog {
    static func fallback(for mood: Mood) -> [PlaylistSuggestion] {
        var items: [PlaylistSuggestion] = []
        if let editorial = editorial[mood] {
            items.append(editorial)
        }
        items.append(searchLink(for: mood))
        return items
    }

    static func searchLink(for mood: Mood) -> PlaylistSuggestion {
        let encoded = mood.searchQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? mood.searchQuery
        return PlaylistSuggestion(
            id: "search-\(mood.rawValue)",
            name: mood.searchQuery.capitalized,
            detail: "Search in Spotify",
            uri: "spotify:search:\(encoded)",
            webURL: URL(string: "https://open.spotify.com/search/\(encoded)")!,
            imageURL: nil,
            trackCount: nil,
            isSearchLink: true
        )
    }

    /// Public editorial playlists. Search results replace these after Spotify connects.
    private static let editorial: [Mood: PlaylistSuggestion] = [
        .joyful: playlist(
            id: "37i9dQZF1DX3rxVfibe1L0",
            name: "Mood Booster",
            detail: "Spotify editorial"
        ),
        .calm: playlist(
            id: "37i9dQZF1DX4sWSpwq3LiO",
            name: "Peaceful Piano",
            detail: "Spotify editorial"
        ),
        .focused: playlist(
            id: "37i9dQZF1DWZeKCadgRdKQ",
            name: "Deep Focus",
            detail: "Spotify editorial"
        ),
        .energetic: playlist(
            id: "37i9dQZF1DX76Wlfdnj7AP",
            name: "Beast Mode",
            detail: "Spotify editorial"
        ),
        .stressed: playlist(
            id: "37i9dQZF1DX4WYpdgoIcn6",
            name: "Chill Hits",
            detail: "Spotify editorial"
        ),
        .tired: playlist(
            id: "37i9dQZF1DWZd79rJ6a7lp",
            name: "Sleep",
            detail: "Spotify editorial"
        ),
    ]

    private static func playlist(id: String, name: String, detail: String) -> PlaylistSuggestion {
        PlaylistSuggestion(
            id: id,
            name: name,
            detail: detail,
            uri: "spotify:playlist:\(id)",
            webURL: URL(string: "https://open.spotify.com/playlist/\(id)")!,
            imageURL: nil,
            trackCount: nil,
            isSearchLink: false
        )
    }
}
