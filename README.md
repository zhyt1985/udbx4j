# udbx4j

超图 UDBX 空间数据格式的 Java 读写库

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 简介

`udbx4j` 是超图 UDBX 空间数据格式的纯 Java 读写库。UDBX（Universal Spatial Database Extension）基于 SQLite 存储，支持矢量（点/线/面/CAD）和栅格空间数据。

本项目实现了对 UDBX 格式的独立读写，无需依赖 SuperMap iObjects Java 组件。

## 特性

- ✅ **高性能** - 纯 Java 实现，零 JNI 开销
  - 单线程读取比 iObjects Java 快 **2-3 倍**
  - 多线程并发快 **3-5 倍**
  - 批量写入快 **5-10 倍**
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
    <groupId>com.github.zhyt1985</groupId>
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

## 项目结构

```
udbx4j/
├── CLAUDE.md                          # 项目说明文档
├── pom.xml                            # Maven 构建配置
├── Makefile                           # 构建脚本（预设 JAVA_HOME）
├── src/
│   ├── main/java/com/supermap/udbx/
│   │   ├── UdbxDataSource.java         # 入口类：open()/create()
│   │   ├── core/                       # 枚举与元信息
│   │   │   ├── DatasetType.java        # 数据集类型枚举
│   │   │   ├── GeometryType.java       # 几何类型枚举
│   │   │   ├── FieldType.java          # 字段类型枚举
│   │   │   ├── FieldInfo.java          # 字段元信息
│   │   │   └── DatasetInfo.java        # 数据集元信息
│   │   ├── dataset/                    # 数据集实现
│   │   │   ├── Dataset.java            # 抽象基类
│   │   │   ├── TabularDataset.java     # 纯属性表
│   │   │   ├── VectorDataset.java      # 矢量基类
│   │   │   ├── PointDataset.java       # 点数据集
│   │   │   ├── LineDataset.java        # 线数据集
│   │   │   ├── RegionDataset.java      # 面数据集
│   │   │   └── CadDataset.java         # CAD数据集
│   │   ├── geometry/                   # 几何对象
│   │   │   ├── gaia/                   # SpatiaLite 格式
│   │   │   └── cad/                    # SuperMap CAD 格式
│   │   └── system/                     # 系统表 DAO
│   └── test/java/com/supermap/udbx/
│       ├── spec/                       # Spec 测试
│       └── integration/                # 集成测试
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

### 为什么选择 udbx4j 而非 iObjects Java？

| 维度 | iObjects Java | udbx4j |
|------|---------------|--------|
| **实现方式** | C++ 内核 + JNI 调用 | 纯 Java + JDBC |
| **性能** | JNI 边界开销 | **快 2-5 倍** |
| **稳定性** | JNI 内存泄漏风险 | **零泄漏** |
| **部署** | 需要原生库 + 许可 | **单个 JAR** |
| **依赖** | SuperMap 组件 | 仅 JDBC + JTS |
| **学习成本** | 复杂 API（60+ 方法） | **简洁 API** |
| **适用场景** | 桌面 GIS、复杂分析 | **数据迁移、微服务** |

**详细对比**：参见 [性能优势技术路线](docs/performance-advantage-roadmap.md)

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
- **iObjects Java API 文档**：`SuperMap iObjects Java Javadoc/`

## 许可证

[MIT License](LICENSE)

## 作者

zhyt1985

## 相关链接

- [UDBX 格式白皮书](UDBX开放数据格式白皮书(V1.0).pdf)
- [SuperMap 官方文档](https://www.supermap.com/)
- [SpatiaLite 文档](https://www.gaia-gis.it/gaia-sins/)
