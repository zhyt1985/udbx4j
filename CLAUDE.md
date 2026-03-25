# udbx4j — CLAUDE.md

## 项目简介

`udbx4j` 是超图 UDBX 空间数据格式的 Java 读写库。UDBX（Universal Spatial Database Extension）基于 SQLite 存储，支持矢量（点/线/面/CAD）和栅格空间数据。

**当前版本**: v1.0.0（已发布到 Maven Central）
**Maven 坐标**: `io.github.zhyt1985:udbx4j:1.0.0`
**发布日期**: 2025-03-25

**白皮书：** `UDBX开放数据格式白皮书(V1.0).pdf`（项目根目录）
**测试数据：** `src/test/resources/SampleData.udbx`（19MB 示例文件）
**iObjects Java API 文档：** `SuperMap iObjects Java Javadoc/`（离线 JavaDoc，5,500+ 页面）

---

## 发布状态

### v1.0.0（当前版本）

**发布日期**: 2025-03-25
**Maven Central**: ✅ 已发布
**GitHub Release**: https://github.com/zhyt1985/udbx4j/releases/tag/v1.0.0

**使用方式**:
```xml
<dependency>
    <groupId>io.github.zhyt1985</groupId>
    <artifactId>udbx4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

**主要特性**:
- ✅ 完整的数据集支持（Point, Line, Region, CAD, Tabular）
- ✅ 高性能（92.5% 单线程提升，3.49x 并发扩展）
- ✅ 流式处理（支持大数据集的流式读取和分页查询）
- ✅ 批量写入（10-50x 性能提升）
- ✅ 连接池（基于 HikariCP）
- ✅ 315 个测试全部通过，80%+ 覆盖率

**性能测试报告**: 详见 `docs/performance-roadmap/` 目录

---

## 参考文档

### SuperMap iObjects Java API 离线文档

项目根目录包含完整的 iObjects Java 离线 API 文档，便于开发时查阅 SuperMap 原生类的定义：

```
SuperMap iObjects Java Javadoc/
├── index.html              # 入口页面
├── allclasses-frame.html   # 所有类列表
├── index-all.html          # 全索引（方法/字段检索）
└── com/supermap/
    ├── data/               # 核心数据包（Dataset, Datasource, Recordset...）
    ├── data/conversion/    # 数据转换
    ├── data/topology/      # 拓扑分析
    ├── analyst/            # 分析模块
    │   ├── spatialanalyst/   # 空间分析
    │   ├── networkanalyst/   # 网络分析
    │   └── terrainanalyst/   # 地形分析
    ├── mapping/            # 制图与地图
    ├── realspace/          # 三维场景
    ├── layout/             # 布局排版
    ├── image/              # 影像处理
    ├── ui/                 # UI 组件
    └── chart/              # 海图
```

**使用方式：**
```bash
# 方式1：浏览器直接打开
cd "SuperMap iObjects Java Javadoc"
python3 -m http.server 8080
# 访问 http://localhost:8080

# 方式2：搜索特定类
grep -r "class Dataset" "SuperMap iObjects Java Javadoc/com/supermap/data/"

# 方式3：查看特定类
open "SuperMap iObjects Java Javadoc/com/supermap/data/Dataset.html"
```

**与 udbx4j 的关系：**
- `com.supermap.data` 包是 udbx4j 对接的核心 API
- 重点关注：Dataset、Datasource、Recordset、Geometry、PrjCoordSys 等类
- udbx4j 实现了 UDBX 格式的独立读写，API 设计参考 iObjects Java 的语义

---

## 构建与测试命令

> **注意：** 系统需要 Java 17。若系统默认 JDK 不是 17，使用 `make` 命令（已预设 JAVA_HOME）或手动指定：
> ```bash
> export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
> ```

```bash
# 推荐：使用 Makefile（自动处理 JAVA_HOME）
make test            # 运行 Spec 测试
make test-all        # 运行所有测试 + 覆盖率报告
make run-test CLASS=GaiaPointSpecTest  # 运行单个测试类
make coverage        # 打开覆盖率报告

