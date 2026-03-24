# udbx4j 性能优化研发计划

**版本**: 1.0
**日期**: 2025-01-24
**状态**: Draft
**负责人**: udbx4j 开发团队

---

## 文档概述

本文档定义了 udbx4j 在 1-2 个月短期内的性能优化研发计划，以"轻量级、性能优先"为核心目标，采用三阶段渐进式优化策略。

**核心目标**：
- 读取性能提升 2-3 倍（vs iObjects Java）
- 内存占用降低 80-90%
- 并发性能提升 3-5 倍

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

## 2. 三阶段优化计划

### 2.1 阶段 1：基础优化（第 1-2 周）

**目标**：减少对象分配，降低 GC 压力，性能提升 30-50%

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

**2. 重构 GaiaGeometryReader/Writer**

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

| 指标 | 当前 | 目标 | 提升 |
|------|------|------|------|
| 对象分配 | 100% | 20-30% | ↓ 70-80% |
| GC 时间 | 基线 | -60% | ↓ 60% |
| 读取 10K 要素 | ~200ms | <140ms | ↑ 30-50% |

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

**1. 流式读取 API**

```java
// 在 Dataset 基类中新增
public default Stream<PointFeature> streamFeatures() {
    return StreamSupport(new FeatureSpliterator(
        this.conn, this.info, this.getTableName()
    ), false);
}

// 使用示例
try (UdbxDataSource ds = UdbxDataSource.open("large.udbx")) {
    PointDataset dataset = (PointDataset) ds.getDataset("points");
    dataset.streamFeatures()
        .filter(f -> f.geometry().getX() > 116.0)
        .limit(1000)
        .forEach(f -> process(f));
}
```

**FeatureSpliterator 实现**：

```java
class FeatureSpliterator extends Spliterators.AbstractSpliterator<PointFeature> {
    private final PreparedStatement stmt;
    private final ResultSet rs;

    public FeatureSpliterator(Connection conn, DatasetInfo info, String tableName)
            throws SQLException {
        super(Long.MAX_VALUE, Spliterator.ORDERED);
        this.stmt = conn.prepareStatement("SELECT * FROM \"" + tableName + "\"");
        this.rs = stmt.executeQuery();
    }

    @Override
    public boolean tryAdvance(Consumer<? super PointFeature> action) {
        try {
            if (rs.next()) {
                action.accept(mapRow(rs));
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            // log warning
        }
    }
}
```

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

#### 预期收益

| 指标 | 当前 | 目标 | 提升 |
|------|------|------|------|
| 100K 要素内存 | ~500MB | <100MB | ↓ 80% |
| 首次加载时间 | ~2s | <500ms | ↑ 75% |
| 支持最大数据集 | ~10万 | 无限 | 流式处理 |

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

**1. HikariCP 连接池集成**

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
    }

    public <T> T execute(Function<Connection, T> callback) {
        try (Connection conn = pool.getConnection()) {
            return callback.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    // 获取数据集
    public Dataset getDataset(String name) {
        return execute(conn -> {
            // 复用现有 Dataset 创建逻辑
            return createDataset(conn, name);
        });
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
```

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
void concurrentReadTest() throws InterruptedException {
    Path testFile = createTestDataset(10_000);
    int threadCount = 10;

    long singleThreadStart = System.nanoTime();
    try (UdbxDataSource ds = UdbxDataSource.open(testFile.toString())) {
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
                dataset.getFeatures();
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    long multiThreadDuration = System.nanoTime() - multiThreadStart;
    executor.shutdown();

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

| 指标 | 基线 | 阶段1 | 阶段2 | 阶段3 | vs iObjects Java |
|------|------|-------|-------|-------|------------------|
| 读 10K 要素 | ~200ms | <140ms | <100ms | <100ms | **2x** |
| 读 100K 要素 | ~2s | <1.5s | <500ms | <500ms | **3x** |
| 内存（100K） | ~500MB | <400MB | <100MB | <100MB | **50%** |
| 批量写 1K | ~100ms | <80ms | <80ms | <20ms | **5x** |
| 并发 10 线程 | 1x | 1.5x | 1.5x | 3-5x | **5x** |

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

**总工作量**：24 个工作日 ≈ 6 周

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

## 8. 后续规划

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
