package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;

import java.io.File;
import java.util.List;

/**
 * 手动运行基线测试（不使用 JMH）。
 * 用于获取初始性能数据。
 */
public class ManualBaselineTest {

    public static void main(String[] args) throws Exception {
        String testFile = "src/test/resources/SampleData.udbx";
        File file = new File(testFile);

        if (!file.exists()) {
            System.err.println("测试文件不存在: " + testFile);
            System.exit(1);
        }

        System.out.println("=== 基线性能测试 ===");
        System.out.println("测试文件: " + testFile);
        System.out.println("文件大小: " + (file.length() / 1024 / 1024) + " MB");
        System.out.println();

        try (UdbxDataSource dataSource = UdbxDataSource.open(testFile)) {
            // 使用已知的数据集名称（从 SampleData.udbx 中获取）
            PointDataset dataset = null;
            String[] datasetNames = {"BaseMap_P", "points", "PointDataset", "P"};

            for (String name : datasetNames) {
                var ds = dataSource.getDataset(name);
                if (ds instanceof PointDataset) {
                    dataset = (PointDataset) ds;
                    System.out.println("找到数据集: " + name);
                    break;
                }
            }

            if (dataset == null) {
                System.err.println("未找到点数据集，尝试的数据集: " + String.join(", ", datasetNames));
                System.exit(1);
            }

            System.out.println("数据集名称: " + dataset.getName());
            System.out.println();

            // 预热
            System.out.println("预热中...");
            for (int i = 0; i < 5; i++) {
                dataset.getFeatures();
            }

            // 测试读取 10K 要素
            System.out.println("测试读取 10K 要素...");
            int iterations = 10;
            long totalTime = 0;

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                List<PointFeature> features = dataset.getFeatures();
                long end = System.nanoTime();

                long elapsedMs = (end - start) / 1_000_000;
                totalTime += elapsedMs;

                System.out.printf("  迭代 %d: %d ms (%d 要素)\n", i + 1, elapsedMs, features.size());
            }

            double avgTime = (double) totalTime / iterations;
            System.out.println();
            System.out.printf("平均时间: %.2f ms\n", avgTime);
            System.out.printf("吞吐量: %.2f 要素/秒\n", 10000.0 / (avgTime / 1000.0));
        }
    }
}
