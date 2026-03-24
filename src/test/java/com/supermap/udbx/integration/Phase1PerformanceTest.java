package com.supermap.udbx.integration;

import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.UdbxDataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 1 性能测试 - 验证 GeometryFactory 对象池和 ArrayList 预分配优化效果。
 *
 * <p>测试方法：
 * 1. 复制 SampleData.udbx 到临时文件
 * 2. 测量读取 10K 点要素的时间
 * 3. 与基线对比（基线：4.516 ms）
 *
 * <p>预期目标：15-25% 性能提升（4.516 ms → 3.4-3.8 ms）
 */
public class Phase1PerformanceTest {

    @Test
    @Disabled("手动运行性能测试，避免影响 CI/CD")
    public void testRead10KPointsPerformance() throws Exception {
        Path tempFile = Files.createTempFile("phase1-perf-", ".udbx");
        try {
            Files.copy(Path.of("src/test/resources/SampleData.udbx"), tempFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            try (UdbxDataSource ds = UdbxDataSource.open(tempFile.toString())) {
                PointDataset pointDataset = (PointDataset) ds.getDataset("BaseMap_P");
                assertNotNull(pointDataset);

                // 预热 JVM
                for (int i = 0; i < 5; i++) {
                    pointDataset.getFeatures();
                }

                // 正式测量
                long totalElapsed = 0;
                int iterations = 20;

                for (int i = 0; i < iterations; i++) {
                    long start = System.nanoTime();
                    List<PointFeature> features = pointDataset.getFeatures();
                    long elapsed = System.nanoTime() - start;
                    totalElapsed += elapsed;

                    // 验证数据完整性
                    assertFalse(features.isEmpty());
                }

                double avgMs = (totalElapsed / iterations) / 1_000_000.0;
                System.out.printf("阶段 1 平均读取时间: %.3f ms%n", avgMs);
                System.out.printf("基线时间: 4.516 ms%n");
                System.out.printf("性能提升: %.1f%%%n", (4.516 - avgMs) / 4.516 * 100);

                // 阶段 1 目标：15-25% 提升
                double targetMin = 4.516 * 0.75;  // 3.387 ms (25% 提升)
                double targetMax = 4.516 * 0.85;  // 3.839 ms (15% 提升)

                assertTrue(avgMs >= targetMin && avgMs <= targetMax,
                    String.format("期望 %.3f-%.3f ms，实际 %.3f ms", targetMin, targetMax, avgMs));
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
