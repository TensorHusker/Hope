// PerformanceProfiler.swift
// Advanced performance profiling and benchmarking system
// Provides detailed metrics and optimization recommendations

import Foundation
import MetalPerformanceShaders
import os.signpost
import QuartzCore

/// Performance profiler for comprehensive metrics collection
final class PerformanceProfiler {
    
    // MARK: - Singleton
    
    static let shared = PerformanceProfiler()
    
    // MARK: - Properties
    
    private let signpostLog = OSLog(subsystem: "com.hope.profiler", category: .pointsOfInterest)
    private var signpostID = OSSignpostID(log: .default)
    
    // MARK: - Metrics Storage
    
    private var frameMetrics: RingBuffer<FrameMetrics> = RingBuffer(capacity: 300) // 5 seconds at 60fps
    private var touchMetrics: RingBuffer<TouchMetrics> = RingBuffer(capacity: 100)
    private var memoryMetrics: RingBuffer<MemoryMetrics> = RingBuffer(capacity: 60)
    
    // MARK: - Benchmarks
    
    private var benchmarkResults: [String: BenchmarkResult] = [:]
    
    // MARK: - Types
    
    struct FrameMetrics {
        let timestamp: TimeInterval
        let cpuTime: TimeInterval
        let gpuTime: TimeInterval
        let presentTime: TimeInterval
        let drawCalls: Int
        let triangles: Int
        let textureMemory: Int
        let bufferMemory: Int
    }
    
    struct TouchMetrics {
        let timestamp: TimeInterval
        let inputLatency: TimeInterval
        let processingTime: TimeInterval
        let eventType: String
        let coalescedCount: Int
    }
    
    struct MemoryMetrics {
        let timestamp: TimeInterval
        let resident: Int
        let virtual: Int
        let compressed: Int
        let purgeable: Int
        let wired: Int
    }
    
    struct BenchmarkResult {
        let name: String
        let averageTime: TimeInterval
        let minTime: TimeInterval
        let maxTime: TimeInterval
        let standardDeviation: TimeInterval
        let samples: Int
    }
    
    // MARK: - Frame Profiling
    
    func beginFrame() {
        os_signpost(.begin, log: signpostLog, name: "Frame", signpostID: signpostID)
    }
    
    func endFrame(cpuTime: TimeInterval, gpuTime: TimeInterval) {
        os_signpost(.end, log: signpostLog, name: "Frame", signpostID: signpostID)
        
        let metrics = FrameMetrics(
            timestamp: CACurrentMediaTime(),
            cpuTime: cpuTime,
            gpuTime: gpuTime,
            presentTime: 0,
            drawCalls: 0,
            triangles: 0,
            textureMemory: 0,
            bufferMemory: 0
        )
        
        frameMetrics.append(metrics)
        
        // Check for performance issues
        if cpuTime > 0.016 {
            logPerformanceIssue("CPU time exceeded 16ms: \(cpuTime * 1000)ms")
        }
        
        if gpuTime > 0.016 {
            logPerformanceIssue("GPU time exceeded 16ms: \(gpuTime * 1000)ms")
        }
    }
    
    // MARK: - Touch Profiling
    
    func profileTouch(inputLatency: TimeInterval, processingTime: TimeInterval, eventType: String, coalescedCount: Int) {
        let metrics = TouchMetrics(
            timestamp: CACurrentMediaTime(),
            inputLatency: inputLatency,
            processingTime: processingTime,
            eventType: eventType,
            coalescedCount: coalescedCount
        )
        
        touchMetrics.append(metrics)
        
        if inputLatency > 0.016 {
            logPerformanceIssue("Touch latency exceeded 16ms: \(inputLatency * 1000)ms")
        }
    }
    
    // MARK: - Memory Profiling
    
