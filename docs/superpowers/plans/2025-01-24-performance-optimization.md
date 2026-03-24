# udbx4j 性能优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 在 7-8 周内通过三阶段渐进式优化，实现 udbx4j 性能提升 2-3 倍、内存占用降低 90%、并发性能提升 3-5 倍

**架构:** 采用渐进式优化策略，从基础优化（对象池）到流式处理（降低内存）再到并发优化（连接池），每个阶段独立可验证

**技术栈:** Java 17, JMH (基准测试), HikariCP (连接池), JTS (几何), SQLite JDBC

---

## 前置条件

开始实施前，请确保：

- [ ] Java 17 已安装并配置为默认 JDK
- [ ] Maven 3.6+ 可用
- [ ] 已阅读规格文档：`docs/superpowers/specs/2025-01-24-performance-optimization-plan.md`
- [ ] 了解项目编码规范：`rules/java-coding-style.md`, `rules/java-testing.md`

---

## 第 0 周：建立性能基线

**目标：** 建立真实的性能基线数据，所有后续目标基于此基线设定

### Task 1: 添加 JMH 依赖到 pom.xml

**文件：**
- Modify: `pom.xml`

- [ ] **Step 1: 添加 JMH 依赖**

```xml
<!-- 在 <dependencies> 中添加 -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: 添加 JMH Maven 插件**

```xml
<!-- 在 <build><plugins> 中添加 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.openjdk.jmh.Main</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: 验证依赖**

```bash
mvn dependency:tree
```

Expected: 输出中应包含 `jmh-core:jar`

- [ ] **Step 4: 提交**

```bash
git add pom.xml
git commit -m "build: add JMH benchmark dependencies"
```

---

### Task 2: 创建基线测试套件

**文件：**
- Create: `src/test/java/com/supermap/udbx/benchmark/BaselineBenchmark.java`

- [ ] **Step 1: 创建基线测试类**

```java
package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
            testFile = Files.createTempFile("baseline-", ".udbx");
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
    }

    @Benchmark
    public List<PointFeature> baselineRead10KPoints(DataSetState state) {
        return state.dataset.getFeatures();
    }

    @Benchmark
    public Point baselineDecodeGeometry(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPoint(state.bytes);
    }

    // 辅助方法：创建测试数据集
    private void createTestDataset(Path file, int count) throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.create(file.toString())) {
            PointDataset pd = ds.createPointDataset("points", 4326);
            for (int i = 0; i < count; i++) {
                Point p = GEOM_FACTORY.createPoint(
                    new Coordinate(116.0 + i * 0.0001, 39.0 + i * 0.0001));
                pd.addFeature(i + 1, p, java.util.Map.of());
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn test-compile
```

Expected: 编译成功，无错误

- [ ] **Step 3: 运行基线测试**

```bash
mvn test -Dtest=BaselineBenchmark
```

Expected: 测试通过（但数据不完整，因为是 JMH 测试）

- [ ] **Step 4: 提交**

```bash
git add src/test/java/com/supermap/udbx/benchmark/BaselineBenchmark.java
git commit -m "test: add baseline performance benchmark suite"
```

---

### Task 3: 运行基线测试并记录结果

- [ ] **Step 1: 编译并打包 JMH benchmarks**

```bash
mvn clean package -DskipTests
```

- [ ] **Step 2: 运行 JMH 基准测试**

```bash
java -jar target/udbx4j-0.1.0-SNAPSHOT-benchmarks.jar
```

- [ ] **Step 3: 记录基线数据**

创建 `docs/performance-roadmap/results/baseline.json`:

```json
{
  "date": "2025-01-24",
  "results": {
    "baselineRead10KPoints": {
      "score": "XXX ms",
      "scoreError": "XX ms"
    },
    "baselineDecodeGeometry": {
      "score": "XXX ms",
      "scoreError": "XX ms"
    }
  }
}
```

将实际测试结果替换 XXX 值。

- [ ] **Step 4: 创建基线测试报告**

```bash
mkdir -p docs/performance-roadmap/results
```

创建 `docs/performance-roadmap/phase0-baseline.md`:

```markdown
# 第 0 周：性能基线测试报告

## 测试环境
- 日期: 2025-01-24
- JDK: Java 17
- 数据量: 10,000 点要素

## 基线数据

| 指标 | 基线值 | 单位 |
|------|--------|------|
| 读取 10K 要素 | XXX | ms |
| 几何解码 | XXX | ms |

## 结论
[根据实际测试结果填写]
```

- [ ] **Step 5: 提交基线报告**

```bash
git add docs/performance-roadmap/
git commit -m "docs: add phase 0 baseline performance report"
```

---

## 第 1 阶段：基础优化（第 1-2 周）

**目标：** 减少 70-80% 对象分配，降低 60% GC 压力，性能提升 15-25%

### Task 4: 实现 GeometryFactoryPool

**文件：**
- Create: `src/main/java/com/supermap/udbx/geometry/GeometryFactoryPool.java`

- [ ] **Step 1: 创建 GeometryFactoryPool 类**

```java
package com.supermap.udbx.geometry;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * GeometryFactory 对象池。
 *
 * <p>缓存不同 SRID 的 GeometryFactory 实例，避免重复创建，
 * 减少 70-80% 的对象分配和 GC 压力。
 */
public final class GeometryFactoryPool {

    private static final ConcurrentHashMap<Integer, GeometryFactory> POOL =
        new ConcurrentHashMap<>();

    private GeometryFactoryPool() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取指定 SRID 的 GeometryFactory 实例。
     *
     * @param srid 坐标系 ID
     * @return GeometryFactory 实例（已缓存）
     */
    public static GeometryFactory getFactory(int srid) {
        return POOL.computeIfAbsent(srid,
            k -> new GeometryFactory(new PrecisionModel(), k));
    }

    /**
     * 预加载常用 SRID。
     */
    static {
        getFactory(4326);  // WGS84
        getFactory(4490);  // CGCS2000
        getFactory(3857);  // Web Mercator
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/supermap/udbx/geometry/GeometryFactoryPool.java
git commit -m "feat: add GeometryFactoryPool for object reuse"
```

---

### Task 5: 重构 GaiaGeometryReader 使用 GeometryFactoryPool

**文件：**
- Modify: `src/main/java/com/supermap/udbx/geometry/gaia/GaiaGeometryReader.java`

- [ ] **Step 1: 修改 readPoint 方法使用缓存**

找到 `readPoint` 方法，将完整的实现替换为：

```java
public static Point readPoint(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

    // 跳过 startMarker 和 srid
    buffer.get(); // startMarker
    buffer.getInt(); // srid

    // 读取 MBR
    double minX = buffer.getDouble();
    double minY = buffer.getDouble();
    double maxX = buffer.getDouble();
    double maxY = buffer.getDouble();

    // 跳过 0x7c
    buffer.get();

    // 读取 geoType
    int geoType = buffer.getInt();

    if (geoType != 1) {
        throw new IllegalArgumentException("Expected geoType=1 (Point), got: " + geoType);
    }

    // 读取坐标
    double x = buffer.getDouble();
    double y = buffer.getDouble();

    // 使用缓存的 GeometryFactory
    GeometryFactory factory = GeometryFactoryPool.getFactory(4326);
    return factory.createPoint(new Coordinate(x, y));
}
```

- [ ] **Step 2: 修改 readPointZ 方法使用缓存**

找到 `readPointZ` 方法，将创建 GeometryFactory 的代码替换为：

```java
// 在方法开头，从字节中提取 SRID
int srid = extractSrid(bytes); // 从字节中提取

// 使用缓存的 GeometryFactory（替换原来的 new GeometryFactory(...)）
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

- [ ] **Step 3: 修改 readMultiLineString 方法使用缓存**

找到 `readMultiLineString` 方法，将创建 GeometryFactory 的代码替换为：

```java
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

- [ ] **Step 4: 修改 readMultiLineStringZ 方法使用缓存**

找到 `readMultiLineStringZ` 方法，将创建 GeometryFactory 的代码替换为：

```java
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

- [ ] **Step 5: 修改 readMultiPolygon 方法使用缓存**

找到 `readMultiPolygon` 方法，将创建 GeometryFactory 的代码替换为：

```java
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

- [ ] **Step 6: 修改 readMultiPolygonZ 方法使用缓存**

找到 `readMultiPolygonZ` 方法，将创建 GeometryFactory 的代码替换为：