# 直接使用 Maven（需先设置 JAVA_HOME）
mvn compile
mvn test
mvn test -Dtest=GaiaPointSpecTest
mvn verify           # 含集成测试 + 覆盖率检查
mvn package -DskipTests
mvn clean
```

---

## 项目结构

```
udbx4j/
├── CLAUDE.md                          # 本文件
├── pom.xml                            # Maven 构建配置
├── rules/                             # 开发规范（本项目专属）
│   ├── java-coding-style.md
│   ├── java-testing.md
│   ├── java-patterns.md
│   └── spec-coding.md
└── src/
    ├── main/java/com/supermap/udbx/
    │   ├── UdbxDataSource.java         # 入口类：open()/create()
    │   ├── core/                       # 枚举与元信息 POJO
    │   │   ├── DatasetType.java        # Enum: Tabular=0, Point=1, Line=3...
    │   │   ├── GeometryType.java       # Enum: GAIAPoint=1, GeoLine=3...
    │   │   ├── FieldType.java          # Enum: SuperMap 字段类型
    │   │   ├── FieldInfo.java          # 字段元信息
    │   │   └── DatasetInfo.java        # 数据集元信息（来自 SmRegister）
    │   ├── dataset/                    # 数据集实现
    │   │   ├── Dataset.java            # 抽象基类（AutoCloseable）
    │   │   ├── TabularDataset.java
    │   │   ├── VectorDataset.java      # 矢量数据集基类
    │   │   ├── PointDataset.java
    │   │   ├── LineDataset.java
    │   │   ├── RegionDataset.java
    │   │   └── CadDataset.java
    │   ├── geometry/
    │   │   ├── gaia/                   # SpatiaLite 二进制格式（点/线/面）
    │   │   │   ├── GaiaGeometryReader.java
    │   │   │   └── GaiaGeometryWriter.java
    │   │   └── cad/                    # SuperMap 自定义二进制格式（CAD）
    │   │       ├── CadGeometryReader.java
    │   │       ├── CadGeometryWriter.java
    │   │       ├── CadGeometry.java
    │   │       ├── Style.java
    │   │       ├── StyleMarker.java
    │   │       ├── StyleLine.java
    │   │       └── StyleFill.java
    │   └── system/                     # 系统表 DAO
    │       ├── SmRegisterDao.java
    │       ├── SmFieldInfoDao.java
    │       ├── SmDataSourceInfoDao.java
    │       └── GeometryColumnsDao.java
    └── test/java/com/supermap/udbx/
        ├── spec/                       # Spec 测试（白皮书驱动）
        └── integration/               # 集成测试（SampleData.udbx 验证）
