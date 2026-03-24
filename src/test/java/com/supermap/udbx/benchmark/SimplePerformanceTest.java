package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.streaming.AutoCloseableStream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 简化的性能测试工具，用于测量流式读取的性能改进。
 */
public class SimplePerformanceTest {
    private static final int TEST_POINT_COUNT = 100_000;
    private static final int SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 阶段 2：流式优化性能测试 ===\n");

        // 创建测试数据
        Path testFile = Files.createTempFile("perf-test-", ".udbx");
        Files.deleteIfExists(testFile);  // 删除空文件，准备创建 UDBX
        System.out.println("1. 创建测试数据文件: " + testFile);
        try (UdbxDataSource ds = UdbxDataSource.create(testFile.toString())) {
            PointDataset pd = ds.createPointDataset("points", SRID);
            System.out.println("   写入 " + TEST_POINT_COUNT + " 个点要素...");
            long writeStart = System.currentTimeMillis();
            for (int i = 0; i < TEST_POINT_COUNT; i++) {
                Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(
                    116.0 + (i % 1000) * 0.001,
                    39.0 + (i / 1000) * 0.001
                ));
                pd.addFeature(i + 1, point, null);
            }
            long writeTime = System.currentTimeMillis() - writeStart;
            System.out.println("   写入完成，耗时: " + writeTime + " ms\n");
        }

        // 测试批量读取（基线）
        System.out.println("2. 批量读取测试（加载所有数据到内存）:");
        System.gc();
        Thread.sleep(1000);
        long batchStartMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long batchStart = System.currentTimeMillis();
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             PointDataset pd = (PointDataset) ds.getDataset("points")) {
            int count = pd.getFeatures().size();
            long batchTime = System.currentTimeMillis() - batchStart;
            long batchEndMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long batchMemUsed = (batchEndMem - batchStartMem) / (1024 * 1024);
            System.out.println("   读取要素数: " + count);
            System.out.println("   耗时: " + batchTime + " ms");
            System.out.println("   内存增长: ~" + batchMemUsed + " MB\n");
        }

        // 测试流式读取
        System.out.println("3. 流式读取测试（懒加载）:");
        System.gc();
        Thread.sleep(1000);
        long streamStartMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long streamStart = System.currentTimeMillis();
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             AutoCloseableStream<?> stream = ds.getDataset("points").streamFeatures()) {
            long count = stream.getStream().count();
            long streamTime = System.currentTimeMillis() - streamStart;
            long streamEndMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long streamMemUsed = (streamEndMem - streamStartMem) / (1024 * 1024);
            System.out.println("   读取要素数: " + count);
            System.out.println("   耗时: " + streamTime + " ms");
            System.out.println("   内存增长: ~" + streamMemUsed + " MB\n");
        }

        // 测试分页查询
        System.out.println("4. 分页查询测试（每页 1000 条）:");
        System.gc();
        Thread.sleep(1000);
        long pageStartMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long pageStart = System.currentTimeMillis();
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             PointDataset pd = (PointDataset) ds.getDataset("points")) {
            int totalCount = 0;
            int pageSize = 1000;
            int offset = 0;
            while (true) {
                var batch = pd.getFeatures(offset, pageSize);
                if (batch.isEmpty()) break;
                totalCount += batch.size();
                offset += pageSize;
            }
            long pageTime = System.currentTimeMillis() - pageStart;
            long pageEndMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long pageMemUsed = (pageEndMem - pageStartMem) / (1024 * 1024);
            System.out.println("   读取要素数: " + totalCount);
            System.out.println("   耗时: " + pageTime + " ms");
            System.out.println("   内存增长: ~" + pageMemUsed + " MB\n");
        }

        // 测试 getCount
        System.out.println("5. getCount() 性能测试:");
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             PointDataset pd = (PointDataset) ds.getDataset("points")) {
            long countStart = System.currentTimeMillis();
            int count = pd.getCount();
            long countTime = System.currentTimeMillis() - countStart;
            System.out.println("   要素总数: " + count);
            System.out.println("   耗时: " + countTime + " ms\n");
        }

        // 清理
        Files.deleteIfExists(testFile);
        System.out.println("测试完成，临时文件已清理");
    }
}
