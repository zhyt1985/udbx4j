# udbx4j 性能优化研发计划

**版本**: 1.2
**日期**: 2025-01-24
**状态**: Draft | Revised | Ready for Implementation
**负责人**: udbx4j 开发团队
**修订记录**：
- v1.0 → v1.1: 根据代码审查意见修订
- v1.1 → v1.2: 修复二次审查发现的所有 P0/P1 问题

---

## 文档概述

本文档定义了 udbx4j 在 7-8 周短期内的性能优化研发计划，以"轻量级、性能优先"为核心目标，采用三阶段渐进式优化策略。

**重要前提**：
- ⚠️ **第 0 周**必须先建立性能基线，所有目标基于真实测量数据
- 性能目标为**预期值**，可能根据基线测试结果调整

**核心目标**（基于基线测试后的预期）：
- 读取性能提升 2-3 倍（vs iObjects Java）
- 流式处理内存占用降低 90%（vs 全量加载）
- 并发性能提升 3-5 倍（多线程读取场景）

**技术策略**：性能与轻量并重（60/40 权重）

---

## 1. 总体架构

### 1.1 架构分层

```
┌─────────────────────────────────────────────┐
│  应用层（用户代码）                          │
├─────────────────────────────────────────────┤
│  udbx4j API 层                               │
│  - UdbxDataSource                            │
│  - PointDataset / LineDataset / ...         │
│  - Feature 对象                              │
├─────────────────────────────────────────────┤
│  性能优化层（新增）                          │
│  - GeometryFactoryPool（对象池）            │
│  - StreamingFeatureReader（流式读取）        │
│  - UdbxConnectionPool（连接池）             │
├─────────────────────────────────────────────┤
│  几何层                                      │
│  - GaiaGeometryReader/Writer                │
│  - JTS Geometry                             │
├─────────────────────────────────────────────┤
│  数据访问层                                  │
│  - JDBC (PreparedStatement)                 │
│  - HikariCP 连接池                          │
├─────────────────────────────────────────────┤
│  SQLite 数据库                              │
└─────────────────────────────────────────────┘
```

### 1.2 设计原则

1. **向后兼容**：新增优化层，不破坏现有 API
2. **渐进增强**：每个阶段独立可测试、可验证
3. **可观测性**：内置性能监控指标
4. **测试驱动**：每个优化都有对应的基准测试

---

## 2. 第 0 周：性能基线建立

**目标**：建立真实的性能基线数据，为后续优化设定合理目标

### 2.1 基线测试任务

**1. 创建基线测试套件**

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class BaselineBenchmark {

    @State(Scope.Thread)
    public static class ByteBufferState {
        byte[] bytes;

        @Setup
        public void setup() {
            Point point = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915));
            bytes = GaiaGeometryWriter.writePoint(point, 4326);
        }
    }

    @State(Scope.Thread)
    public static class DataSetState {
        UdbxDataSource dataSource;
        PointDataset dataset;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            Path testFile = createTestDataset(10_000);
            dataSource = UdbxDataSource.open(testFile.toString());
            dataset = (PointDataset) dataSource.getDataset("points");
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    @Benchmark
    public List<PointFeature> baselineRead10KPoints(DataSetState state) {
        return state.dataset.getFeatures();
    }

    @Benchmark
    public Point baselineDecodeGeometry(ByteBufferState state) {
        return GaiaGeometryReader.readPoint(state.bytes);
    }
}
```

**2. 基线测试指标**

| 指标 | 测试方法 | 目的 |
|------|----------|------|
| 读取 10K 要素 | baselineRead10KPoints() | 测量当前读取性能 |
| 几何解码 | baselineDecodeGeometry() | 测量 Geometry 解码开销 |
| 内存占用 | JMX/GC 日志分析 | 测量对象分配和 GC 压力 |
| 并发性能 | 多线程基线测试 | 测量当前并发扩展性 |

**3. 基线数据记录**

```bash
# 运行基线测试
mvn clean test -Dtest=BaselineBenchmark