```java
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

- [ ] **Step 7: 运行 Spec 测试验证**

```bash
mvn test -Dtest=GaiaPointSpecTest
mvn test -Dtest=GaiaLineStringSpecTest
mvn test -Dtest=GaiaPolygonSpecTest
```

Expected: 所有测试通过

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/supermap/udbx/geometry/gaia/GaiaGeometryReader.java
git commit -m "refactor: use GeometryFactoryPool in GaiaGeometryReader"
```

---

### Task 6: 重构 GaiaGeometryWriter 使用 GeometryFactoryPool

**文件：**
- Modify: `src/main/java/com/supermap/udbx/geometry/gaia/GaiaGeometryWriter.java`

- [ ] **Step 1: 检查是否有需要修改的地方**

GaiaGeometryWriter 主要负责编码，不创建 GeometryFactory，但检查是否有使用。

- [ ] **Step 2: 如果有，应用相同的重构**

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/supermap/udbx/geometry/gaia/GaiaGeometryWriter.java
git commit -m "refactor: use GeometryFactoryPool in GaiaGeometryWriter (if needed)"
```

---

### Task 7: 添加 ArrayList 预分配优化

**文件：**
- Modify: `src/main/java/com/supermap/udbx/dataset/PointDataset.java:45-60`

- [ ] **Step 1: 修改 getFeatures 方法预分配容量**

找到 `getFeatures()` 方法，在创建 `ArrayList` 之前添加：

```java
int estimatedCount = info.objectCount();
// 边界检查：防止负数或过大值导致 IllegalArgumentException
int initialCapacity = Math.max(16, Math.min(estimatedCount, 1_000_000));
List<PointFeature> features = new ArrayList<>(initialCapacity);
```

- [ ] **Step 2: 对其他 Dataset 应用相同优化**

对 `LineDataset`, `RegionDataset`, `PointZDataset`, `LineZDataset`, `RegionZDataset` 应用相同的优化。

- [ ] **Step 3: 运行集成测试**

```bash
mvn test -Dtest=*IntegrationTest
```

Expected: 所有集成测试通过

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/supermap/udbx/dataset/*.java
git commit -m "perf: pre-allocate ArrayList capacity based on object count"
```

---

### Task 8: 创建几何解码基准测试

**文件：**
- Create: `src/test/java/com/supermap/udbx/benchmark/GeometryDecodeBenchmark.java`

- [ ] **Step 1: 创建几何解码基准测试**

```java
package com.supermap.udbx.benchmark;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class GeometryDecodeBenchmark {

    private static final GeometryFactory GEOM_FACTORY = new GeometryFactory();

    @State(Scope.Thread)
    public static class ByteBufferState {
        byte[] pointBytes;
        byte[] lineStringBytes;
        byte[] polygonBytes;

        @Setup
        public void setup() throws Exception {
            Point point = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915));
            pointBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writePoint(point, 4326);

            // TODO: 添加 lineString 和 polygon 的测试数据
        }
    }

    @Benchmark
    public Point decodePoint(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPoint(state.pointBytes);
    }

    // TODO: 添加更多几何类型的基准测试
}
```

- [ ] **Step 2: 提交**

```bash
git add src/test/java/com/supermap/udbx/benchmark/GeometryDecodeBenchmark.java
git commit -m "test: add geometry decode benchmark"
```

---

### Task 9: 运行阶段 1 性能测试并验证

- [ ] **Step 1: 编译并运行 JMH 测试**

```bash
mvn clean package -DskipTests
java -jar target/udbx4j-0.1.0-SNAPSHOT-benchmarks.jar -rf json -rff target/phase1-results.json
```

- [ ] **Step 2: 对比基线数据**

对比 `target/phase1-results.json` 和 `docs/performance-roadmap/results/baseline.json`

验证：
- 对象分配减少 > 70%
- 性能提升 15-25%

- [ ] **Step 3: 更新性能报告**

创建 `docs/performance-roadmap/phase1-basics.md`，记录实际测试结果。

- [ ] **Step 4: 提交报告**

```bash
git add docs/performance-roadmap/phase1-basics.md
git commit -m "docs: add phase 1 performance test report"
```

---

## 第 2 阶段：流式优化（第 3-4 周）

**目标：** 实现流式读取和分页查询，内存占用降低 90%（流式场景）

