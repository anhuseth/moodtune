import Foundation

enum Mood: String, CaseIterable, Codable, Equatable, Identifiable {
    case joyful
    case calm
    case focused
    case energetic
    case melancholic
    case stressed
    case tired

    var id: String { rawValue }

    var title: String {
        switch self {
        case .joyful: "Joyful"
        case .calm: "Calm"
        case .focused: "Focused"
        case .energetic: "Energetic"
        case .melancholic: "Melancholic"
        case .stressed: "Stressed"
        case .tired: "Tired"
        }
    }

    var symbol: String {
        switch self {
        case .joyful: "sun.max.fill"
        case .calm: "leaf.fill"
        case .focused: "scope"
        case .energetic: "bolt.fill"
        case .melancholic: "cloud.rain.fill"
        case .stressed: "waveform.path.ecg"
        case .tired: "moon.stars.fill"
        }
    }

    /// Spotify search phrase used when looking up playlists.
    var searchQuery: String {
        switch self {
        case .joyful: "happy hits"
        case .calm: "peaceful piano"
        case .focused: "deep focus"
        case .energetic: "workout beast mode"
        case .melancholic: "sad songs"
        case .stressed: "stress relief"
        case .tired: "sleep"
        }
    }

    var playButtonTitle: String {
        switch self {
        case .stressed:
            "Play soothing music"
        default:
            "Play \(title.lowercased()) music"
        }
    }

    var playlistPitch: String {
        switch self {
        case .joyful: "something upbeat"
        case .calm: "a quieter playlist"
        case .focused: "music for concentration"
        case .energetic: "a high-energy mix"
        case .melancholic: "something slower"
        case .stressed: "a low-pressure playlist"
        case .tired: "a wind-down playlist"
        }
    }
}

struct HealthSnapshot: Equatable {
    var stepsToday: Double?
    var activeCalories: Double?
    var latestHeartRate: Double?
    var heartRateVariabilityMS: Double?
    var sleepHours: Double?
    var mindfulMinutes: Double?
    var recentWorkoutMinutes: Double?
    var hoursSinceWorkout: Double?
    var isSampleData: Bool
    var fetchedAt: Date

    var hasAnySignal: Bool {
        stepsToday != nil
            || activeCalories != nil
            || latestHeartRate != nil
            || heartRateVariabilityMS != nil
            || sleepHours != nil
            || mindfulMinutes != nil
            || recentWorkoutMinutes != nil
    }

    static let empty = HealthSnapshot(
        stepsToday: nil,
        activeCalories: nil,
        latestHeartRate: nil,
        heartRateVariabilityMS: nil,
        sleepHours: nil,
        mindfulMinutes: nil,
        recentWorkoutMinutes: nil,
        hoursSinceWorkout: nil,
        isSampleData: false,
        fetchedAt: .now
    )

    /// Used when Apple Health has no samples yet, so the mood screen is still demonstrable.
    static let sample = HealthSnapshot(
        stepsToday: 3_200,
        activeCalories: 180,
        latestHeartRate: 88,
        heartRateVariabilityMS: 28,
        sleepHours: 5.2,
        mindfulMinutes: 0,
        recentWorkoutMinutes: nil,
        hoursSinceWorkout: nil,
        isSampleData: true,
        fetchedAt: .now
    )
}

struct MoodAssessment: Equatable {
    var mood: Mood
    var confidence: Double
    var reasons: [String]
    var summary: String
    var usedHealth: Bool
    var usedChat: Bool
    var latestMessageHadSignal: Bool

    static let waiting = MoodAssessment(
        mood: .calm,
        confidence: 0.2,
        reasons: [],
        summary: "Tell me how you feel, or allow Health access and I’ll read the day.",
        usedHealth: false,
        usedChat: false,
        latestMessageHadSignal: false
    )
}

enum ChatRole: String, Codable {
    case user
    case assistant
}

struct ChatMessage: Identifiable, Equatable, Codable {
    var id: UUID
    var role: ChatRole
    var text: String
    var date: Date

    init(id: UUID = UUID(), role: ChatRole, text: String, date: Date = .now) {
        self.id = id
        self.role = role
        self.text = text
        self.date = date
    }
}

struct PlaylistSuggestion: Identifiable, Equatable {
    var id: String
    var name: String
    var detail: String
    var uri: String
    var webURL: URL
    var imageURL: URL?
    var trackCount: Int?
    var isSearchLink: Bool

    var spotifyAppURL: URL? {
        if isSearchLink {
            let query = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? name
            return URL(string: "spotify:search:\(query)")
        }
        return URL(string: uri)
    }
}

enum PlaybackRoute: Equatable {
    case remote
    case spotifyApp
    case web
}