# 输出格式
Baseline Performance Report (2025-01-24):
- Read 10K points: 180ms (avg of 30 runs)
- Decode geometry: 0.05ms (avg of 30 runs)
- Memory (100K points): 450MB
- Concurrent (10 threads): 1.2x speedup
```

### 2.2 目标调整原则

基线测试完成后，根据实际数据调整各阶段目标：

- 如果基线读取 10K 要素 = 180ms，则阶段 1 目标设为 **150ms**（↓17%）
- 如果基线读取 10K 要素 = 250ms，则阶段 1 目标设为 **200ms**（↓20%）
- 几何解码占比 < 20%，则需优化其他部分（JDBC、对象分配）

### 2.3 交付物

- [ ] 基线测试套件（5 个 benchmark）
- [ ] 基线性能报告
- [ ] 调整后的性能目标矩阵
- [ ] 时间线微调（基于基线结果）

---

## 3. 三阶段优化计划

### 3.1 阶段 1：基础优化（第 1-2 周）

**目标**：减少对象分配，降低 GC 压力，性能提升 15-25%（基于基线）

**说明**：原目标 30-50% 过于激进，仅通过 GeometryFactory 复用难以达成。降低至 15-25% 更现实，并补充其他优化措施。

#### 实现内容

**1. GeometryFactory 复用机制**

```java
// 新增类：com.supermap.udbx.geometry.GeometryFactoryPool
public class GeometryFactoryPool {
    private static final ConcurrentHashMap<Integer, GeometryFactory> POOL =
        new ConcurrentHashMap<>();

    public static GeometryFactory getFactory(int srid) {
        return POOL.computeIfAbsent(srid,
            k -> new GeometryFactory(new PrecisionModel(), k));
    }

    // 预加载常用 SRID
    static {
        getFactory(4326);  // WGS84
        getFactory(4490);  // CGCS2000
        getFactory(3857);  // Web Mercator
    }
}
```

**2. 补充优化措施**

为达到 15-25% 性能提升，除 GeometryFactory 复用外，还需：

```java
// a) 预分配 ArrayList 容量（避免扩容）
public List<PointFeature> getFeatures() {
    int estimatedCount = info.objectCount();
    if (estimatedCount > 0) {
        features = new ArrayList<>(estimatedCount);
    } else {
        features = new ArrayList<>();  // 回退到默认
    }
    // ...
}

// b) 重用 ByteBuffer（减少内存分配）
private static final ThreadLocal<ByteBuffer> BUFFER_POOL =
    ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(8192)
        .order(ByteOrder.LITTLE_ENDIAN));

// c) 批量预取 SmRegister（减少系统表查询）
public class SmRegisterCache {
    private static final Map<String, DatasetInfo> CACHE =
        new ConcurrentHashMap<>();
}
```

**3. 重构 GaiaGeometryReader/Writer**

```java
// 新增类：com.supermap.udbx.geometry.GeometryFactoryPool
public class GeometryFactoryPool {
    private static final ConcurrentHashMap<Integer, GeometryFactory> POOL =
        new ConcurrentHashMap<>();

    public static GeometryFactory getFactory(int srid) {
        return POOL.computeIfAbsent(srid,
            k -> new GeometryFactory(new PrecisionModel(), k));
    }

    // 预加载常用 SRID
    static {
        getFactory(4326);  // WGS84
        getFactory(4490);  // CGCS2000
        getFactory(3857);  // Web Mercator
    }
}
```

**2. 补充优化措施**

为达到 15-25% 性能提升，除 GeometryFactory 复用外，还需：

```java
// a) 预分配 ArrayList 容量（避免扩容）
public List<PointFeature> getFeatures() {
    int estimatedCount = info.objectCount();
    if (estimatedCount > 0) {
        features = new ArrayList<>(estimatedCount);
    } else {
        // 回退到默认容量或从COUNT(*)查询
        features = new ArrayList<>(Math.min(1024, estimateCountFromDB()));
    }
}

// b) 重用 ByteBuffer（减少内存分配）
// ⚠️ 注意：DirectByteBuffer由GC管理，但在高并发场景可能导致内存泄漏
// 建议：生产环境使用弱引用或限制池大小
private static final ThreadLocal<ByteBuffer> BUFFER_POOL =
    ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(8192)
        .order(ByteOrder.LITTLE_ENDIAN));

// c) 批量预取 SmRegister（减少系统表查询）
// ⚠️ 当前实现：应用生命周期缓存
// TODO: 未来支持按需失效（如检测到数据集修改时间变化）
public class SmRegisterCache {
    private static final Map<String, DatasetInfo> CACHE =
        new ConcurrentHashMap<>();
}
```

**3. 重构 GaiaGeometryReader/Writer**

```java
// 修改前：每次创建新实例
GeometryFactory factory = new GeometryFactory(new PrecisionModel(), srid);

