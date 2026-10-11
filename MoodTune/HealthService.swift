import Foundation
import HealthKit

enum HealthAccessStatus: Equatable {
    case unknown
    case unavailable
    case ready
    case empty
    case failed(String)
}

struct HealthLoadResult {
    var status: HealthAccessStatus
    var snapshot: HealthSnapshot?
}

final class HealthService {
    private let store = HKHealthStore()

    func loadSnapshot(requestAuthorization: Bool) async -> HealthLoadResult {
        guard HKHealthStore.isHealthDataAvailable() else {
            return HealthLoadResult(status: .unavailable, snapshot: nil)
        }

        if requestAuthorization {
            do {
                try await store.requestAuthorization(toShare: [], read: Self.readTypes)
            } catch {
                return HealthLoadResult(status: .failed(error.localizedDescription), snapshot: nil)
            }
        }

        let snapshot = await fetchSnapshot()
        if snapshot.hasAnySignal {
            return HealthLoadResult(status: .ready, snapshot: snapshot)
        }
        return HealthLoadResult(status: .empty, snapshot: snapshot)
    }

    private func fetchSnapshot() async -> HealthSnapshot {
        async let steps = sumToday(identifier: .stepCount, unit: .count())
        async let calories = sumToday(identifier: .activeEnergyBurned, unit: .kilocalorie())
        async let heart = latestQuantity(identifier: .heartRate, unit: HKUnit.count().unitDivided(by: .minute()), since: startOfToday())
        async let hrv = latestQuantity(identifier: .heartRateVariabilitySDNN, unit: .secondUnit(with: .milli), since: Date().addingTimeInterval(-7 * 24 * 3600))
        async let sleep = sleepHours()
        async let mindful = mindfulMinutes()
        async let workout = latestWorkout()

        let workoutResult = await workout
        return HealthSnapshot(
            stepsToday: await steps,
            activeCalories: await calories,
            latestHeartRate: await heart,
            heartRateVariabilityMS: await hrv,
            sleepHours: await sleep,
            mindfulMinutes: await mindful,
            recentWorkoutMinutes: workoutResult?.minutes,
            hoursSinceWorkout: workoutResult?.hoursAgo,
            isSampleData: false,
            fetchedAt: .now
        )
    }

    private func sumToday(identifier: HKQuantityTypeIdentifier, unit: HKUnit) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        let predicate = HKQuery.predicateForSamples(withStart: startOfToday(), end: Date())
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, stats, _ in
                continuation.resume(returning: stats?.sumQuantity()?.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    private func latestQuantity(identifier: HKQuantityTypeIdentifier, unit: HKUnit, since: Date) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        let predicate = HKQuery.predicateForSamples(withStart: since, end: Date())
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: 1, sortDescriptors: [sort]) { _, samples, _ in
                let sample = samples?.first as? HKQuantitySample
                continuation.resume(returning: sample?.quantity.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    private func sleepHours() async -> Double? {
        guard let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
        let calendar = Calendar.current
        let start = calendar.date(byAdding: .hour, value: -18, to: startOfToday()) ?? startOfToday()
        let end = calendar.date(byAdding: .hour, value: 12, to: startOfToday()) ?? Date()
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
        let asleep: Set<Int> = [
            HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue,
            HKCategoryValueSleepAnalysis.asleepCore.rawValue,
            HKCategoryValueSleepAnalysis.asleepDeep.rawValue,
            HKCategoryValueSleepAnalysis.asleepREM.rawValue,
            HKCategoryValueSleepAnalysis.asleep.rawValue,
        ]

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: [sort]) { _, samples, _ in
                let minutes = (samples ?? []).reduce(0.0) { total, sample in
                    guard let category = sample as? HKCategorySample, asleep.contains(category.value) else {
                        return total
                    }
                    return total + category.endDate.timeIntervalSince(category.startDate) / 60
                }
                continuation.resume(returning: minutes > 0 ? minutes / 60 : nil)
            }
            store.execute(query)
        }
    }

    private func mindfulMinutes() async -> Double? {
        guard let type = HKObjectType.categoryType(forIdentifier: .mindfulSession) else { return nil }
        let predicate = HKQuery.predicateForSamples(withStart: startOfToday(), end: Date())
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                let minutes = (samples ?? []).reduce(0.0) { total, sample in
                    total + sample.endDate.timeIntervalSince(sample.startDate) / 60
                }
                continuation.resume(returning: minutes > 0 ? minutes : nil)
            }
            store.execute(query)
        }
    }

    private func latestWorkout() async -> (minutes: Double, hoursAgo: Double)? {
        let type = HKObjectType.workoutType()
        let start = Date().addingTimeInterval(-36 * 3600)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date())
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: 1, sortDescriptors: [sort]) { _, samples, _ in
                guard let workout = samples?.first as? HKWorkout else {
                    continuation.resume(returning: nil)
                    return
                }
                let hours = Date().timeIntervalSince(workout.endDate) / 3600
                continuation.resume(returning: (workout.duration / 60, hours))
            }
            store.execute(query)
        }
    }

    private func startOfToday() -> Date {
        Calendar.current.startOfDay(for: Date())
    }

    private static var readTypes: Set<HKObjectType> {
        var types = Set<HKObjectType>()
        let quantities: [HKQuantityTypeIdentifier] = [
            .stepCount,
            .activeEnergyBurned,
            .heartRate,
            .heartRateVariabilitySDNN,
        ]
        for identifier in quantities {
            if let type = HKObjectType.quantityType(forIdentifier: identifier) {
                types.insert(type)
            }
        }
        if let sleep = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) {
            types.insert(sleep)
        }
        if let mindful = HKObjectType.categoryType(forIdentifier: .mindfulSession) {
            types.insert(mindful)
        }
        types.insert(HKObjectType.workoutType())
        return types
    }
}
