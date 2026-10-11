import Foundation

enum MoodEngine {
    static func assess(health: HealthSnapshot?, messages: [ChatMessage]) -> MoodAssessment {
        let healthResult = scoreHealth(health)
        let userMessages = messages.filter { $0.role == .user }
        let chatResult = scoreChat(userMessages)
        let lastSignal = userMessages.last.map { scoreText($0.text).maxScore > 0 } ?? false

        let healthMax = healthResult.scores.values.max() ?? 0
        let chatMax = chatResult.scores.values.max() ?? 0
        let hasHealth = health?.hasAnySignal == true && healthMax > 0
        let hasChat = chatMax > 0

        var healthWeight = 0.0
        var chatWeight = 0.0
        if hasHealth && hasChat {
            if chatMax >= 0.8 {
                healthWeight = 0.28
                chatWeight = 0.72
            } else {
                healthWeight = 0.55
                chatWeight = 0.45
            }
            if health?.isSampleData == true {
                healthWeight *= 0.45
                let total = healthWeight + chatWeight
                healthWeight /= total
                chatWeight /= total
            }
        } else if hasChat {
            chatWeight = 1
        } else if hasHealth {
            healthWeight = 1
        }

        var combined = Dictionary(uniqueKeysWithValues: Mood.allCases.map { ($0, 0.0) })
        for mood in Mood.allCases {
            combined[mood] = (healthResult.scores[mood] ?? 0) * healthWeight
                + (chatResult.scores[mood] ?? 0) * chatWeight
        }

        let ranked = combined.sorted { $0.value > $1.value }
        let topValue = ranked.first?.value ?? 0
        guard topValue > 0, let winner = ranked.first?.key else {
            return .waiting
        }

        let second = ranked.dropFirst().first?.value ?? 0
        let margin = topValue - second
        var confidence = 0.28 + min(0.45, margin * 0.35) + min(0.22, topValue * 0.12)
        if hasChat { confidence += 0.08 }
        if hasHealth && health?.isSampleData == false { confidence += 0.08 }
        confidence = min(0.94, confidence)

        var reasons = healthResult.reasons
            .filter { $0.mood == winner }
            .map(\.text)
        if hasChat, (chatResult.scores[winner] ?? 0) > 0 {
            reasons.insert("You described feeling \(winner.title.lowercased()).", at: 0)
        }

        let summary: String
        if hasHealth && hasChat {
            summary = "Health data and what you said both lean \(winner.title.lowercased())."
        } else if hasChat {
            summary = "Mostly from what you just said."
        } else if health?.isSampleData == true {
            summary = "A sample day reads as \(winner.title.lowercased()). Your own Health data will replace this."
        } else {
            summary = "Mostly from your health data."
        }

        return MoodAssessment(
            mood: winner,
            confidence: confidence,
            reasons: Array(reasons.prefix(3)),
            summary: summary,
            usedHealth: hasHealth,
            usedChat: hasChat,
            latestMessageHadSignal: lastSignal
        )
    }

    static func assistantReply(assessment: MoodAssessment) -> String {
        let pitch = "I lined up \(assessment.mood.playlistPitch)."
        guard assessment.latestMessageHadSignal else {
            if assessment.usedHealth {
                return "I couldn't read a clear mood in that. Your health data still points to \(assessment.mood.title.lowercased()). \(pitch)"
            }
            return "Say it a bit more plainly — happy, calm, anxious, tired, or focused. I'll match the music."
        }

        let lead: String
        switch assessment.mood {
        case .joyful: lead = "That sounds bright."
        case .calm: lead = "That sounds settled."
        case .focused: lead = "You're in a heads-down mood."
        case .energetic: lead = "You've got some charge."
        case .melancholic: lead = "That sounds heavy."
        case .stressed: lead = "That sounds tense."
        case .tired: lead = "That sounds worn out."
        }

        if let reason = assessment.reasons.first(where: { !$0.hasPrefix("You described") && !$0.hasPrefix("Health figures") }) {
            return "\(lead) \(reason) \(pitch)"
        }
        return "\(lead) \(pitch)"
    }

    private struct TextScore {
        var scores: [Mood: Double]
        var maxScore: Double { scores.values.max() ?? 0 }
    }

    private struct HealthScore {
        var scores: [Mood: Double]
        var reasons: [(mood: Mood, text: String)]
    }

