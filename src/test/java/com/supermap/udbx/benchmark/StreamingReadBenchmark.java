package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.streaming.AutoCloseableStream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 流式读取性能基准测试。
 *
 * <p>对比流式读取与传统批量读取在处理大量数据时的性能差异。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class StreamingReadBenchmark {
    private Path testFile;
    private static final int TEST_POINT_COUNT = 100_000;
    private static final int SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Setup
    public void setup() throws Exception {
        testFile = Files.createTempFile("streaming-", ".udbx");
        try (UdbxDataSource ds = UdbxDataSource.create(testFile.toString())) {
            PointDataset pd = ds.createPointDataset("points", SRID);
            // 创建 10 万个测试点（批量写入，使用事务）
            pd.getConnection().setAutoCommit(false);
            for (int i = 0; i < TEST_POINT_COUNT; i++) {
                Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(
                    116.0 + (i % 1000) * 0.001,
                    39.0 + (i / 1000) * 0.001
                ));
                pd.addFeature(i + 1, point, null);
                // 每 1000 条提交一次
                if ((i + 1) % 1000 == 0) {
                    pd.getConnection().commit();
                }
            }
            pd.getConnection().commit();
            pd.getConnection().setAutoCommit(true);
        }
    }

    @Benchmark
    public long streamRead100KPoints() {
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             AutoCloseableStream<?> stream = ds.getDataset("points").streamFeatures()) {
            return stream.getStream().count();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public int batchRead100KPoints() {
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             PointDataset pd = (PointDataset) ds.getDataset("points")) {
            return pd.getFeatures().size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public int paginatedRead100KPoints() {
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
            return totalCount;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @TearDown
    public void tearDown() throws Exception {
        if (testFile != null) {
            Files.deleteIfExists(testFile);
        }
    }
}