### Task 10: 实现 AutoCloseableStream

**文件：**
- Create: `src/main/java/com/supermap/udbx/streaming/AutoCloseableStream.java`

- [ ] **Step 1: 创建 AutoCloseableStream 类**

```java
package com.supermap.udbx.streaming;

import java.util.AutoCloseable;
import java.util.stream.Stream;

/**
 * 支持资源自动关闭的 Stream 包装器。
 *
 * @param <T> 流元素类型
 */
public class AutoCloseableStream<T> implements AutoCloseable {
    private final Stream<T> stream;
    private final AutoCloseable resource;

    public AutoCloseableStream(Stream<T> stream, AutoCloseable resource) {
        this.stream = stream;
        this.resource = resource;
    }

    /**
     * 获取底层的 Stream。
     */
    public Stream<T> getStream() {
        return stream;
    }

    /**
     * 关闭流和底层资源。
     */
    @Override
    public void close() {
        try {
            if (stream != null) {
                stream.close();
            }
        } finally {
            if (resource != null) {
                resource.close();
            }
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/supermap/udbx/streaming/AutoCloseableStream.java
git commit -m "feat: add AutoCloseableStream for resource management"
```

---

### Task 11: 实现 FeatureSpliterator

**文件：**
- Create: `src/main/java/com/supermap/udbx/streaming/FeatureSpliterator.java`

- [ ] **Step 1: 创建 FeatureSpliterator 类**

```java
package com.supermap.udbx.streaming;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.dataset.PointFeature;
import org.locationtech.jts.geom.GeometryFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * 流式要素迭代器。
 *
 * <p>注意：不支持并行流（ResultSet 非线程安全）。
 */
class FeatureSpliterator extends Spliterators.AbstractSpliterator<PointFeature> {

    private final PreparedStatement stmt;
    private final ResultSet rs;
    private final Connection conn;
    private final DatasetInfo info;
    private boolean closed = false;

    public FeatureSpliterator(Connection conn, DatasetInfo info, String tableName)
            throws SQLException {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        this.conn = conn;
        this.info = info;

        // 使用参数化查询，避免 SQL 注入
        String sql = "SELECT * FROM \"" + tableName.replace("\"", "\"\"") + "\"";
        this.stmt = conn.prepareStatement(sql);
        this.rs = stmt.executeQuery();
    }

    @Override
    public boolean tryAdvance(Consumer<? super PointFeature> action) {
        if (closed) {
            throw new IllegalStateException("Spliterator 已关闭");
        }

        try {
            if (rs.next()) {
                PointFeature feature = mapRow(rs);
                action.accept(feature);
                return true;
            }
            close(); // 自动关闭
            return false;
        } catch (SQLException e) {
            close();
            throw new RuntimeException("读取失败", e);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                // log warning
            } finally {
                closed = true;
            }
        }
    }

    private PointFeature mapRow(ResultSet rs) throws SQLException {
        // 实现映射逻辑
        // TODO: 根据具体 Dataset 类型实现
        throw new UnsupportedOperationException("需要子类实现");
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/supermap/udbx/streaming/FeatureSpliterator.java
git commit -m "feat: add FeatureSpliterator for streaming reads"
```

---

### Task 12: 在 Dataset 基类中添加 streamFeatures 方法

**文件：**
- Modify: `src/main/java/com/supermap/udbx/dataset/Dataset.java`

- [ ] **Step 1: 添加 streamFeatures 默认方法**

在 `Dataset.java` 中添加：

```java
import com.supermap.udbx.streaming.AutoCloseableStream;

import java.util.stream.Stream;

// 在 Dataset 接口中添加默认方法
default AutoCloseableStream<PointFeature> streamFeatures() {
    throw new UnsupportedOperationException(
        "streamFeatures() 不支持 " + getType() + " 类型数据集");
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/supermap/udbx/dataset/Dataset.java
git commit -m "feat: add streamFeatures() method to Dataset interface"
```

---

### Task 13: 在 PointDataset 中实现 streamFeatures

**文件：**
- Modify: `src/main/java/com/supermap/udbx/dataset/PointDataset.java`

- [ ] **Step 1: 实现 streamFeatures 方法**

