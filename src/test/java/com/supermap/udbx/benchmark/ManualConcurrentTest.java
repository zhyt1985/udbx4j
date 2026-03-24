package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手动并发性能测试（非 JMH）。
 *
 * 用于快速验证并发读取性能提升。
 */
public class ManualConcurrentTest {

    private static final GeometryFactory GEOM_FACTORY = new GeometryFactory();
    private static final int DATASET_SIZE = 10_000;
    private static final int THREAD_COUNT = 10;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;

    public static void main(String[] args) throws Exception {
        Path testFile = Files.createTempDirectory("udbx-manual-concurrent-")
            .resolve("test.udbx");

        System.out.println("=== 手动并发性能测试 ===");
        System.out.println("数据集大小: " + DATASET_SIZE);
        System.out.println("线程数: " + THREAD_COUNT);
        System.out.println();

        // 创建测试数据集
        System.out.println("创建测试数据集...");
        createTestDataset(testFile, DATASET_SIZE);
        System.out.println("测试数据集创建完成: " + testFile);
        System.out.println();

        // 预热
        System.out.println("预热阶段 (" + WARMUP_ITERATIONS + " 次迭代)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            singleThreadRead(testFile);
            multiThreadRead(testFile);
        }
        System.out.println("预热完成");
        System.out.println();

        // 测试单线程读取
        System.out.println("测试单线程读取 (" + MEASUREMENT_ITERATIONS + " 次迭代)...");
        long singleThreadTotalTime = 0;
        int singleThreadTotalOps = 0;
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            int ops = singleThreadRead(testFile);
            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            singleThreadTotalTime += elapsedMs;
            singleThreadTotalOps += ops;
            System.out.println("  迭代 " + (i + 1) + ": " + elapsedMs + " ms, " + ops + " 次读取");
        }
        double singleThreadAvgTime = (double) singleThreadTotalTime / MEASUREMENT_ITERATIONS;
        double singleThreadThroughput = (double) singleThreadTotalOps / (singleThreadTotalTime / 1000.0);
        System.out.println("单线程平均耗时: " + String.format("%.2f", singleThreadAvgTime) + " ms");
        System.out.println("单线程吞吐量: " + String.format("%.2f", singleThreadThroughput) + " ops/s");
        System.out.println();

        // 测试多线程读取
        System.out.println("测试多线程读取 (" + MEASUREMENT_ITERATIONS + " 次迭代)...");
        long multiThreadTotalTime = 0;
        int multiThreadTotalOps = 0;
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            int ops = multiThreadRead(testFile);
            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            multiThreadTotalTime += elapsedMs;
            multiThreadTotalOps += ops;
            System.out.println("  迭代 " + (i + 1) + ": " + elapsedMs + " ms, " + ops + " 次读取");
        }
        double multiThreadAvgTime = (double) multiThreadTotalTime / MEASUREMENT_ITERATIONS;
        double multiThreadThroughput = (double) multiThreadTotalOps / (multiThreadTotalTime / 1000.0);
        System.out.println("多线程平均耗时: " + String.format("%.2f", multiThreadAvgTime) + " ms");
        System.out.println("多线程吞吐量: " + String.format("%.2f", multiThreadThroughput) + " ops/s");
        System.out.println();

        // 计算性能提升
        double speedup = singleThreadThroughput / multiThreadThroughput;
        double concurrencyImprovement = (multiThreadThroughput / singleThreadThroughput);

        System.out.println("=== 性能对比结果 ===");
        System.out.println("单线程吞吐量: " + String.format("%.2f", singleThreadThroughput) + " ops/s");
        System.out.println("多线程吞吐量: " + String.format("%.2f", multiThreadThroughput) + " ops/s");
        System.out.println("并发性能提升: " + String.format("%.2f", concurrencyImprovement) + "x");
        System.out.println("目标: 3-5x 并发改进");

        if (concurrencyImprovement >= 3.0) {
            System.out.println("✓ 达到目标");
        } else if (concurrencyImprovement >= 2.0) {
            System.out.println("△ 接近目标");
        } else {
            System.out.println("✗ 未达到目标");
        }

        // 清理
        Files.deleteIfExists(testFile);
        System.out.println();
        System.out.println("测试完成，临时文件已清理");
    }

    private static int singleThreadRead(Path file) {
        try (UdbxDataSource ds = UdbxDataSource.open(file.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            return dataset.getFeatures().size();
        } catch (Exception e) {
            throw new RuntimeException("Single thread read failed", e);
        }
    }

    private static int multiThreadRead(Path file) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger totalCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    totalCount.addAndGet(readInThread(file));
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Multi-thread read interrupted", e);
        }

        return totalCount.get();
    }

    private static int readInThread(Path file) {
        try (UdbxDataSource ds = UdbxDataSource.open(file.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            return dataset.getFeatures().size();
        } catch (Exception e) {
            System.err.println("Thread read failed: " + e.getMessage());
            return 0;
        }
    }

    private static void createTestDataset(Path file, int count) throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.create(file.toString())) {
            PointDataset pd = ds.createPointDataset("points", 4326);
            for (int i = 0; i < count; i++) {
                Point p = GEOM_FACTORY.createPoint(
                    new Coordinate(116.0 + i * 0.0001, 39.0 + i * 0.0001));
                pd.addFeature(i + 1, p, Map.of());
            }
        }
    }
}
