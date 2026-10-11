import SwiftUI

struct MoodScreen: View {
    @Environment(AppModel.self) private var model
    @Binding var showSettings: Bool

    private var accent: Color { MoodPalette.accent(model.assessment.mood) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack {
                    Text("MOODTUNE")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .tracking(2.2)
                        .foregroundStyle(accent)
                    Spacer()
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(.white.opacity(0.85))
                            .frame(width: 40, height: 40)
                            .background(.white.opacity(0.08), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Settings")
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Right now")
                        .font(.system(size: 17, weight: .medium, design: .rounded))
                        .foregroundStyle(.white.opacity(0.6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Image(systemName: model.assessment.mood.symbol)
                            .font(.system(size: 34, weight: .regular))
                            .foregroundStyle(accent)
                        Text(model.assessment.mood.title)
                            .font(.system(size: 68, weight: .light, design: .serif))
                            .foregroundStyle(.white)
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                    }
                    Text(model.assessment.summary)
                        .font(.system(size: 18, weight: .regular, design: .rounded))
                        .foregroundStyle(.white.opacity(0.78))
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(Int((model.assessment.confidence * 100).rounded()))% match")
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(accent)
                }

                Button {
                    Task {
                        if let playlist = model.playlists.first {
                            await model.play(playlist)
                        }
                    }
                } label: {
                    HStack {
                        Image(systemName: "play.fill")
                        Text("Play \(model.assessment.mood.title.lowercased()) music")
                    }
                    .font(.system(size: 19, weight: .semibold, design: .rounded))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18)
                    .background(accent, in: Capsule())
                    .foregroundStyle(MoodPalette.ink(model.assessment.mood))
                }
                .buttonStyle(.plain)
                .disabled(model.playlists.isEmpty || model.isPlaying)
                .opacity(model.playlists.isEmpty ? 0.5 : 1)

                if model.snapshot?.isSampleData == true {
                    Text("These health figures are sample data. On iPhone, allow Health access to use your own sleep, heart, and activity.")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundStyle(.white.opacity(0.8))
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }

                if !model.assessment.reasons.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(model.assessment.reasons, id: \.self) { reason in
                            Text(reason)
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundStyle(.white.opacity(0.88))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(.white.opacity(0.06), in: Capsule())
                        }
                    }
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    MetricTile(title: "Sleep", value: MetricText.hours(model.snapshot?.sleepHours), accent: accent)
                    MetricTile(title: "Heart", value: MetricText.bpm(model.snapshot?.latestHeartRate), accent: accent)
                    MetricTile(title: "HRV", value: MetricText.milliseconds(model.snapshot?.heartRateVariabilityMS), accent: accent)
                    MetricTile(title: "Steps", value: MetricText.steps(model.snapshot?.stepsToday), accent: accent)
                    MetricTile(title: "Move", value: MetricText.energy(model.snapshot?.activeCalories), accent: accent)
                    MetricTile(title: "Mindful", value: MetricText.minutes(model.snapshot?.mindfulMinutes), accent: accent)
                }

                if let statusMessage = model.statusMessage {
                    Text(statusMessage)
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundStyle(.white.opacity(0.7))
                }
            }
            .padding(.horizontal, 22)
            .padding(.top, 18)
            .padding(.bottom, 120)
        }
    }
}

struct ChatScreen: View {
    @Environment(AppModel.self) private var model
    @State private var draft = ""
    @FocusState private var isFocused: Bool