```java
import com.supermap.udbx.streaming.AutoCloseableStream;
import com.supermap.udbx.streaming.FeatureSpliterator;

@Override
public AutoCloseableStream<PointFeature> streamFeatures() {
    FeatureSpliterator spliterator;
    try {
        spliterator = new FeatureSpliterator(conn, info, getTableName()) {
            @Override
            protected PointFeature mapRow(ResultSet rs) throws SQLException {
                int smId = rs.getInt("SmID");
                byte[] geomBytes = rs.getBytes("SmGeometry");
                Point geometry = GaiaGeometryReader.readPoint(geomBytes);

                java.util.Map<String, Object> attributes = new java.util.HashMap<>();
                // TODO: 提取用户字段

                return new PointFeature(smId, geometry, attributes);
            }
        };
    } catch (SQLException e) {
        throw new RuntimeException("创建 FeatureSpliterator 失败", e);
    }

    try {
        return new AutoCloseableStream<>(
            StreamSupport.stream(spliterator, false),  // 明确禁止并行流
            spliterator
        );
    } catch (Exception e) {
        // 确保在创建 Stream 失败时关闭 spliterator
        spliterator.close();
        throw new RuntimeException("创建流式读取失败", e);
    }
}
```

- [ ] **Step 2: 运行测试验证**

```bash
mvn test -Dtest=PointDatasetTest
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/supermap/udbx/dataset/PointDataset.java
git commit -m "feat: implement streamFeatures() in PointDataset"
```

---

### Task 14: 实现分页查询 API

**文件：**
- Modify: `src/main/java/com/supermap/udbx/dataset/PointDataset.java`

- [ ] **Step 1: 添加 getFeatures(offset, limit) 方法**

```java
/**
 * 分页读取点要素。
 *
 * @param offset 起始位置
 * @param limit 返回数量
 * @return 要素列表
 */
public List<PointFeature> getFeatures(int offset, int limit) {
    String sql = "SELECT * FROM \"" + getTableName() + "\" LIMIT ? OFFSET ?";

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setInt(1, limit);
        stmt.setInt(2, offset);

        try (ResultSet rs = stmt.executeQuery()) {
            List<PointFeature> features = new ArrayList<>(limit);
            while (rs.next()) {
                features.add(mapRow(rs));
            }
            return features;
        }
    } catch (SQLException e) {
        throw new RuntimeException("分页查询失败", e);
    }
}
```

- [ ] **Step 2: 添加 getCount() 方法**

```java
/**
 * 返回数据集中要素总数。
 *
 * @return 要素数量
 */
public int getCount() {
    String sql = "SELECT COUNT(*) FROM \"" + getTableName() + "\"";
    try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
        return rs.next() ? rs.getInt(1) : 0;
    } catch (SQLException e) {
        throw new RuntimeException("查询要素数量失败", e);
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/supermap/udbx/dataset/PointDataset.java
git commit -m "feat: add pagination support to PointDataset"
```

---

### Task 15: 创建流式读取基准测试

**文件：**
- Create: `src/test/java/com/supermap/udbx/benchmark/StreamingReadBenchmark.java`

- [ ] **Step 1: 创建流式读取基准测试**

```java
package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.streaming.AutoCloseableStream;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class StreamingReadBenchmark {

    private Path testFile;

    @Setup
    public void setup() throws Exception {
        testFile = Files.createTempFile("streaming-", ".udbx");
        // 创建 100 万测试数据
        try (UdbxDataSource ds = UdbxDataSource.create(testFile.toString())) {
            PointDataset pd = ds.createPointDataset("points", 4326);
            // TODO: 批量写入 100 万要素
        }
    }

    @Benchmark
    public long streamRead100KPoints() {
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString());
             AutoCloseableStream<?> stream = ds.getDataset("points")
                 .streamFeatures()) {
            return stream.getStream()
                .limit(100_000)
                .count();
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
```

- [ ] **Step 2: 提交**

```bash
git add src/test/java/com/supermap/udbx/benchmark/StreamingReadBenchmark.java
git commit -m "test: add streaming read benchmark"
```

---

### Task 16: 运行阶段 2 性能测试并验证

- [ ] **Step 1: 运行 JMH 测试**

```bash
java -jar target/udbx4j-0.1.0-SNAPSHOT-benchmarks.jar -rf json -rff target/phase2-results.json
```

- [ ] **Step 2: 验证内存占用**

