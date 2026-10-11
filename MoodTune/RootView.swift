import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @State private var showSettings = false

    var body: some View {
        Group {
            if model.didFinishOnboarding {
                TabView {
                    MoodScreen(showSettings: $showSettings)
                        .tabItem { Label("Mood", systemImage: "waveform.path.ecg") }
                    ChatScreen()
                        .tabItem { Label("Chat", systemImage: "text.bubble") }
                    ListenScreen()
                        .tabItem { Label("Listen", systemImage: "play.circle") }
                }
                .tint(MoodPalette.accent(model.assessment.mood))
            } else {
                OnboardingScreen()
            }
        }
        .background {
            MoodBackground(mood: model.assessment.mood)
        }
        .sheet(isPresented: $showSettings) {
            SettingsScreen()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .task {
            await model.bootstrap()
        }
    }
}

struct OnboardingScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            Spacer()
            Text("MOODTUNE")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .tracking(2.4)
                .foregroundStyle(MoodPalette.accent(.calm))
            Text("Music that follows the day you’re actually having.")
                .font(.system(size: 36, weight: .light, design: .serif))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 16) {
                feature("Heart and sleep", "Apple Health supplies heart rate, HRV, sleep, steps, workouts, and mindfulness.")
                feature("A sentence from you", "Chat can confirm the reading or override it when the sensors are wrong.")
                feature("A playlist", "MoodTune opens a Spotify mix that fits the mood it landed on.")
            }

            Spacer()

            Button {
                Task { await model.finishOnboarding() }
            } label: {
                Text("Allow Health access")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(MoodPalette.accent(.calm), in: Capsule())
                    .foregroundStyle(Color(red: 0.08, green: 0.1, blue: 0.09))
            }
            .buttonStyle(.plain)

            Button("Not now") {
                Task { await model.skipOnboarding() }
            }
            .font(.system(size: 15, weight: .medium, design: .rounded))
            .foregroundStyle(.white.opacity(0.7))
            .frame(maxWidth: .infinity)
        }
        .padding(28)
    }

    private func feature(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            Text(detail)
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundStyle(.white.opacity(0.68))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
