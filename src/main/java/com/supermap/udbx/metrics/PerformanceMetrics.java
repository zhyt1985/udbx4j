package com.supermap.udbx.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 性能指标收集器（可选功能）。
 *
 * 使用 Micrometer 库收集 udbx4j 的运行时性能指标，包括：
 * - 读取操作计数和时间
 * - 写入操作计数和时间
 *
 * 依赖关系：
 * - io.micrometer:micrometer-core:1.12.0（optional 依赖）
 *
 * 使用示例：
 * <pre>
 * // 创建 MeterRegistry（例如 Prometheus、StatsD、Logback 等）
 * MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 *
 * // 创建性能指标收集器
 * PerformanceMetrics metrics = new PerformanceMetrics(registry);
 *
 * // 记录读取操作
 * metrics.recordRead();
 * Timer.Sample sample = metrics.startReadTimer();
 * // ... 执行读取操作 ...
 * metrics.stopReadTimer(sample);
 * </pre>
 */
public class PerformanceMetrics {

    private final MeterRegistry registry;
    private final Counter readCounter;
    private final Counter writeCounter;
    private final Timer readTimer;
    private final Timer writeTimer;

    /**
     * 创建性能指标收集器。
     *
     * @param registry Micrometer MeterRegistry 实例
     */
    public PerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 初始化读取计数器
        this.readCounter = Counter.builder("udbx.read.count")
            .description("Total number of read operations")
            .register(registry);

        // 初始化写入计数器
        this.writeCounter = Counter.builder("udbx.write.count")
            .description("Total number of write operations")
            .register(registry);

        // 初始化读取计时器
        this.readTimer = Timer.builder("udbx.read.time")
            .description("Time taken for read operations")
            .register(registry);

        // 初始化写入计时器
        this.writeTimer = Timer.builder("udbx.write.time")
            .description("Time taken for write operations")
            .register(registry);
    }

    /**
     * 记录一次读取操作。
     */
    public void recordRead() {
        readCounter.increment();
    }

    /**
     * 记录一次写入操作。
     */
    public void recordWrite() {
        writeCounter.increment();
    }

    /**
     * 启动读取计时器。
     *
     * @return Timer.Sample 实例，用于后续停止计时
     */
    public Timer.Sample startReadTimer() {
        return Timer.start(registry);
    }

    /**
     * 启动写入计时器。
     *
     * @return Timer.Sample 实例，用于后续停止计时
     */
    public Timer.Sample startWriteTimer() {
        return Timer.start(registry);
    }

    /**
     * 停止读取计时器并记录耗时。
     *
     * @param sample 由 startReadTimer() 返回的样本
     */
    public void stopReadTimer(Timer.Sample sample) {
        sample.stop(readTimer);
    }

    /**
     * 停止写入计时器并记录耗时。
     *
     * @param sample 由 startWriteTimer() 返回的样本
     */
    public void stopWriteTimer(Timer.Sample sample) {
        sample.stop(writeTimer);
    }

    /**
     * 获取 MeterRegistry 实例。
     *
     * @return MeterRegistry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * 获取读取计数器。
     *
     * @return 读取计数器
     */
    public Counter getReadCounter() {
        return readCounter;
    }

    /**
     * 获取写入计数器。
     *
     * @return 写入计数器
     */
    public Counter getWriteCounter() {
        return writeCounter;
    }

    /**
     * 获取读取计时器。
     *
     * @return 读取计时器
     */
    public Timer getReadTimer() {
        return readTimer;
    }

    /**
     * 获取写入计时器。
     *
     * @return 写入计时器
     */
    public Timer getWriteTimer() {
        return writeTimer;
    }
}