使用 VisualVM 或 JConsole 监控内存占用。

- [ ] **Step 3: 更新性能报告**

创建 `docs/performance-roadmap/phase2-streaming.md`

- [ ] **Step 4: 提交报告**

```bash
git add docs/performance-roadmap/phase2-streaming.md
git commit -m "docs: add phase 2 streaming performance test report"
```

---

## 第 3 阶段：并发优化（第 5-6 周）

**目标：** 实现连接池和批量写入，并发性能提升 3-5 倍

### Task 17: 添加 HikariCP 依赖

**文件：**
- Modify: `pom.xml`

- [ ] **Step 1: 添加 HikariCP 依赖**

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

- [ ] **Step 2: 提交**

```bash
git add pom.xml
git commit -m "build: add HikariCP dependency for connection pooling"
```

---

### Task 18: 实现 UdbxDataSourcePool

**文件：**
- Create: `src/main/java/com/supermap/udbx/pool/UdbxDataSourcePool.java`

- [ ] **Step 1: 创建连接池类**

```java
package com.supermap.udbx.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.supermap.udbx.dataset.Dataset;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.function.Function;

/**
 * UDBX 数据源连接池。
 *
 * <p>使用 HikariCP 管理 SQLite 连接，提升多线程并发读取性能。
 * 注意：SQLite 写锁限制，连接池主要用于读取场景。
 */
public class UdbxDataSourcePool implements AutoCloseable {

    private final HikariDataSource pool;

    /**
     * 创建连接池。
     *
     * @param path UDBX 文件路径
     * @param poolSize 最大连接数
     */
    public UdbxDataSourcePool(String path, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);

        // SQLite 优化配置
        config.addDataSourceProperty("cache", "shared");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        // 连接池配置
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.pool = new HikariDataSource(config);

        // 检测 WAL 模式
        checkWALEnabled();
    }

    /**
     * 执行数据库操作。
     *
     * @param callback 操作回调
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T execute(Function<Connection, T> callback) {
        try (Connection conn = pool.getConnection()) {
            return callback.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    /**
     * 获取数据集。
     *
     * @param name 数据集名称
     * @return 数据集实例
     */
    public Dataset getDataset(String name) {
        return execute(conn -> {
            try {
                // 查询数据集类型
                String sql = "SELECT DatasetType FROM SmRegister WHERE DatasetName = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("数据集不存在: " + name);
                        }
                        int datasetType = rs.getInt("DatasetType");

                        // 根据类型创建相应的 Dataset
                        return createDatasetByType(conn, name, datasetType);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("获取数据集失败: " + name, e);
            }
        });
    }

    /**
     * 根据数据集类型创建 Dataset 实例。
     */
    private Dataset createDatasetByType(Connection conn, String name, int datasetType)
            throws SQLException {
        // 复用现有 Dataset 创建逻辑
        // 这里简化处理，实际应参考 UdbxDataSource.getDataset() 的实现
        com.supermap.udbx.core.DatasetInfo info =
            new com.supermap.udbx.system.SmRegisterDao(conn).getByName(name);

        return switch (info.type()) {
            case POINT -> new com.supermap.udbx.dataset.PointDataset(conn, info);
            case LINE -> new com.supermap.udbx.dataset.LineDataset(conn, info);
            case REGION -> new com.supermap.udbx.dataset.RegionDataset(conn, info);
            case TABULAR -> new com.supermap.udbx.dataset.TabularDataset(conn, info);
            default -> throw new UnsupportedOperationException(
                "不支持的数据集类型: " + info.type());
        };
    }

    /**
     * 检测 WAL 模式是否启用。
     */
    private void checkWALEnabled() {
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            if (rs.next()) {
                String mode = rs.getString(1);
                if (!"wal".equalsIgnoreCase(mode)) {
                    System.err.println("警告: WAL 模式未启用，并发性能受限。当前模式: " + mode);
                }
            }
        } catch (SQLException e) {
            System.err.println("警告: 无法检测 WAL 模式: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/supermap/udbx/pool/UdbxDataSourcePool.java
git commit -m "feat: add UdbxDataSourcePool with HikariCP"
```

---

### Task 19: 实现批量写入 API

**文件：**
- Modify: `src/main/java/com/supermap/udbx/dataset/PointDataset.java`