    private let suggestions = ["I'm anxious", "Feeling great", "Need to focus", "Can't sleep"]
    private var accent: Color { MoodPalette.accent(model.assessment.mood) }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(model.messages) { message in
                            bubble(message)
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 16)
                    .padding(.bottom, 12)
                }
                .onChange(of: model.messages.count) { _, _ in
                    if let last = model.messages.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(suggestions, id: \.self) { suggestion in
                        Button(suggestion) {
                            Task { await model.send(suggestion) }
                        }
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.white.opacity(0.08), in: Capsule())
                        .foregroundStyle(.white)
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 18)
            }
            .padding(.bottom, 8)

            HStack(spacing: 10) {
                TextField("How do you feel?", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($isFocused)
                    .font(.system(size: 18, weight: .regular, design: .rounded))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .foregroundStyle(.white)

                Button {
                    let text = draft
                    draft = ""
                    Task { await model.send(text) }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(MoodPalette.ink(model.assessment.mood))
                        .frame(width: 42, height: 42)
                        .background(accent, in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("Send")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
    }

    private func bubble(_ message: ChatMessage) -> some View {
        let isUser = message.role == .user
        return HStack {
            if isUser { Spacer(minLength: 48) }
            Text(message.text)
                .font(.system(size: 18, weight: .regular, design: .rounded))
                .foregroundStyle(isUser ? MoodPalette.ink(model.assessment.mood) : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    isUser ? accent : Color.white.opacity(0.08),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                )
            if !isUser { Spacer(minLength: 48) }
        }
    }
}

struct ListenScreen: View {
    @Environment(AppModel.self) private var model

    private var accent: Color { MoodPalette.accent(model.assessment.mood) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(model.assessment.mood.title)
                    .font(.system(size: 40, weight: .light, design: .serif))
                    .foregroundStyle(.white)
                Text(model.isSpotifyConnected
                     ? "Playlists for this mood\(model.spotifyName.map { ", \($0)" } ?? "")."
                     : "Connect Spotify in settings for search and remote play. These still open in Spotify.")
                    .font(.system(size: 17, weight: .regular, design: .rounded))
                    .foregroundStyle(.white.opacity(0.7))
                    .fixedSize(horizontal: false, vertical: true)

                if model.isLoadingPlaylists {
                    ProgressView()
                        .tint(accent)
                }

                ForEach(model.playlists) { playlist in
                    Button {
                        Task { await model.play(playlist) }
                    } label: {
                        HStack(spacing: 14) {
                            artwork(playlist)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(playlist.name)
                                    .font(.system(size: 19, weight: .semibold, design: .rounded))
                                    .foregroundStyle(.white)
                                    .multilineTextAlignment(.leading)
                                Text(playlistSubtitle(playlist))
                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                    .foregroundStyle(.white.opacity(0.62))
                            }
                            Spacer()
                            Image(systemName: "play.fill")
                                .foregroundStyle(accent)
                        }
                        .padding(12)
                        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }

                if let statusMessage = model.statusMessage {
                    Text(statusMessage)
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundStyle(.white.opacity(0.7))
                }
            }
            .padding(.horizontal, 22)
            .padding(.top, 22)
            .padding(.bottom, 120)
        }
    }

    private func artwork(_ playlist: PlaylistSuggestion) -> some View {
        AsyncImage(url: playlist.imageURL) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                ZStack {
                    accent.opacity(0.25)
                    Image(systemName: playlist.isSearchLink ? "magnifyingglass" : "music.note")
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: 64, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func playlistSubtitle(_ playlist: PlaylistSuggestion) -> String {
        if let count = playlist.trackCount {
            return "\(playlist.detail) · \(count) tracks"
        }
        return playlist.detail
    }
}

struct SettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                Section("Spotify") {
                    TextField("Client ID", text: $model.clientID)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Text("In the Spotify dashboard, add redirect URI \(SpotifyClient.redirectURI).")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if model.isSpotifyConnected {
                        LabeledContent("Account", value: model.spotifyName ?? "Connected")
                        Button("Disconnect", role: .destructive) {
                            model.disconnectSpotify()
                        }
                    } else {
                        Button("Connect Spotify") {
                            Task { await model.connectSpotify() }
                        }
                        .disabled(model.clientID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }

                Section("Health") {
                    Toggle("Use sample data when Health is empty", isOn: Binding(
                        get: { model.useSampleHealthData },
                        set: { enabled in
                            Task { await model.setUseSampleHealthData(enabled) }
                        }
                    ))
                    Button("Refresh Health access") {
                        Task { await model.refreshHealth(requestAuthorization: true) }
                    }
                    Text(healthDetail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let statusMessage = model.statusMessage {
                    Section {
                        Text(statusMessage)
                            .font(.footnote)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var healthDetail: String {
        switch model.healthStatus {
        case .unknown:
            "Health has not been read yet."
        case .unavailable:
            "Apple Health is not available on this device."
        case .ready:
            "Reading your Health data."
        case .empty:
            "Health allowed a read, but there were no recent samples."
        case .failed(let message):
            message
        }
    }
}
