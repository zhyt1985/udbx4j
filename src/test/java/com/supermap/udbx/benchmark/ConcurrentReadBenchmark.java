package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发读取性能基准测试。
 *
 * 测试目标：验证多线程并发读取的性能提升，目标为 3-5x 的并发改进。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class ConcurrentReadBenchmark {

    private static final GeometryFactory GEOM_FACTORY = new GeometryFactory();
    private static final int DATASET_SIZE = 10_000;
    private static final int THREAD_COUNT = 10;

    @State(Scope.Thread)
    public static class ConcurrentState {
        Path testFile;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            String fileName = "concurrent-" + java.util.UUID.randomUUID() + ".udbx";
            testFile = Files.createTempDirectory("udbx-concurrent-").resolve(fileName);
            createTestDataset(testFile, DATASET_SIZE);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            if (testFile != null) {
                Files.deleteIfExists(testFile);
            }
        }

        private void createTestDataset(Path file, int count) throws Exception {
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

    /**
     * 单线程读取基线测试。
     *
     * @return 读取到的要素数量
     */
    @Benchmark
    public int singleThreadRead(ConcurrentState state) {
        try (UdbxDataSource ds = UdbxDataSource.open(state.testFile.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            List<?> features = dataset.getFeatures();
            return features.size();
        } catch (Exception e) {
            throw new RuntimeException("Single thread read failed", e);
        }
    }

    /**
     * 多线程并发读取测试（10 线程）。
     *
     * 使用 AtomicInteger 线程安全地累加所有线程读取的要素总数。
     *
     * @return 所有线程读取的要素总数
     */
    @Benchmark
    public int multiThreadRead(ConcurrentState state) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger totalCount = new AtomicInteger(0);

        // 启动多个线程并发读取
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    totalCount.addAndGet(readInThread(state.testFile));
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
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

    /**
     * 单个线程的读取逻辑。
     *
     * @param file UDBX 文件路径
     * @return 读取到的要素数量
     */
    private int readInThread(Path file) {
        try (UdbxDataSource ds = UdbxDataSource.open(file.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            List<?> features = dataset.getFeatures();
            return features.size();
        } catch (Exception e) {
            // 记录错误但不中断其他线程
            System.err.println("Thread read failed: " + e.getMessage());
            return 0;
        }
    }
}