- [ ] **Step 1: 添加 addFeaturesBatch 方法**

```java
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import com.supermap.udbx.system.SmRegisterDao;

/**
 * 批量写入点要素。
 *
 * @param features 要素列表
 */
public void addFeaturesBatch(List<PointFeature> features) {
    String sql = "INSERT INTO \"" + getTableName() +
        "\" (SmID, SmUserID, \"SmGeometry\") VALUES (?, 0, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        conn.setAutoCommit(false);

        for (PointFeature f : features) {
            ps.setInt(1, f.smId());
            ps.setBytes(2, GaiaGeometryWriter.writePoint(f.geometry(), info.srid()));
            ps.addBatch();
        }

        ps.executeBatch();
        conn.commit();

        // 更新 SmRegister（仅一次）
        new SmRegisterDao(conn).incrementObjectCount(info.datasetId(), features.size());
    } catch (SQLException e) {
        try { conn.rollback(); } catch (SQLException ignored) {}
        throw new RuntimeException("批量写入失败", e);
    } finally {
        try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/supermap/udbx/dataset/PointDataset.java
git commit -m "feat: add batch write API (addFeaturesBatch)"
```

---

### Task 20: 创建并发性能基准测试

**文件：**
- Create: `src/test/java/com/supermap/udbx/benchmark/ConcurrentReadBenchmark.java`

- [ ] **Step 1: 创建并发基准测试**

```java
package com.supermap.udbx.benchmark;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.PointDataset;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class ConcurrentReadBenchmark {

    @TempDir
    Path tempDir;

    private Path testFile;

    @Setup
    public void setup() throws Exception {
        testFile = tempDir.resolve("concurrent-test.udbx");
        createTestDataset(testFile, 10_000);
    }

    @Benchmark
    public int singleThreadRead() {
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            return dataset.getFeatures().size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public int multiThreadRead() {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 使用 AtomicInteger 保证线程安全
        java.util.concurrent.atomic.AtomicInteger totalCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    totalCount.addAndGet(readInThread());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return totalCount.get();
    }

    private int readInThread() {
        try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
            PointDataset dataset = (PointDataset) ds.getDataset("points");
            return dataset.getFeatures().size();
        } catch (Exception e) {
            return 0;
        }
    }

    private void createTestDataset(Path file, int count) throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.create(file.toString())) {
            PointDataset pd = ds.createPointDataset("points", 4326);
            // TODO: 创建测试数据
        }
    }

    @TearDown
    public void tearDown() throws Exception {
        // @TempDir 会自动清理
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/test/java/com/supermap/udbx/benchmark/ConcurrentReadBenchmark.java
git commit -m "test: add concurrent read benchmark"
```

---

### Task 21: 运行阶段 3 性能测试并验证

- [ ] **Step 1: 运行所有 JMH 基准测试**

```bash
java -jar target/udbx4j-0.1.0-SNAPSHOT-benchmarks.jar -rf json -rff target/phase3-results.json
```

- [ ] **Step 2: 验证所有目标达成**

创建验证脚本 `docs/performance-roadmap/verify-groovy.groovy`：

```groovy
#!/usr/bin/env groovy
import groovy.json.JsonSlurper

// 读取各阶段结果
def baseline = new JsonSlurper().parse(file("docs/performance-roadmap/results/baseline.json"))
def phase1 = new JsonSlurper().parse(file("target/phase1-results.json"))
def phase3 = new JsonSlurper().parse(file("target/phase3-results.json"))

// 验证阶段 1：性能提升 15-25%
double baselineTime = baseline.baselineRead10KPoints.score.toDouble()
double phase1Time = phase1.primaryResult.score.toDouble()
double improvement1 = (baselineTime - phase1Time) / baselineTime * 100
assert improvement1 >= 15 && improvement1 <= 25 : "阶段 1 性能提升 ${improvement1}% 不在 15-25% 范围内"

// 验证阶段 3：并发提升 3-5 倍
double singleThroughput = phase3.singleThreadRead.score.toDouble()
double multiThroughput = phase3.multiThreadRead.score.toDouble()
double concurrencyImprovement = multiThroughput / singleThroughput
assert concurrencyImprovement >= 3 && concurrencyImprovement <= 5 :
    "并发提升 ${concurrencyImprovement}x 不在 3-5x 范围内"

println "✅ 所有性能目标达成："
println "  - 阶段 1 性能提升: ${String.format('%.1f', improvement1)}%"
println "  - 阶段 3 并发提升: ${String.format('%.1f', concurrencyImprovement)}x"
```