    func profileMemory() {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4
        
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_,
                         task_flavor_t(MACH_TASK_BASIC_INFO),
                         $0,
                         &count)
            }
        }
        
        if result == KERN_SUCCESS {
            let metrics = MemoryMetrics(
                timestamp: CACurrentMediaTime(),
                resident: Int(info.resident_size),
                virtual: Int(info.virtual_size),
                compressed: 0,
                purgeable: 0,
                wired: 0
            )
            
            memoryMetrics.append(metrics)
            
            // Check for memory issues
            let residentMB = Int(info.resident_size) / 1024 / 1024
            if residentMB > 200 {
                logPerformanceIssue("Memory usage exceeded 200MB: \(residentMB)MB")
            }
        }
    }
    
    // MARK: - Benchmarking
    
    func benchmark(_ name: String, iterations: Int = 100, block: () throws -> Void) rethrows {
        var times: [TimeInterval] = []
        times.reserveCapacity(iterations)
        
        // Warm up
        for _ in 0..<10 {
            try block()
        }
        
        // Actual benchmark
        for _ in 0..<iterations {
            let start = CACurrentMediaTime()
            try block()
            let end = CACurrentMediaTime()
            times.append(end - start)
        }
        
        // Calculate statistics
        let average = times.reduce(0, +) / Double(times.count)
        let min = times.min() ?? 0
        let max = times.max() ?? 0
        
        let variance = times.reduce(0) { $0 + pow($1 - average, 2) } / Double(times.count)
        let stdDev = sqrt(variance)
        
        let result = BenchmarkResult(
            name: name,
            averageTime: average,
            minTime: min,
            maxTime: max,
            standardDeviation: stdDev,
            samples: iterations
        )
        
        benchmarkResults[name] = result
        
        print("""
        Benchmark: \(name)
        Average: \(average * 1000)ms
        Min: \(min * 1000)ms
        Max: \(max * 1000)ms
        StdDev: \(stdDev * 1000)ms
        Samples: \(iterations)
        """)
    }
    
    // MARK: - Analysis
    
    func analyzeFramePerformance() -> PerformanceAnalysis {
        let frames = frameMetrics.allElements()
        guard !frames.isEmpty else {
            return PerformanceAnalysis(issues: [], recommendations: [])
        }
        
        var issues: [String] = []
        var recommendations: [String] = []
        
        // Calculate averages
        let avgCPU = frames.map(\.cpuTime).reduce(0, +) / Double(frames.count)
        let avgGPU = frames.map(\.gpuTime).reduce(0, +) / Double(frames.count)
        
        // Analyze CPU performance
        if avgCPU > 0.014 {
            issues.append("CPU time average (\(avgCPU * 1000)ms) risks missing 60fps target")
            recommendations.append("Consider reducing game logic complexity or offloading to background threads")
        }
        
        // Analyze GPU performance
        if avgGPU > 0.014 {
            issues.append("GPU time average (\(avgGPU * 1000)ms) risks missing 60fps target")
            recommendations.append("Reduce shader complexity, lower resolution, or decrease draw calls")
        }
        
        // Check for frame spikes
        let spikes = frames.filter { $0.cpuTime > 0.020 || $0.gpuTime > 0.020 }
        if spikes.count > frames.count / 10 {
            issues.append("Frequent frame spikes detected (\(spikes.count) frames)")
            recommendations.append("Profile spike frames to identify causes")
        }
        
        return PerformanceAnalysis(issues: issues, recommendations: recommendations)
    }
    
    func analyzeTouchPerformance() -> PerformanceAnalysis {
        let touches = touchMetrics.allElements()
        guard !touches.isEmpty else {
            return PerformanceAnalysis(issues: [], recommendations: [])
        }
        
        var issues: [String] = []
        var recommendations: [String] = []
        
        // Calculate average latency
        let avgLatency = touches.map(\.inputLatency).reduce(0, +) / Double(touches.count)
        
        if avgLatency > 0.016 {
            issues.append("Touch latency average (\(avgLatency * 1000)ms) exceeds target")
            recommendations.append("Use touch prediction and coalescing to reduce perceived latency")
        }
        
        // Check processing time
        let avgProcessing = touches.map(\.processingTime).reduce(0, +) / Double(touches.count)
        
        if avgProcessing > 0.002 {
            issues.append("Touch processing time high (\(avgProcessing * 1000)ms)")
            recommendations.append("Optimize touch event handling or batch process touches")
        }
        
        return PerformanceAnalysis(issues: issues, recommendations: recommendations)
    }
    
    func analyzeMemoryPerformance() -> PerformanceAnalysis {
        let memories = memoryMetrics.allElements()
        guard !memories.isEmpty else {
            return PerformanceAnalysis(issues: [], recommendations: [])
        }
        
        var issues: [String] = []
        var recommendations: [String] = []
        
        // Check memory usage
        let lastMemory = memories.last!
        let residentMB = lastMemory.resident / 1024 / 1024
        
        if residentMB > 200 {
            issues.append("Memory usage high: \(residentMB)MB")
            recommendations.append("Implement texture atlasing and resource pooling")
        }
        
        // Check for memory growth
        if memories.count > 10 {
            let firstMemory = memories.first!
            let growth = lastMemory.resident - firstMemory.resident
            let growthMB = growth / 1024 / 1024
            
            if growthMB > 50 {
                issues.append("Memory growth detected: \(growthMB)MB")
                recommendations.append("Check for memory leaks and implement resource cleanup")
            }
        }
        
        return PerformanceAnalysis(issues: issues, recommendations: recommendations)
    }
    
    // MARK: - Reporting
    
    func generateReport() -> PerformanceReport {
        let frameAnalysis = analyzeFramePerformance()
        let touchAnalysis = analyzeTouchPerformance()
        let memoryAnalysis = analyzeMemoryPerformance()
        
        return PerformanceReport(
            timestamp: Date(),
            frameAnalysis: frameAnalysis,
            touchAnalysis: touchAnalysis,
            memoryAnalysis: memoryAnalysis,
            benchmarks: benchmarkResults
        )
    }
    
    func exportReport(_ report: PerformanceReport) -> Data? {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        encoder.dateEncodingStrategy = .iso8601
        
        return try? encoder.encode(report)
    }
    
    // MARK: - Helpers
    
    private func logPerformanceIssue(_ message: String) {
        os_log(.fault, log: signpostLog, "%{public}s", message)
    }
}