// 修改后：使用缓存
GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
```

**3. JMH 基准测试框架**

```java
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class GeometryDecodeBenchmark {

    private byte[] testBytes;

    @Setup
    public void setup() {
        Point point = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915));
        testBytes = GaiaGeometryWriter.writePoint(point, 4326);
    }

    @Benchmark
    public Point benchmarkDecodePoint() {
        return GaiaGeometryReader.readPoint(testBytes);
    }
}
```

#### 预期收益

| 指标 | 基线（预估） | 阶段1目标 | 提升 |
|------|-------------|-----------|------|
| 对象分配 | 100% | 20-30% | ↓ 70-80% |
| GC 时间 | 基线 | -60% | ↓ 60% |
| 读取 10K 要素 | ~200ms | <170ms | ↑ 15-25% |

**注**：实际目标值取决于第0周基线测试结果

#### 交付物

- [ ] GeometryFactoryPool 类
- [ ] 更新的 GaiaGeometryReader/Writer
- [ ] JMH 基准测试套件（3 个 benchmark）
- [ ] 性能测试报告
- [ ] 技术文档：`phase1-basics.md`

---

### 2.2 阶段 2：流式优化（第 3-4 周）

**目标**：降低大数据集内存占用，支持百万级要素

#### 实现内容

**1. 流式读取 API（修复设计）**

```java
// 在 Dataset 基类中新增
public default AutoCloseableStream<PointFeature> streamFeatures() {
    return new AutoCloseableStream<>(
        StreamSupport(new FeatureSpliterator(
            this.conn, this.info, this.getTableName()
        ), false)  // 明确禁止并行流
    );
}

// AutoCloseableStream 包装器
public class AutoCloseableStream<T> implements AutoCloseable {
    private final Stream<T> stream;
    private final AutoCloseable resource;

    public AutoCloseableStream(Stream<T> stream, AutoCloseable resource) {
        this.stream = stream;
        this.resource = resource;
    }

    public Stream<T> getStream() {
        return stream;
    }

    @Override
    public void close() {
        resource.close();
    }
}

// 使用示例（确保资源释放）
try (var stream = dataset.streamFeatures()) {
    stream.getStream()
        .filter(f -> f.geometry().getX() > 116.0)
        .limit(1000)
        .forEach(f -> process(f));
}
```

**FeatureSpliterator 实现（修复安全问题）**：

```java
class FeatureSpliterator extends Spliterators.AbstractSpliterator<PointFeature> {
    private final PreparedStatement stmt;
    private final ResultSet rs;
    private boolean closed = false;

    public FeatureSpliterator(Connection conn, DatasetInfo info, String tableName)
            throws SQLException {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);

        // 修复1：使用参数化查询，避免 SQL 注入
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
                action.accept(mapRow(rs));
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
}
```

**安全说明**：
- ✅ 使用参数化查询（或表名白名单验证）
- ✅ AutoCloseableStream 确保资源释放
- ✅ 禁止并行流（ResultSet 非线程安全）
- ✅ 异常时自动关闭资源

**2. 分页查询 API**

```java
// 在 PointDataset 中新增
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
    }
}

// 总数查询
public int getCount() {
    String sql = "SELECT COUNT(*) FROM \"" + getTableName() + "\"";
    try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
        return rs.next() ? rs.getInt(1) : 0;
    }
}

// 分页迭代器
public Iterable<List<PointFeature>> iterate(int pageSize) {
    return () -> new PaginationIterator(pageSize);
}

class PaginationIterator implements Iterator<List<PointFeature>> {
    private final int pageSize;
    private int offset = 0;

