import SwiftUI

enum MoodPalette {
    static func ink(_ mood: Mood) -> Color {
        switch mood {
        case .joyful: Color(red: 0.11, green: 0.08, blue: 0.05)
        case .calm: Color(red: 0.05, green: 0.09, blue: 0.08)
        case .focused: Color(red: 0.05, green: 0.07, blue: 0.11)
        case .energetic: Color(red: 0.12, green: 0.06, blue: 0.04)
        case .melancholic: Color(red: 0.07, green: 0.08, blue: 0.12)
        case .stressed: Color(red: 0.12, green: 0.07, blue: 0.06)
        case .tired: Color(red: 0.07, green: 0.07, blue: 0.11)
        }
    }

    static func accent(_ mood: Mood) -> Color {
        switch mood {
        case .joyful: Color(red: 0.96, green: 0.73, blue: 0.32)
        case .calm: Color(red: 0.56, green: 0.78, blue: 0.68)
        case .focused: Color(red: 0.56, green: 0.73, blue: 0.88)
        case .energetic: Color(red: 1.0, green: 0.45, blue: 0.31)
        case .melancholic: Color(red: 0.64, green: 0.72, blue: 0.84)
        case .stressed: Color(red: 0.89, green: 0.49, blue: 0.36)
        case .tired: Color(red: 0.70, green: 0.72, blue: 0.88)
        }
    }
}

struct MoodBackground: View {
    var mood: Mood

    var body: some View {
        ZStack {
            MoodPalette.ink(mood)
            RadialGradient(
                colors: [MoodPalette.accent(mood).opacity(0.38), .clear],
                center: .init(x: 0.5, y: 0.18),
                startRadius: 20,
                endRadius: 420
            )
        }
        .ignoresSafeArea()
    }
}

struct MetricTile: View {
    var title: String
    var value: String
    var accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title.uppercased())
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .tracking(1.1)
                .foregroundStyle(.white.opacity(0.55))
            Text(value)
                .font(.system(size: 26, weight: .medium, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(.white.opacity(0.08), lineWidth: 1)
        )
    }
}

enum MetricText {
    static func steps(_ value: Double?) -> String {
        guard let value else { return "—" }
        return value.formatted(.number.precision(.fractionLength(0)))
    }

    static func hours(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(value.formatted(.number.precision(.fractionLength(1)))) h"
    }

    static func bpm(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(Int(value.rounded())) bpm"
    }

    static func milliseconds(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(Int(value.rounded())) ms"
    }

    static func energy(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(Int(value.rounded())) kcal"
    }

    static func minutes(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(Int(value.rounded())) min"
    }
}
