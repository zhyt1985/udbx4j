package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class BaselineBenchmark {

    private static final GeometryFactory GEOM_FACTORY = new GeometryFactory();

    @State(Scope.Thread)
    public static class ByteBufferState {
        byte[] bytes;

        @Setup
        public void setup() throws Exception {
            Point point = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915));
            bytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writePoint(point, 4326);
        }
    }

    @State(Scope.Thread)
    public static class DataSetState {
        Path testFile;
        UdbxDataSource dataSource;
        PointDataset dataset;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            // 使用 UUID 生成唯一文件名，但不创建文件
            String fileName = "baseline-" + java.util.UUID.randomUUID() + ".udbx";
            testFile = java.nio.file.Files.createTempDirectory("udbx-bench-").resolve(fileName);
            createTestDataset(testFile, 10_000);
            dataSource = UdbxDataSource.open(testFile.toString());
            dataset = (PointDataset) dataSource.getDataset("points");
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            if (dataSource != null) {
                dataSource.close();
            }
            if (testFile != null) {
                Files.deleteIfExists(testFile);
            }
        }

        /**
         * 创建测试数据集（辅助方法）。
         *
         * @param file  UDBX 文件路径
         * @param count 要插入的点要素数量
         */
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

    @Benchmark
    public List<PointFeature> baselineRead10KPoints(DataSetState state) {
        return state.dataset.getFeatures();
    }

    @Benchmark
    public Point baselineDecodeGeometry(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPoint(state.bytes);
    }
}