// MARK: - Support Types

struct PerformanceAnalysis {
    let issues: [String]
    let recommendations: [String]
}

struct PerformanceReport: Codable {
    let timestamp: Date
    let frameAnalysis: PerformanceAnalysis
    let touchAnalysis: PerformanceAnalysis
    let memoryAnalysis: PerformanceAnalysis
    let benchmarks: [String: PerformanceProfiler.BenchmarkResult]
}

// Make analysis types codable for export
extension PerformanceAnalysis: Codable {}
extension PerformanceProfiler.BenchmarkResult: Codable {}

// MARK: - Ring Buffer

struct RingBuffer<T> {
    private var buffer: [T?]
    private var writeIndex = 0
    private let capacity: Int
    
    init(capacity: Int) {
        self.capacity = capacity
        self.buffer = Array(repeating: nil, count: capacity)
    }
    
    mutating func append(_ element: T) {
        buffer[writeIndex] = element
        writeIndex = (writeIndex + 1) % capacity
    }
    
    func allElements() -> [T] {
        return buffer.compactMap { $0 }
    }
}

// MARK: - Benchmark Suite

extension PerformanceProfiler {
    
    func runCompleteBenchmarkSuite() {
        print("Running performance benchmark suite...")
        
        // FFI benchmarks
        benchmark("FFI Touch Event") {
            OptimizedHopeFFI.shared.sendTouchOptimized(at: CGPoint(x: 0.5, y: 0.5), eventType: .began)
        }
        
        benchmark("FFI Score Query") {
            _ = OptimizedHopeFFI.shared.getScoreOptimized()
        }
        
        benchmark("FFI Update Call") {
            OptimizedHopeFFI.shared.updateAdaptive(deltaTime: 0.016)
        }
        
        // Memory benchmarks
        benchmark("Memory Allocation (1MB)") {
            let _ = Data(count: 1024 * 1024)
        }
        
        benchmark("Memory Pool Acquire/Release") {
            let pool = MemoryPool<NSObject>(capacity: 10)
            let obj = NSObject()
            pool.release(obj)
            _ = pool.acquire()
        }
        
        // Touch processing benchmarks
        benchmark("Touch Normalization") {
            let point = CGPoint(x: 100, y: 200)
            let _ = normalizePoint(point, viewSize: CGSize(width: 375, height: 667))
        }
        
        print("Benchmark suite complete!")
        
        // Generate and print report
        let report = generateReport()
        if let data = exportReport(report),
           let json = String(data: data, encoding: .utf8) {
            print("Performance Report:")
            print(json)
        }
    }
    
    private func normalizePoint(_ point: CGPoint, viewSize: CGSize) -> CGPoint {
        return CGPoint(
            x: point.x / viewSize.width,
            y: 1.0 - (point.y / viewSize.height)
        )
    }
}

// Memory pool helper for benchmarking
private class MemoryPool<T: AnyObject> {
    private var pool: [T] = []
    private let capacity: Int
    
    init(capacity: Int) {
        self.capacity = capacity
    }
    
    func acquire() -> T? {
        return pool.popLast()
    }
    
    func release(_ object: T) {
        if pool.count < capacity {
            pool.append(object)
        }
    }
}