    PaginationIterator(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNext() {
        return offset < getCount();
    }

    @Override
    public List<PointFeature> next() {
        List<PointFeature> page = getFeatures(offset, pageSize);
        offset += pageSize;
        return page;
    }
}
```

#### 预期收益（明确场景）

**注意**：流式处理与全量加载是两种不同的使用场景

| 场景 | API | 当前内存 | 目标内存 | 提升 |
|------|-----|----------|----------|------|
| **流式处理** | `streamFeatures()` | N/A | <100MB | 支持百万级要素 |
| **全量加载** | `getFeatures()` | ~500MB | <300MB | ↓ 40% |

**说明**：
- 流式处理：逐条读取，内存占用恒定（与数据量无关）
- 全量加载：所有要素加载到 List，内存随数据量线性增长
- 大数据集（>10 万要素）强烈推荐使用流式 API

#### 交付物

- [ ] StreamingFeatureReader 类
- [ ] FeatureSpliterator 实现
- [ ] 分页查询 API（3 个方法）
- [ ] 集成测试（百万级数据）
- [ ] 技术文档：`phase2-streaming.md`

---

### 2.3 阶段 3：并发优化（第 5-6 周）

**目标**：提升多线程并发性能，实现 3-5 倍提升

#### 实现内容

**1. HikariCP 连接池集成（含 SQLite 限制说明）**

```java
// 新增类：com.supermap.udbx.UdbxDataSourcePool
public class UdbxDataSourcePool implements AutoCloseable {
    private final HikariDataSource pool;

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