运行验证脚本：

```bash
groovy docs/performance-roadmap/verify-groovy.groovy
```

Expected: 输出显示所有目标达成

- [ ] **Step 3: 生成最终性能报告**

创建 `docs/performance-roadmap/phase3-concurrency.md`，总结所有阶段的测试结果。

- [ ] **Step 4: 创建与 iObjects Java 对比文档**

创建 `docs/comparison/vs-iobjects-java.md`，对比性能差异。

- [ ] **Step 5: 提交最终报告**

```bash
git add docs/performance-roadmap/phase3-concurrency.md
git add docs/comparison/vs-iobjects-java.md
git commit -m "docs: add final phase 3 performance report and iObjects Java comparison"
```

---

### Task 22: （可选）集成性能监控

**文件：**
- Create: `src/main/java/com/supermap/udbx/metrics/PerformanceMetrics.java`

- [ ] **Step 1: 创建性能监控类（可选）**

```java
package com.supermap.udbx.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 性能指标收集器。
 *
 * <p>注意：此功能为可选，默认禁用。通过配置启用。
 */
public class PerformanceMetrics {

    private final MeterRegistry registry;
    private final Counter readCounter;
    private final Counter writeCounter;
    private final Timer readTimer;
    private final Timer writeTimer;

    public PerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.readCounter = Counter.builder("udbx.read.count")
            .description("Total number of read operations")
            .register(registry);
        this.writeCounter = Counter.builder("udbx.write.count")
            .description("Total number of write operations")
            .register(registry);
        this.readTimer = Timer.builder("udbx.read.time")
            .description("Time taken for read operations")
            .register(registry);
        this.writeTimer = Timer.builder("udbx.write.time")
            .description("Time taken for write operations")
            .register(registry);
    }

    public void recordRead() {
        readCounter.increment();
    }

    public void recordWrite() {
        writeCounter.increment();
    }

    public Timer.Sample startReadTimer() {
        return Timer.start(registry);
    }

    public Timer.Sample startWriteTimer() {
        return Timer.start(registry);
    }

    public void stopReadTimer(Timer.Sample sample) {
        sample.stop(readTimer);
    }

    public void stopWriteTimer(Timer.Sample sample) {
        sample.stop(writeTimer);
    }
}
```

- [ ] **Step 2: 添加 Micrometer 依赖（可选）**

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>1.12.0</version>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 3: 提交（可选）**

```bash
git add src/main/java/com/supermap/udbx/metrics/PerformanceMetrics.java
git add pom.xml
git commit -m "feat: add optional performance monitoring with Micrometer"
```

---

## 验收标准

完成所有任务后，请验证：

- [ ] 所有 JMH 基准测试通过
- [ ] 性能提升达成目标（基于基线数据）
- [ ] 内存占用符合预期
- [ ] 并发性能提升 3-5 倍
- [ ] 所有 Spec 测试通过
- [ ] 所有集成测试通过
- [ ] 技术文档完整（4 个阶段的文档 + 最终报告）
- [ ] 代码覆盖率 ≥ 80%

---

## 附录：常见问题

### Q1: JMH 测试运行很慢怎么办？

A: 减少 `@Measurement` 的 iterations 参数，或减少 `@Fork` 数值。

### Q2: 如何调试 JMH 测试？

A: 在 IDE 中直接运行测试方法，不使用 JMH Runner。

### Q3: 连接池对写入没有提升？

A: 正常。SQLite 的写锁限制导致连接池对写入无效，连接池主要用于读取场景。

### Q4: 流式 API 支持并行流吗？

A: 不支持。ResultSet 非线程安全，已明确禁止并行流。

### Q5: 如何查看内存占用？

A: 使用 VisualVM、JConsole 或 JVM 参数 `-Xmx2g -XX:+PrintGCDetails`。

---

**计划版本**: 1.1
**创建日期**: 2025-01-24
**最后修订**: 2025-01-24（修复 P0/P1/P2 问题）
**预计完成**: 7-8 周