    private static func scoreHealth(_ snapshot: HealthSnapshot?) -> HealthScore {
        var scores = Dictionary(uniqueKeysWithValues: Mood.allCases.map { ($0, 0.0) })
        var reasons: [(mood: Mood, text: String)] = []
        guard let snapshot, snapshot.hasAnySignal else {
            return HealthScore(scores: scores, reasons: reasons)
        }

        func note(_ mood: Mood, _ amount: Double, _ text: String) {
            scores[mood, default: 0] += amount
            let line = text
            if !reasons.contains(where: { $0.mood == mood && $0.text == line }) {
                reasons.append((mood, line))
            }
        }

        if let sleep = snapshot.sleepHours {
            switch sleep {
            case ..<5:
                note(.tired, 0.9, "Sleep was under 5 hours.")
                note(.stressed, 0.4, "Sleep was under 5 hours.")
                note(.melancholic, 0.3, "Sleep was under 5 hours.")
            case 5..<6.5:
                note(.tired, 0.45, "Sleep was short.")
                note(.stressed, 0.2, "Sleep was short.")
            case 7.5...9:
                note(.calm, 0.55, "Sleep landed in a solid range.")
                note(.joyful, 0.35, "Sleep landed in a solid range.")
            case 9...:
                note(.tired, 0.25, "Sleep ran long.")
                note(.calm, 0.2, "Sleep ran long.")
            default:
                note(.calm, 0.2, "Sleep was about average.")
            }
        }

        if let hrv = snapshot.heartRateVariabilityMS {
            switch hrv {
            case ..<20:
                note(.stressed, 0.8, "Heart-rate variability is low.")
                note(.melancholic, 0.2, "Heart-rate variability is low.")
            case 20..<35:
                note(.stressed, 0.4, "Heart-rate variability is on the low side.")
            case 60...:
                note(.calm, 0.6, "Heart-rate variability looks high.")
                note(.joyful, 0.3, "Heart-rate variability looks high.")
            default:
                note(.calm, 0.35, "Heart-rate variability looks steady.")
                note(.focused, 0.2, "Heart-rate variability looks steady.")
            }
        }

        let recentWorkout = (snapshot.hoursSinceWorkout ?? 99) < 3 && (snapshot.recentWorkoutMinutes ?? 0) > 10
        if let heart = snapshot.latestHeartRate {
            if recentWorkout && heart > 90 {
                note(.energetic, 0.7, "Heart rate is up after a workout.")
            } else if heart > 100 {
                note(.stressed, 0.6, "Heart rate is elevated.")
            } else if heart > 85 {
                note(.energetic, 0.25, "Heart rate is a little elevated.")
                note(.stressed, 0.15, "Heart rate is a little elevated.")
            } else if heart < 60 {
                note(.calm, 0.3, "Heart rate is low and steady.")
                note(.tired, 0.2, "Heart rate is low and steady.")
            } else {
                note(.calm, 0.15, "Heart rate is in a resting range.")
            }
        }

        if let steps = snapshot.stepsToday {
            switch steps {
            case 10_000...:
                note(.energetic, 0.55, "Step count is high today.")
                note(.joyful, 0.35, "Step count is high today.")
            case 6_000..<10_000:
                note(.joyful, 0.25, "You've been moving around.")
                note(.energetic, 0.2, "You've been moving around.")
            case ..<2_000:
                note(.tired, 0.35, "Activity is very low today.")
                note(.melancholic, 0.25, "Activity is very low today.")
            default:
                break
            }
        }

        if let calories = snapshot.activeCalories, calories > 400 {
            note(.energetic, 0.3, "Active energy is high.")
        }

        if let mindful = snapshot.mindfulMinutes, mindful >= 5 {
            note(.calm, 0.7, "There's a mindfulness session today.")
            scores[.stressed, default: 0] = max(0, (scores[.stressed] ?? 0) - 0.3)
        }

        if let minutes = snapshot.recentWorkoutMinutes,
           minutes > 20,
           (snapshot.hoursSinceWorkout ?? 99) < 8 {
            note(.energetic, 0.8, "You worked out recently.")
            note(.joyful, 0.2, "You worked out recently.")
        }

        return HealthScore(scores: scores, reasons: reasons)
    }

    private static func scoreChat(_ messages: [ChatMessage]) -> TextScore {
        var scores = Dictionary(uniqueKeysWithValues: Mood.allCases.map { ($0, 0.0) })
        let recent = Array(messages.suffix(6))
        for (index, message) in recent.enumerated() {
            let distanceFromEnd = recent.count - 1 - index
            let weight = distanceFromEnd == 0 ? 1.0 : (distanceFromEnd == 1 ? 0.45 : 0.2)
            let textScore = scoreText(message.text)
            for mood in Mood.allCases {
                scores[mood, default: 0] += (textScore.scores[mood] ?? 0) * weight
            }
        }
        return TextScore(scores: scores)
    }

