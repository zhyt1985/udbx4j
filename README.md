# udbx4j

超图 UDBX 空间数据格式的 Java 读写库

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.zhyt1985/udbx4j)](https://central.sonatype.com/artifact/io.github.zhyt1985/udbx4j)

> **🎉 v1.0.0 已发布！** 现已发布到 Maven Central，可通过 Maven 依赖使用。

## 简介

`udbx4j` 是超图 UDBX 空间数据格式的纯 Java 读写库。UDBX（Universal Spatial Database Extension）基于 SQLite 存储，支持矢量（点/线/面/CAD）和栅格空间数据。

本项目实现了对 UDBX 格式的独立读写，无需依赖 SuperMap iObjects Java 组件。

## 特性

- ✅ **高性能** - 纯 Java 实现，零 JNI 开销
  - 单线程读取性能提升 **92.5%**（实测：0.34ms vs 4.5ms 基线）
  - 多线程并发扩展 **3.49 倍**（实测：7.94M vs 2.27M ops/s）
  - 批量写入性能提升 **10-50 倍**（使用 addFeaturesBatch API）
  - 分页查询内存开销仅 **+22%**
- ✅ **高稳定性** - 无 JNI 内存泄漏风险
  - JVM GC 自动管理内存，零泄漏
  - 无许可依赖，部署更简单
  - 详细的 Java 异常堆栈，易调试
- ✅ **纯 Java 实现** - 无需原生依赖
  - 单个 JAR 包，跨平台无障碍
  - 支持 GraalVM Native Image
- ✅ **完整的数据集支持**
  - 纯属性表（Tabular）
  - 矢量数据：点、线、面（Point/Line/Region）
  - 三维矢量：PointZ、LineZ、RegionZ
  - CAD 数据集
- ✅ **标准生态集成** - JDBC + JTS
  - 无缝集成 Spring Boot、连接池、监控
  - 支持 HikariCP、MyBatis、Hibernate
- ✅ **SpatiaLite 兼容** - GAIA Geometry 格式支持
- ✅ **轻量级** - 基于 JDBC + SQLite，无重型依赖
- ✅ **测试驱动** - Spec 测试 + 集成测试双重保障

## 快速开始

### 环境要求

- Java 17 或更高版本
- Maven 3.6+

### Maven 依赖

```xml
<dependency>
    <groupId>io.github.zhyt1985</groupId>
    <artifactId>udbx4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 代码示例

#### 打开 UDBX 文件

```java
import com.supermap.udbx.UdbxDataSource;

// 打开现有 UDBX 文件
try (UdbxDataSource dataSource = UdbxDataSource.open("path/to/data.udbx")) {
    // 列出所有数据集
    dataSource.getDatasetNames().forEach(System.out::println);
}
```

#### 读取点数据

```java
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;

try (UdbxDataSource dataSource = UdbxDataSource.open("points.udbx")) {
    PointDataset dataset = (PointDataset) dataSource.getDataset("PointData");

    // 遍历所有要素
    for (PointFeature feature : dataset) {
        System.out.printf("ID: %d, X: %.2f, Y: %.2f%n",
            feature.getId(),
            feature.getGeometry().getX(),
            feature.getGeometry().getY());
    }
}
```

#### 创建新数据集

```java
import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.geometry.gaia.GaiaPoint;

// 创建新 UDBX 文件
try (UdbxDataSource dataSource = UdbxDataSource.create("output.udbx")) {
    // 创建字段信息
    List<FieldInfo> fields = List.of(
        new FieldInfo("名称", FieldType.CHAR, 50),
        new FieldInfo("数量", FieldType.INT32)
    );

    // 创建点数据集
    PointDataset dataset = dataSource.createPointDataset("MyPoints", fields);

    // 添加要素
    PointFeature feature = dataset.createFeature();
    feature.setGeometry(new GaiaPoint(116.404, 39.915)); // 北京坐标
    feature.setString("名称", "天安门");
    feature.setInt32("数量", 100);
    dataset.add(feature);
}
```

#### 流式读取（大数据集）

```java
import com.supermap.udbx.streaming.AutoCloseableStream;

try (UdbxDataSource dataSource = UdbxDataSource.open("large_dataset.udbx")) {
    PointDataset dataset = (PointDataset) dataSource.getDataset("BigData");

    // 流式读取，内存占用恒定
    try (AutoCloseableStream<PointFeature> stream = dataset.streamFeatures()) {
        stream.getStream()
            .filter(f -> f.geometry().getX() > 116.0)
            .limit(1000)
            .forEach(feature -> process(feature));
    } // 自动关闭资源
}
```

#### 分页查询

```java
// 分页读取点数据
try (UdbxDataSource dataSource = UdbxDataSource.open("data.udbx")) {
    PointDataset dataset = (PointDataset) dataSource.getDataset("Points");

    int pageSize = 1000;
    int totalCount = dataset.getCount();
    int pageCount = (totalCount + pageSize - 1) / pageSize;

    for (int page = 0; page < pageCount; page++) {
        List<PointFeature> features = dataset.getFeatures(page * pageSize, pageSize);
        System.out.println("Page " + page + ": " + features.size() + " features");
    }
}
```

#### 批量写入

```java
import com.supermap.udbx.dataset.PointFeature;

// 批量写入性能提升 10-50 倍
try (UdbxDataSource dataSource = UdbxDataSource.create("output.udbx")) {
    PointDataset dataset = (PointDataset) dataSource.getDataset("Points");

    List<PointFeature> batch = new ArrayList<>();
    for (int i = 0; i < 10000; i++) {
        PointFeature feature = dataset.createFeature();
        feature.setGeometry(new GaiaPoint(116.0 + i * 0.0001, 39.0 + i * 0.0001));
        batch.add(feature);
    }

    // 单次事务批量写入
    dataset.addFeaturesBatch(batch);
}
```

## 项目结构

```
udbx4j/
├── README.md                          # 项目介绍和快速开始
├── CHANGELOG.md                       # 版本变更记录
├── CONTRIBUTING.md                     # 贡献指南
├── CLAUDE.md                          # 项目开发文档
├── LICENSE                            # MIT 许可证
├── pom.xml                            # Maven 构建配置
├── Makefile                           # 构建脚本（预设 JAVA_HOME）
│
├── docs/                              # 文档目录
│   └── performance-roadmap/           # 性能优化路线图
│       ├── phase0-baseline.md         # Phase 0: 性能基线
│       ├── phase1-basics.md           # Phase 1: 基础优化（92.5% 提升）
│       ├── phase2-streaming.md        # Phase 2: 流式优化
│       ├── phase3-concurrency.md      # Phase 3: 并发优化（3.49x 扩展）
│       └── phase3-summary.md          # Phase 3: 总结报告
│
├── rules/                             # 开发规范
│   ├── java-coding-style.md          # Java 编码规范
│   ├── java-testing.md               # 测试规范
│   ├── java-patterns.md               # 设计模式
│   └── spec-coding.md                # Spec 测试流程
│
├── .github/                           # GitHub 配置
│   ├── ISSUE_TEMPLATE/                # Issue 模板
│   │   ├── bug_report.md              # Bug 报告模板
│   │   └── feature_request.md         # 功能请求模板
│   └── PULL_REQUEST_TEMPLATE.md       # PR 模板
│
└── src/
    ├── main/java/com/supermap/udbx/
    │   ├── UdbxDataSource.java         # 入口类：open()/create()
    │   ├── pool/                       # 对象池
    │   │   └── GeometryFactoryPool.java # GeometryFactory 对象池
    │   ├── core/                       # 核心枚举与元信息
    │   │   ├── DatasetType.java        # 数据集类型枚举
    │   │   ├── GeometryType.java       # 几何类型枚举
    │   │   ├── FieldType.java          # 字段类型枚举
    │   │   ├── FieldInfo.java          # 字段元信息
    │   │   └── DatasetInfo.java        # 数据集元信息
    │   ├── dataset/                    # 数据集实现
    │   │   ├── Dataset.java            # 抽象基类
    │   │   ├── TabularDataset.java     # 纯属性表
    │   │   ├── VectorDataset.java      # 矢量基类
    │   │   ├── PointDataset.java       # 点数据集
    │   │   ├── LineDataset.java        # 线数据集
    │   │   ├── RegionDataset.java      # 面数据集
    │   │   ├── PointZDataset.java      # 三维点数据集
    │   │   ├── LineZDataset.java       # 三维线数据集
    │   │   ├── RegionZDataset.java     # 三维面数据集
    │   │   └── CadDataset.java         # CAD 数据集
    │   ├── geometry/                   # 几何对象
    │   │   ├── gaia/                   # SpatiaLite GAIA 格式
    │   │   │   ├── GaiaGeometryReader.java
    │   │   │   ├── GaiaGeometryWriter.java
    │   │   │   └── ...
    │   │   └── cad/                    # SuperMap CAD 格式
    │   │       ├── CadGeometryReader.java
    │   │       ├── CadGeometryWriter.java
    │   │       ├── CadGeometry.java
    │   │       └── ...
    │   ├── streaming/                  # 流式处理
    │   │   ├── AutoCloseableStream.java # 资源管理
    │   │   └── FeatureSpliterator.java  # 懒加载迭代器
    │   ├── pool/                       # 连接池
    │   │   └── UdbxDataSourcePool.java  # HikariCP 连接池
    │   ├── metrics/                    # 性能监控（可选）
    │   │   └── PerformanceMetrics.java  # Micrometer 集成
    │   ├── system/                     # 系统表 DAO
    │   │   ├── SmRegisterDao.java
    │   │   ├── SmFieldInfoDao.java
    │   │   └── SmDataSourceInfoDao.java
    │   └── viewer/                     # 可视化工具（实验性）
    │
    └── test/java/com/supermap/udbx/
        ├── spec/                       # Spec 测试（基于白皮书）
        │   └── *SpecTest.java           # 21 个 Spec 测试类
        ├── integration/                # 集成测试
        │   └── *Test.java               # 14 个集成测试类
        └── benchmark/                  # JMH 性能基准测试
            ├── BaselineBenchmark.java   # 基线测试
            ├── GeometryDecodeBenchmark.java
            ├── StreamingReadBenchmark.java
            ├── ConcurrentReadBenchmark.java
            └── ManualConcurrentTest.java
```

## 构建与测试

### 使用 Makefile（推荐）

```bash
make test            # 运行 Spec 测试
make test-all        # 运行所有测试 + 覆盖率报告
make run-test CLASS=GaiaPointSpecTest  # 运行单个测试类
make coverage        # 打开覆盖率报告
```

### 使用 Maven

```bash
mvn compile
mvn test
mvn verify           # 含集成测试 + 覆盖率检查
mvn package -DskipTests
```

## 技术架构

### UDBX 格式要点

| 概念 | 说明 |
|------|------|
| 文件格式 | SQLite 数据库（`.udbx` 后缀） |
| 字节序 | Little-Endian |
| 矢量几何 | GAIA Geometry（SpatiaLite 标准格式） |
| CAD 几何 | SuperMap GeoHeader 自定义格式 |
| 系统表 | SmRegister、SmFieldInfo、SmDataSourceInfo |

### Geometry 二进制格式

**GAIA（SpatiaLite）：**
```
0x00 | byteOrder(1) | srid(int32) | MBR(4×double) | 0x7c | geoType(int32) | ...coords... | 0xFE
```

**CAD（SuperMap GeoHeader）：**
```
geoType(int32) | styleSize(int32) | Style(...) | ...geometry data...
```

## 开发规范

- **测试驱动开发**（TDD）- 先写测试，再写实现
- **不可变优先** - 元信息类使用 Java Record
- **测试覆盖率** ≥ 80%
- **提交格式** - `feat/fix/test/refactor/docs/chore: 描述`

详细规范见 `rules/` 目录。

## 参考文档

- **白皮书**：`UDBX开放数据格式白皮书(V1.0).pdf`
- **性能报告**：`docs/performance-roadmap/`（Phase 0-3 优化报告）
- **开发规范**：`rules/` 目录

## 许可证

[MIT License](LICENSE)

## 作者

zhyt1985

## 相关链接

- [UDBX 格式白皮书](UDBX开放数据格式白皮书(V1.0).pdf)
- [SuperMap 官方文档](https://www.supermap.com/)
- [SpatiaLite 文档](https://www.gaia-gis.it/gaia-sins/)
