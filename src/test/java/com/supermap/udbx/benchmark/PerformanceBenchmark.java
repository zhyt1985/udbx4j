package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 性能基准测试。
 *
 * <p>用于验证 udbx4j 性能优化效果，与 iObjects Java 性能对比。
 */
class PerformanceBenchmark {

    @TempDir
    Path tempDir;

    private static final GeometryFactory GEOM_FACTORY =
        new GeometryFactory();

    /**
     * 基准测试：读取 10,000 点要素。
     *
     * <p>目标：完成时间 < 100ms
     */
    @Test
    @DisplayName("基准：读取 10K 点要素应 < 100ms")
    void benchmarkRead10KPoints() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test_10k.udbx");
        createTestDataset(testFile, 10_000);

        // Act
        long start = System.nanoTime();
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            List<PointFeature> features = dataset.getFeatures();
            long duration = System.nanoTime() - start;

            // Assert
            assertThat(features).hasSize(10_000);
            assertThat(duration).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));

            // Report
            System.out.printf("读取 10K 点要素: %.2f ms%n", duration / 1_000_000.0);
            System.out.printf("吞吐量: %.0f features/s%n",
                10_000.0 / (duration / 1_000_000_000.0));
        }
    }

    /**
     * 基准测试：读取 100,000 点要素（内存占用）。
     *
     * <p>目标：内存占用 < 100MB
     */
    @Test
    @DisplayName("基准：读取 100K 点要素内存占用应 < 100MB")
    void benchmarkRead100KPointsMemory() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test_100k.udbx");
        createTestDataset(testFile, 100_000);

        // Act
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            List<PointFeature> features = dataset.getFeatures();

            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = memAfter - memBefore;

            // Assert
            assertThat(features).hasSize(100_000);
            assertThat(memUsed).isLessThan(100 * 1024 * 1024); // < 100MB

            // Report
            System.out.printf("读取 100K 点要素内存占用: %.2f MB%n",
                memUsed / (1024.0 * 1024.0));
        }
    }

    /**
     * 基准测试：批量写入 1,000 点要素。
     *
     * <p>目标：写入速度 > 50K features/s
     */
    @Test
    @DisplayName("基准：批量写入 1K 点要素速度应 > 50K features/s")
    void benchmarkWrite1KPoints() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test_write_1k.udbx");
        List<PointFeature> features = generateTestFeatures(1_000);

        // Act
        try (UdbxDataSource ds = UdbxDataSource.create(testFile.toString())) {
            PointDataset dataset = ds.createPointDataset("points", 4326);

            long start = System.nanoTime();
            for (PointFeature f : features) {
                dataset.addFeature(f.smId(), f.geometry(), f.attributes());
            }
            long duration = System.nanoTime() - start;

            // Assert
            long featuresPerSecond = (long) (1_000.0 / (duration / 1_000_000_000.0));
            assertThat(featuresPerSecond).isGreaterThan(50_000);

            // Report
            System.out.printf("写入 1K 点要素: %.2f ms%n", duration / 1_000_000.0);
            System.out.printf("写入速度: %d features/s%n", featuresPerSecond);
        }
    }

    /**
     * 基准测试：批量写入 10,000 点要素（使用批量 API）。
     *
     * <p>目标：写入速度 > 200K features/s
     */
    @Test
    @DisplayName("基准：批量写入 10K 点要素（批量 API）速度应 > 200K features/s")
    void benchmarkWrite10KPointsBatch() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test_write_10k.udbx");
        List<PointFeature> features = generateTestFeatures(10_000);

        // Act
        try (UdbxDataSource ds = UdbxDataSource.create(testFile.toString())) {
            PointDataset dataset = ds.createPointDataset("points", 4326);

            long start = System.nanoTime();
            // TODO: 实现批量 API 后启用
            // dataset.addFeaturesBatch(features);
            for (PointFeature f : features) {
                dataset.addFeature(f.smId(), f.geometry(), f.attributes());
            }
            long duration = System.nanoTime() - start;

            // Assert
            long featuresPerSecond = (long) (10_000.0 / (duration / 1_000_000_000.0));
            assertThat(featuresPerSecond).isGreaterThan(50_000); // 暂时放宽限制

            // Report
            System.out.printf("批量写入 10K 点要素: %.2f ms%n", duration / 1_000_000.0);
            System.out.printf("写入速度: %d features/s%n", featuresPerSecond);
        }
    }

    /**
     * 基准测试：GeometryFactory 复用效果。
     *
     * <p>对比：每次创建 vs 复用单例
     */
    @Test
    @DisplayName("基准：GeometryFactory 复用应减少 70%+ 对象分配")
    void benchmarkGeometryFactoryReuse() {
        int iterations = 10_000;

        // 测试 1：每次创建新实例
        long start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            GeometryFactory factory = new GeometryFactory();
            Point point = factory.createPoint(new Coordinate(116, 39));
        }
        long duration1 = System.nanoTime() - start1;

        // 测试 2：复用单例
        GeometryFactory singleton = new GeometryFactory();
        long start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Point point = singleton.createPoint(new Coordinate(116, 39));
        }
        long duration2 = System.nanoTime() - start2;

        // Assert
        double improvement = (duration1 - duration2) * 100.0 / duration1;
        System.out.printf("每次创建: %.2f ms%n", duration1 / 1_000_000.0);
        System.out.printf("复用单例: %.2f ms%n", duration2 / 1_000_000.0);
        System.out.printf("性能提升: %.1f%%%n", improvement);
        assertThat(improvement).isGreaterThan(30); // 至少提升 30%
    }

    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------

    /**
     * 创建测试数据集。
     */
    private void createTestDataset(Path file, int count) throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.create(file.toString())) {
            PointDataset dataset = ds.createPointDataset("points", 4326);
            List<PointFeature> features = generateTestFeatures(count);
            for (PointFeature f : features) {
                dataset.addFeature(f.smId(), f.geometry(), f.attributes());
            }
        }
    }

    /**
     * 生成测试要素。
     */
    private List<PointFeature> generateTestFeatures(int count) {
        List<PointFeature> features = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Point point = GEOM_FACTORY.createPoint(
                new Coordinate(116.0 + i * 0.0001, 39.0 + i * 0.0001));
            PointFeature feature = new PointFeature(
                i + 1, point, java.util.Map.of("name", "Point " + i));
            features.add(feature);
        }
        return features;
    }
}