    private static func scoreText(_ text: String) -> TextScore {
        var scores = Dictionary(uniqueKeysWithValues: Mood.allCases.map { ($0, 0.0) })
        var working = text.lowercased()
        let phrases = phraseLexicon.sorted { $0.phrase.count > $1.phrase.count }
        for entry in phrases where working.contains(entry.phrase) {
            for (mood, amount) in entry.scores {
                scores[mood, default: 0] += amount
            }
            working = working.replacingOccurrences(of: entry.phrase, with: " ")
        }

        working = working.replacingOccurrences(of: "n't", with: " not ")
        let tokens = working
            .components(separatedBy: CharacterSet.letters.inverted)
            .filter { !$0.isEmpty }

        var negationWindow = 0
        for token in tokens {
            if negationWords.contains(token) {
                negationWindow = 3
                continue
            }
            guard let weights = wordLexicon[token] else {
                if negationWindow > 0 { negationWindow -= 1 }
                continue
            }
            if negationWindow > 0 {
                for (mood, amount) in weights {
                    let flipped = negated(mood)
                    scores[flipped, default: 0] += amount
                }
                negationWindow = 0
            } else {
                for (mood, amount) in weights {
                    scores[mood, default: 0] += amount
                }
            }
        }

        return TextScore(scores: scores)
    }

    private static func negated(_ mood: Mood) -> Mood {
        switch mood {
        case .joyful: .melancholic
        case .calm: .stressed
        case .focused: .stressed
        case .energetic: .tired
        case .melancholic: .joyful
        case .stressed: .calm
        case .tired: .energetic
        }
    }

    private static let negationWords: Set<String> = ["not", "no", "never", "hardly", "without"]

    private static let phraseLexicon: [(phrase: String, scores: [Mood: Double])] = [
        ("burnt out", [.tired: 1.0, .stressed: 0.7]),
        ("burned out", [.tired: 1.0, .stressed: 0.7]),
        ("can't sleep", [.tired: 1.0, .stressed: 0.45]),
        ("cannot sleep", [.tired: 1.0, .stressed: 0.45]),
        ("need to focus", [.focused: 1.0]),
        ("deep work", [.focused: 0.9]),
        ("feeling great", [.joyful: 1.0]),
        ("feel great", [.joyful: 1.0]),
        ("so happy", [.joyful: 1.0]),
        ("feeling good", [.joyful: 0.8]),
        ("worked out", [.energetic: 1.0]),
        ("at the gym", [.energetic: 0.9]),
        ("heart broken", [.melancholic: 1.0]),
        ("feel down", [.melancholic: 0.9]),
    ]

    private static let wordLexicon: [String: [Mood: Double]] = [
        "happy": [.joyful: 1],
        "joy": [.joyful: 1],
        "joyful": [.joyful: 1],
        "excited": [.joyful: 1],
        "great": [.joyful: 0.8],
        "amazing": [.joyful: 1],
        "wonderful": [.joyful: 1],
        "awesome": [.joyful: 0.9],
        "good": [.joyful: 0.7],
        "glad": [.joyful: 0.8],
        "cheerful": [.joyful: 1],
        "calm": [.calm: 1],
        "relaxed": [.calm: 1],
        "peaceful": [.calm: 1],
        "chill": [.calm: 0.9],
        "serene": [.calm: 1],
        "quiet": [.calm: 0.5],
        "focus": [.focused: 1],
        "focused": [.focused: 1],
        "concentrate": [.focused: 1],
        "study": [.focused: 0.8],
        "studying": [.focused: 0.9],
        "productive": [.focused: 0.8],
        "coding": [.focused: 0.6],
        "energy": [.energetic: 0.9],
        "energetic": [.energetic: 1],
        "workout": [.energetic: 1],
        "gym": [.energetic: 0.9],
        "hype": [.energetic: 0.8],
        "pumped": [.energetic: 0.9, .joyful: 0.4],
        "wired": [.energetic: 0.7, .stressed: 0.3],
        "sad": [.melancholic: 1],
        "lonely": [.melancholic: 1],
        "blue": [.melancholic: 0.4],
        "cry": [.melancholic: 0.9],
        "crying": [.melancholic: 1],
        "heartbroken": [.melancholic: 1],
        "empty": [.melancholic: 0.7],
        "miserable": [.melancholic: 1],
        "depressed": [.melancholic: 1],
        "stressed": [.stressed: 1],
        "stress": [.stressed: 1],
        "anxious": [.stressed: 1],
        "anxiety": [.stressed: 1],
        "overwhelmed": [.stressed: 1],
        "angry": [.stressed: 0.8],
        "frustrated": [.stressed: 0.8],
        "tense": [.stressed: 0.9],
        "worried": [.stressed: 0.8],
        "tired": [.tired: 1],
        "sleepy": [.tired: 1],
        "exhausted": [.tired: 1],
        "drained": [.tired: 1],
        "fatigue": [.tired: 1],
        "fatigued": [.tired: 1],
    ]
}
