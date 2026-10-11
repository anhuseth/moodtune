import Foundation

var failures = 0

@main
enum MoodEngineTestRunner {
    static func main() {
        run()
        if failures > 0 {
            print("\(failures) failed")
            exit(1)
        }
        print("all mood checks passed")
    }
}

func check(_ condition: Bool, _ message: String) {
    if condition {
        print("PASS \(message)")
    } else {
        failures += 1
        print("FAIL \(message)")
    }
}

func user(_ text: String) -> ChatMessage {
    ChatMessage(role: .user, text: text)
}

func run() {
let sample = MoodEngine.assess(health: .sample, messages: [])
check(sample.mood == .stressed, "sample health reads as stressed, got \(sample.mood)")
check(sample.usedHealth, "sample health is used")
check(sample.summary.lowercased().contains("sample"), "sample data is labeled")

let happy = MoodEngine.assess(health: nil, messages: [user("I am so happy")])
check(happy.mood == .joyful, "so happy reads as joyful, got \(happy.mood)")

let negated = MoodEngine.assess(health: nil, messages: [user("I am not happy")])
check(negated.mood == .melancholic, "not happy reads as melancholic, got \(negated.mood)")

let anxious = MoodEngine.assess(health: nil, messages: [user("can't sleep and anxious")])
check(anxious.mood == .stressed, "can't sleep and anxious reads as stressed, got \(anxious.mood)")

let override = MoodEngine.assess(health: .sample, messages: [user("I feel peaceful and calm")])
check(override.mood == .calm, "chat overrides sample stress, got \(override.mood)")

let focus = MoodEngine.assess(health: nil, messages: [user("I need to focus")])
check(focus.mood == .focused, "need to focus reads as focused, got \(focus.mood)")

let empty = MoodEngine.assess(health: nil, messages: [])
check(empty.mood == .calm && empty.confidence < 0.3, "no signal stays undecided")

let vague = MoodEngine.assess(health: .sample, messages: [user("hello there")])
check(vague.latestMessageHadSignal == false, "hello is not a mood signal")
check(vague.mood == .stressed, "vague chat keeps the health mood, got \(vague.mood)")
}