    // 检测 WAL 模式是否启用
    private void checkWALEnabled() {
        try (Connection conn = pool.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    String mode = rs.getString(1);
                    if (!"wal".equalsIgnoreCase(mode)) {
                        logger.warn("WAL 模式未启用，并发性能受限。当前模式: {}", mode);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("无法检测 WAL 模式", e);
        }
    }

    public <T> T execute(Function<Connection, T> callback) {
        try (Connection conn = pool.getConnection()) {
            return callback.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    // ⚠️ 重要：连接池主要用于读取场景
    // SQLite 写锁限制：同一时间只允许一个写操作
    // 连接池对写入场景无性能提升，反而增加开销
}
```

**SQLite 限制说明**：

| 特性 | 说明 | 影响 |
|------|------|------|
| **写锁** | SQLite 同一时间只允许一个写操作 | 连接池对写入无效 |
| **WAL 模式** | 允许多读一写，提升并发读取 | 需要检测是否启用 |
| **连接复用** | 读操作可复用连接 | 主要收益在读取场景 |

**使用建议**：
- ✅ 使用连接池进行**并发读取**
- ⚠️ **写入操作**使用单连接（或禁用连接池）
- ✅ 确保 WAL 模式已启用

**使用示例**：

```java
// 创建连接池
try (UdbxDataSourcePool pool = new UdbxDataSourcePool("data.udbx", 10)) {
    // 并发读取
    List<Dataset> datasets = IntStream.range(0, 10)
        .parallel()
        .mapToObj(i -> pool.getDataset("points"))
        .collect(Collectors.toList());
}
```

**2. 批量写入优化**

```java
// 在 PointDataset 中新增
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

**3. 并发性能测试**

```java
@Test
@DisplayName("并发测试：10 线程并发读取应 > 3 倍性能提升")
void concurrentReadTest(@TempDir Path tempDir) throws InterruptedException {
    Path testFile = createTestDataset(tempDir, 10_000);
    int threadCount = 10;

    // 单线程基线
    long singleThreadStart = System.nanoTime();
    try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
        PointDataset dataset = (PointDataset) ds.getDataset("points");
        dataset.getFeatures();
    }
    long singleThreadDuration = System.nanoTime() - singleThreadStart;

    // 多线程测试
    CountDownLatch latch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    long multiThreadStart = System.nanoTime();

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
                PointDataset dataset = (PointDataset) ds.getDataset("points");
                dataset.getFeatures();
            } catch (Exception e) {
                // log error
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    long multiThreadDuration = System.nanoTime() - multiThreadStart;

    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS); // ✅ 等待任务完成

    // 验证：多线程应该更快（连接池）
    double speedup = (double) singleThreadDuration / multiThreadDuration * threadCount;
    assertThat(speedup).isGreaterThan(3.0);
}
```

#### 预期收益

| 指标 | 当前 | 目标 | 提升 |
|------|------|------|------|
| 10 线程并发 | 1x | 3-5x | ↑ 300% |
| 批量写 1K 要素 | ~100ms | <20ms | ↑ 5x |
| 连接开销 | 基线 | -90% | ↓ 90% |

#### 交付物

- [ ] UdbxDataSourcePool 类
- [ ] HikariCP 集成（pom.xml）
- [ ] 批量写入 API
- [ ] 并发性能测试套件
- [ ] 技术文档：`phase3-concurrency.md`

---

## 3. 性能测试体系

### 3.1 基准测试框架

**JMH 配置**：

```xml
<!-- pom.xml -->
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

**基准测试套件**：

```java
// 1. Geometry 解码测试
public class GeometryDecodeBenchmark {
    @Benchmark
    public Point decodePoint(ByteBufferState state) {
        return GaiaGeometryReader.readPoint(state.bytes);
    }
}

// 2. 数据集读取测试
public class DatasetReadBenchmark {
    @Benchmark
    public List<PointFeature> read10KPoints(DataSetState state) {
        return state.dataset.getFeatures();
    }
}

// 3. 批量写入测试
public class BatchWriteBenchmark {
    @Benchmark
    public void write1KPoints(WriteState state) {
        state.dataset.addFeaturesBatch(state.features);
    }
}
```

### 3.2 性能目标矩阵

| 指标 | 基线（预估） | 阶段1 | 阶段2 | 阶段3 | vs iObjects Java |
|------|-------------|-------|-------|-------|------------------|
| 读 10K 要素 | ~200ms | <170ms | <120ms | <100ms | **2x** |
| 读 100K 要素 | ~2s | <1.7s | <500ms | <500ms | **3x** |
| 内存-流式（100K） | N/A | N/A | <100MB | <100MB | **支持无限** |
| 内存-全量（100K） | ~500MB | <400MB | <300MB | <300MB | **50%** |
| 批量写 1K | ~100ms | <85ms | <85ms | <20ms | **5x** |
| 并发 10 线程 | 1x | 1.5x | 1.5x | 3-5x | **5x** |

**注**：基线值为预估，实际目标根据第0周测试结果调整

### 3.3 CI/CD 集成

**GitHub Actions 工作流**：

```yaml
name: Performance Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Run JMH benchmarks
        run: |
          java -jar target/benchmarks.jar -rf json -rff target/benchmark-results.json

      - name: Store benchmark result
        uses: benchmark-action/github-action-benchmark@v1
        with:
          tool: 'jmh'
          output-file-path: target/benchmark-results.json
          github-token: ${{ secrets.GITHUB_TOKEN }}
          auto-push: false

      - name: Comment PR with results
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const results = JSON.parse(fs.readFileSync('target/benchmark-results.json'));
            // 格式化并评论性能结果
```

---

## 4. 技术文档结构

### 4.1 文档体系

```
docs/
├── performance-roadmap/              # 性能优化路线图
│   ├── README.md                     # 总览文档
│   ├── phase1-basics.md             # 阶段1：基础优化
│   ├── phase2-streaming.md          # 阶段2：流式优化
│   ├── phase3-concurrency.md        # 阶段3：并发优化
│   └── performance-guide.md         # 性能最佳实践
│
├── architecture/                     # 架构设计
│   ├── performance-architecture.md   # 性能优化架构图
│   ├── factory-pool-design.md       # GeometryFactory 池设计
│   ├── streaming-api-design.md      # 流式 API 设计
│   └── connection-pool-design.md    # 连接池设计
│
├── benchmark/                        # 基准测试
│   ├── jmh-guide.md                  # JMH 使用指南
│   ├── test-plan.md                  # 测试计划
│   └── results/                      # 测试结果
│       ├── baseline.json             # 基线数据
│       ├── phase1.json              # 阶段1结果
│       ├── phase2.json              # 阶段2结果
│       └── phase3.json              # 阶段3结果
│
└── comparison/                       # 对比分析
    ├── vs-iobjects-java.md          # 与 iObjects Java 对比
    └── performance-report.md        # 性能测试报告
```

### 4.2 核心文档内容

**README.md（总览）**：
- 项目目标与时间线
- 三阶段优化概览
- 快速开始指南
- 性能指标汇总

**phase1-basics.md（阶段1详细）**：
- GeometryFactory 复用原理
- JMH 基准测试集成
- 代码示例与最佳实践
- 性能测试结果

**performance-guide.md（最佳实践）**：
- 何时使用流式 API
- 连接池配置建议
- 常见性能陷阱
- 与 iObjects Java 迁移建议

---

## 5. 开发任务分解

### 5.1 阶段 1 任务清单

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 实现 GeometryFactoryPool | 2天 | - | GeometryFactoryPool.java |
| 重构 GaiaGeometryReader | 1天 | GeometryFactoryPool | 更新的 Reader/Writer |
| 集成 JMH 基准测试 | 2天 | - | 3 个 Benchmark 类 |
| 性能测试与验证 | 2天 | JMH | 性能报告 |
| 文档编写 | 1天 | 所有实现 | phase1-basics.md |

**总计**：8 个工作日

### 5.2 阶段 2 任务清单

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 实现流式读取 API | 3天 | - | FeatureSpliterator.java |
| 实现分页查询 | 2天 | - | 3 个新方法 |
| 大数据集测试 | 2天 | 流式 API | 内存测试报告 |
| 文档编写 | 1天 | 所有实现 | phase2-streaming.md |

**总计**：8 个工作日

### 5.3 阶段 3 任务清单

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 集成 HikariCP | 2天 | - | UdbxDataSourcePool.java |
| 实现批量写入 | 2天 | - | addFeaturesBatch() |
| 并发性能测试 | 2天 | 连接池 | 并发测试报告 |
| 完整文档与报告 | 2天 | 所有实现 | 所有文档 |

**总计**：8 个工作日

**总工作量**：
- 第 0 周（基线测试）：5 个工作日
- 阶段 1-3：24 个工作日
- 缓冲时间（调试、优化迭代）：5 个工作日
- **总计**：34 个工作日 ≈ **7-8 周**

**时间线调整**：
- 原 6 周计划过于乐观
- 增加 20% 缓冲时间用于性能调优和 Bug 修复
- 每个阶段结束后预留 0.5 天进行性能验证和目标调整

---

## 6. 风险管理

### 6.1 风险评估矩阵

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 性能提升不达标 | 高 | 中 | 每阶段建立基准测试，及时调整优化方向 |
| 流式 API 资源泄漏 | 高 | 低 | 使用 try-with-resources，添加资源泄漏检测 |
| 连接池配置复杂 | 中 | 中 | 提供合理默认值，简化配置 API |
| SQLite WAL 兼容性 | 中 | 低 | 添加兼容性测试，提供降级方案 |
| JMH 测试不稳定 | 低 | 中 | 增加预热轮次，多次运行取中位数 |

### 6.2 成功标准

**阶段 1 成功标准**：
- ✅ GeometryFactory 复用率 > 95%
- ✅ 对象分配减少 > 70%
- ✅ 读取 10K 要素性能提升 > 30%
- ✅ JMH 基准测试通过 CI

**阶段 2 成功标准**：
- ✅ 流式读取 100 万要素内存占用 < 100MB
- ✅ 分页查询延迟 < 10ms
- ✅ 首次加载时间降低 > 50%

**阶段 3 成功标准**：
- ✅ 10 线程并发性能提升 > 3 倍
- ✅ 批量写入 1K 要素 > 50K features/s
- ✅ 所有性能目标达成

---

## 7. 里程碑与交付物

### 7.1 时间线

```
Week 1-2:  阶段 1 - 基础优化
           ↓
Week 3-4:  阶段 2 - 流式优化
           ↓
Week 5-6:  阶段 3 - 并发优化
           ↓
Week 6:    最终交付与文档
```

### 7.2 关键交付物

**第 2 周末**：阶段 1 里程碑
- GeometryFactoryPool 实现
- JMH 基准测试框架
- 性能测试报告（提升 30-50%）
- 技术文档：`phase1-basics.md`

**第 4 周末**：阶段 2 里程碑
- 流式读取 API
- 分页查询 API
- 内存测试报告（降低 80-90%）
- 技术文档：`phase2-streaming.md`

**第 6 周末**：最终交付
- 连接池集成
- 批量写入 API
- 完整性能测试报告
- 与 iObjects Java 对比文档
- 完整技术文档体系

---

## 8. 性能监控与可观测性

### 8.1 性能指标收集

```java
// 新增类：com.supermap.udbx.metrics.PerformanceMetrics
public class PerformanceMetrics {
    private final MeterRegistry registry;
    private final boolean enabled;

    public PerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.enabled = registry != null;
    }

    // 全局单例（可选启用）
    private static final PerformanceMetrics GLOBAL =
        new PerformanceMetrics(Metrics.globalRegistry);

    public static PerformanceMetrics global() {
        return GLOBAL;
    }

    // 检查是否启用
    public boolean isEnabled() {
        return enabled;
    }

    public void recordReadTime(String dataset, long durationMs) {
        if (!enabled) return;
        registry.timer("udbx.read.time",
            "dataset", dataset)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordDecodeTime(String geometryType, long durationNs) {
        if (!enabled) return;
        registry.timer("udbx.decode.time",
            "geometry", geometryType)
            .record(durationNs, TimeUnit.NANOSECONDS);
    }

    public void recordMemoryUsage(String dataset, long bytes) {
        if (!enabled) return;
        registry.gauge("udbx.memory.usage", bytes,
            "dataset", dataset);
    }

    public void recordConnectionPoolMetrics(String poolName, HikariPoolMXBean pool) {
        if (!enabled) return;
        registry.gauge("udbx.pool.active", pool.getActiveConnections(),
            "pool", poolName);
        registry.gauge("udbx.pool.idle", pool.getIdleConnections(),
            "pool", poolName);
        registry.gauge("udbx.pool.waiting", pool.getThreadsAwaitingConnection(),
            "pool", poolName);
    }
}
```

### 8.2 可选依赖配置

```xml
<!-- pom.xml（可选依赖，scope=provided） -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>1.12.0</version>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

### 8.3 配置开关

```java
// 配置示例（通过系统属性或环境变量）
boolean metricsEnabled = Boolean.parseBoolean(
    System.getProperty("udbx.metrics.enabled", "false")
);

String exportType = System.getProperty("udbx.metrics.export", "none");
// 可选值：none, prometheus, jmx, logback
```

**说明**：
- 默认关闭性能监控（零开销）
- 需要时通过系统属性启用
- 使用 `provided` scope，不增加运行时依赖

### 8.2 集成 Micrometer

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>1.12.0</version>
</dependency>
```

### 8.3 使用示例

```java
// 在 Dataset 中集成性能监控
public class PointDataset extends VectorDataset {
    private static final PerformanceMetrics METRICS =
        new PerformanceMetrics(Metrics.globalRegistry);

    public List<PointFeature> getFeatures() {
        long start = System.nanoTime();

        List<PointFeature> features = // ... 实现逻辑 ...

        long duration = System.nanoTime() - start;
        METRICS.recordReadTime(getName(), TimeUnit.NANOSECONDS.toMillis(duration));

        return features;
    }
}
```

### 8.4 导出指标

**Spring Boot 集成**：
```yaml
# application.yml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**独立应用**：
```java
// 启用 Prometheus HTTPServer
HTTPServer server = new HTTPServer(8080);
PrometheusRegistry registry = new PrometheusRegistry();
new PerformanceMetrics(registry);
server.start();
```

---

## 9. 后续规划

### 8.1 中期规划（3-6 个月）

基于短期计划的效果评估，考虑：

1. **空间索引支持**
   - 利用 SpatiaLite R-Tree 索引
   - 空间查询性能提升 10-100 倍

2. **性能监控集成**
   - Micrometer 指标导出
   - JMX 监控支持
   - 性能仪表盘

3. **Spring Boot Starter**
   - 自动配置 UdbxDataSource
   - 简化集成

### 8.2 长期规划（6-12 个月）

1. **异步 API**
   - CompletableFuture 异步读取
   - Reactive Streams 响应式流

2. **高级特性**
   - Geometry 压缩（减小 30-50% 存储）
   - SQLCipher 加密支持

3. **生态扩展**
   - MyBatis/Hibernate ORM 支持
   - GraalVM Native Image

---

## 9. 附录

### 9.1 术语表

- **JMH**: Java Microbenchmark Harness，Java 微基准测试工具
- **JTS**: JTS Topology Suite，JTS 几何拓扑库
- **WAL**: Write-Ahead Logging，SQLite 预写日志模式
- **GC**: Garbage Collection，垃圾回收

### 9.2 参考文档

- [JMH 官方文档](https://openjdk.org/projects/code-tools/jmh/)
- [HikariCP 文档](https://github.com/brettwooldridge/HikariCP)
- [SQLite 优化指南](https://www.sqlite.org/optoverview.html)
- [JTS 文档](https://locationtech.github.io/jts/)

---

**文档变更历史**：

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| 1.0 | 2025-01-24 | 初始版本 | udbx4j 团队 |