```

---

## 架构核心概念

### UDBX 格式要点

| 概念 | 说明 |
|------|------|
| 文件 | SQLite 数据库，`.udbx` 后缀 |
| 字节序 | **Little-Endian**（所有二进制数据） |
| GAIA Geometry | SpatiaLite 标准格式，用于 Point/Line/Region |
| GeoHeader Geometry | SuperMap 自定义格式，用于 CAD 数据集 |
| SmRegister | 矢量数据集注册表（类型、范围、对象数等） |
| SmFieldInfo | 字段元信息（别名、SuperMap 字段类型） |

### Geometry 二进制格式

**GAIA（SpatiaLite）：**
```
0x00 | byteOrder(1) | srid(int32) | MBR(4×double) | 0x7c | geoType(int32) | ...coords... | 0xFE
```

**CAD（SuperMap GeoHeader）：**
```
geoType(int32) | styleSize(int32) | Style(...) | ...geometry data...
```

### 数据集类型枚举

| DatasetType | 枚举值 | Geometry 格式 |
|-------------|--------|---------------|
| Tabular | 0 | 无 |
| Point | 1 | GAIAPoint (geoType=1) |
| Line | 3 | GAIAMultiLineString (geoType=5) |
| Region | 5 | GAIAMultiPolygon (geoType=6) |
| PointZ | 101 | GAIAPointZ (geoType=1001) |
| LineZ | 103 | GAIAMultiLineStringZ (geoType=1005) |
| RegionZ | 105 | GAIAMultiPolygonZ (geoType=1006) |
| CAD | 149 | GeoHeader + 具体类型 |

---

## 开发规范速查

详细规范见 `rules/` 目录，关键点：

1. **Spec-Coding 强制流程**：见 `rules/spec-coding.md`
   - 先写测试（必须 RED），再写实现（GREEN），再重构
   - Spec 测试基于白皮书二进制规范，使用硬编码字节序列作为输入
   - 集成测试基于 `SampleData.udbx` 中的已知数据验证

2. **不可变优先**：见 `rules/java-coding-style.md`
   - FieldInfo、DatasetInfo 等元信息类使用 Java Record（Java 16+）
   - 解码结果返回新对象，不修改输入字节数组

3. **测试覆盖率 ≥ 80%**：见 `rules/java-testing.md`

4. **Commit 格式**：`feat/fix/test/refactor/docs/chore: 描述`

---

## 关键实现陷阱

| 陷阱 | 正确做法 |
|------|---------|
| Color 字段字节序 | ABGR 顺序（a, b, g, r），不是 RGBA |
| SuperMap String | `int32(字节长度)` + UTF-8 bytes，不是 null-terminated |
| CAD StyleSize=0 | styleSize=0 时**不读取** Style 字节（GeoText 在文本数据集中） |
| ByteBuffer 字节序 | 必须显式设置 `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` |
| SmObjectCount | 写入/删除记录后必须同步更新 `SmRegister.SmObjectCount` |
| SmMaxGeometrySize | 写入后更新为该数据集最大 Geometry BLOB 字节数 |
| SQLite 事务 | 批量写操作必须包裹在事务中 |

---

## 发布与维护

### 发布流程

项目使用 Maven Central 进行发布：

**发布坐标**:
- Group ID: `io.github.zhyt1985`
- Artifact ID: `udbx4j`
- Version: `1.0.0`（当前）

**发布命令**:
```bash
# 1. 更新版本号（pom.xml）
# 2. 运行完整测试
mvn clean verify

# 3. 部署到 Maven Central
mvn clean deploy

# 4. 创建 Git 标签
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# 5. 创建 GitHub Release
gh release create v1.0.0
```

**Maven Central 管理**:
- Portal: https://central.sonatype.com/
- Namespace: `io.github.zhyt1985`
- 自动发布已启用（autoPublish=true）

### 版本策略

遵循语义化版本（Semantic Versioning）：
- `MAJOR.MINOR.PATCH`（如 1.0.0）
- MAJOR: 破坏性变更
- MINOR: 新功能（向后兼容）
- PATCH: Bug 修复

### 性能优化历史

**v1.0.0 性能提升**:
- Phase 0: 建立性能基线（4.516ms）
- Phase 1: 基础优化（92.5% 提升，0.34ms）
- Phase 2: 流式优化（分页查询 +22% 内存）
- Phase 3: 并发优化（3.49x 扩展性）

详见 `docs/performance-roadmap/` 目录。

### 贡献指南

欢迎贡献代码！详见 `CONTRIBUTING.md`。

**基本流程**:
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

**测试要求**:
- 所有现有测试必须通过
- 新功能必须包含 Spec 测试和集成测试
- 测试覆盖率必须 ≥ 80%

### 获取帮助

- **Issues**: https://github.com/zhyt1985/udbx4j/issues
- **Discussions**: https://github.com/zhyt1985/udbx4j/discussions
- **Wiki**: https://github.com/zhyt1985/udbx4j/wiki

---

**项目状态**: 🎉 **v1.0.0 已发布，生产就绪**
**最后更新**: 2025-03-25
**维护者**: zhyt1985
