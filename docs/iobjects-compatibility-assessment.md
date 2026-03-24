# iObjects Java API 兼容性评估

## 1. 需求概述

**目标**：让 udbx4j 的类和接口设计与 SuperMap iObjects Java 兼容，以便开发者可以平滑迁移。

## 2. API 对比分析

### 2.1 包结构差异

| 维度 | iObjects Java | udbx4j | 兼容性 |
|------|---------------|--------|--------|
| 包名 | `com.supermap.data` | `com.supermap.udbx` | ❌ 不兼容 |
| 数据源类 | `Datasource` | `UdbxDataSource` | ❌ 不兼容 |
| 数据集类 | `Dataset` / `DatasetVector` | `Dataset` / `VectorDataset` | ⚠️ 部分兼容 |

### 2.2 Datasource 类对比

#### iObjects Java 关键方法
```java
// 数据源打开
Datasource ds = new Datasource(EngineType.UDBX);
ds.open(connectionInfo);

// 数据集访问（通过集合）
Datasets datasets = ds.getDatasets();
Dataset dataset = datasets.get(name);

// 事务管理
ds.beginTrans();
ds.commitTrans();
ds.rollbackTrans();

// 状态查询
boolean isOpened = ds.isOpened();
boolean isConnected = ds.isConnected();
ds.close();
```

#### udbx4j 当前设计
```java
// 数据源打开（静态工厂）
UdbxDataSource ds = UdbxDataSource.open(path);

// 数据集访问（直接方法）
Dataset dataset = ds.getDataset(name);
List<DatasetInfo> infos = ds.listDatasetInfos();

// 无事务方法（使用 JDBC 事务）

// 资源管理
ds.close(); // AutoCloseable
```

### 2.3 Dataset 类对比

#### iObjects Java 关键方法
```java
// 数据集需要显式打开
dataset.open();
Recordset recordset = dataset.getRecordset();
// 使用 recordset...
recordset.dispose();
dataset.close();
```

#### udbx4j 当前设计
```java
// 数据集自动可用
List<PointFeature> features = pointDataset.getFeatures();
PointFeature feature = pointDataset.getFeature(smId);

// 写入
pointDataset.addFeature(smId, geometry, attributes);
pointDataset.updateFeature(smId, geometry, attributes);
pointDataset.deleteFeature(smId);
```

### 2.4 Recordset 类对比

#### iObjects Java
```java
Recordset rs = dataset.getRecordset();
rs.moveFirst();
rs.moveNext();
rs.edit();
rs.setGeometry(geometry);
rs.setFieldValue("name", "value");
rs.update();
rs.moveLast();
rs.dispose();
```

#### udbx4j
- **无 Recordset 类**
- 直接使用 Feature 对象（PointFeature、LineFeature 等）
- 一次性加载全部数据：`getFeatures()`

## 3. 兼容性影响评估

### 3.1 高影响差异

| 差异点 | 影响 | 迁移成本 |
|--------|------|----------|
| 包名不同 | 需要 import 替换 | 低 |
| 类名不同（UdbxDataSource vs Datasource） | 类名需修改 | 低 |
| 无 Recordset 游标模式 | 代码模式完全不同 | **高** |
| 无 Datasets 集合 | 数据集访问方式不同 | 中 |
| 无事务 API（beginTrans/commitTrans） | 事务处理需重写 | 中 |
| Dataset 无 open/close 状态 | 资源管理模式不同 | 低 |
| Geometry 对象（JTS vs SuperMap） | 几何操作 API 不同 | **高** |

### 3.2 设计理念差异

| 维度 | iObjects Java | udbx4j |
|------|---------------|--------|
| **资源管理** | 显式 open/close | AutoCloseable 自动管理 |
| **数据访问** | 游标模式（Recordset） | 集合模式（List<Feature>） |
| **连接模型** | DatasourceConnectionInfo | 直接文件路径 |
| **事务** | 数据源级别事务 | JDBC 原生事务 |
| **几何** | 自定义 Geometry | JTS (LocationTech) |

## 4. 兼容性方案建议

### 方案 A：完全兼容层（高成本，高收益）

**设计**：创建 `com.supermap.data` 兼容包，实现 iObjects Java 核心接口

```java
// 新增兼容层
com.supermap.data.Datasource (接口)
com.supermap.data.DatasetVector (抽象类)
com.supermap.data.Recordset (类)
com.supermap.data.Geometry (接口)
```

**优点**：
- ✅ 代码迁移成本最低（仅替换依赖）
- ✅ 学习曲线平缓
- ✅ 生态系统兼容

**缺点**：
- ❌ 实现复杂度极高（需要适配器模式）
- ❌ 维护成本高（需跟随 iObjects Java API 演进）
- ❌ 性能开销（多层适配）
- ❌ 与 udbx4j 简洁设计理念冲突

**实现示例**：
```java
// 适配器实现
public class UdbxDatasourceAdapter implements Datasource {
    private final UdbxDataSource delegate;

    public UdbxDatasourceAdapter(UdbxDataSource ds) {
        this.delegate = ds;
    }

    @Override
    public Datasets getDatasets() {
        return new UdbxDatasetsAdapter(delegate);
    }

    @Override
    public boolean beginTrans(...) {
        // 委托给 JDBC 事务
        return false; // 暂不支持
    }
}
```

### 方案 B：部分兼容（中成本，中收益）

**设计**：
1. 保留 udbx4j 简洁 API
2. 提供可选的兼容工具类
3. 文档提供迁移指南

```java
// 新增迁移工具类
com.supermap.udbx.migration.DatasourceCompat
com.supermap.udbx.migration.RecordsetCompat
com.supermap.udbx.migration.GeometryConverter
```

**优点**：
- ✅ 平衡简洁性与兼容性
- ✅ 实现成本可控
- ✅ 核心代码保持简单
- ✅ 渐进式迁移路径

**缺点**：
- ⚠️ 仍需修改部分代码
- ⚠️ 需要用户主动使用工具类

**实现示例**：
```java
// 迁移工具示例
public class DatasourceCompat {
    public static RecordsetCompat query(UdbxDataSource ds, String sql) {
        // 模拟 Recordset 行为
        return new RecordsetCompat(ds, sql);
    }
}

public class RecordsetCompat implements AutoCloseable {
    private final List<PointFeature> features;
    private int cursor = -1;

    public boolean moveNext() {
        cursor++;
        return cursor < features.size();
    }

    public PointFeature getCurrent() {
        return features.get(cursor);
    }
}
```

### 方案 C：完全不兼容（低成本，低收益）

**设计**：保持 udbx4j 独立设计，提供详细迁移文档

**优点**：
- ✅ 代码最简洁
- ✅ 性能最优
- ✅ 维护成本最低

**缺点**：
- ❌ 迁移成本最高
- ❌ 用户学习成本高
- ❌ 生态隔离

## 5. 关键技术难点

### 5.1 Recordset 游标模式实现

iObjects Java 的 Recordset 是游标模式，支持：
- `moveFirst()`, `moveNext()`, `moveLast()`, `moveTo()`
- `addNew()`, `edit()`, `update()`, `delete()`
- `getRecordCount()`, `getID()`

udbx4j 是集合模式：
- 一次性加载：`List<Feature>`
- 批量操作：`addFeature()`, `updateFeature()`

**解决方案**：
```java
public class RecordsetCursor<T> implements AutoCloseable {
    private final List<T> data;
    private int pos = -1;
    private T current;
    private boolean editing = false;

    public boolean moveNext() {
        if (pos + 1 < data.size()) {
            current = data.get(++pos);
            return true;
        }
        return false;
    }

    public void edit() {
        editing = true;
    }

    public void update() {
        if (!editing) throw new IllegalStateException();
        // 写入数据库
        editing = false;
    }
}
```

### 5.2 Geometry 对象兼容

| 类型 | iObjects Java | udbx4j (JTS) |
|------|---------------|--------------|
| Point | `com.supermap.data.Point` | `org.locationtech.jts.geom.Point` |
| LineString | `com.supermap.data.GeoLine` | `org.locationtech.jts.geom.LineString` |
| Polygon | `com.supermap.data.GeoRegion` | `org.locationtech.jts.geom.Polygon` |

**解决方案**：双向转换器
```java
public class GeometryConverter {
    public static Point toSuperMap(org.locationtech.jts.geom.Point jtsPoint) {
        return new Point(jtsPoint.getX(), jtsPoint.getY());
    }

    public static org.locationtech.jts.geom.Point toJTS(Point smPoint) {
        return geometryFactory.createPoint(
            new Coordinate(smPoint.getX(), smPoint.getY())
        );
    }
}
```

### 5.3 事务模型差异

iObjects Java：数据源级别事务
```java
ds.beginTrans();
dataset.getRecordset().edit();
// ... 多个操作
ds.commitTrans();
```

udbx4j：JDBC 原生事务（已实现）
```java
conn.setAutoCommit(false);
try {
    // 多个操作
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
}
```

## 6. 推荐方案

### 阶段 1：完善 udbx4j API（当前优先级）
- ✅ 补充缺失功能（批量写入、事务封装）
- ✅ 优化性能（流式读取、分页）
- ✅ 完善文档和示例

### 阶段 2：提供迁移工具（中期）
- 📝 创建 `udbx4j-migration` 模块
- 🔧 实现 Recordset 游标适配器
- 🔧 实现 Geometry 转换器
- 📚 编写迁移指南

### 阶段 3：考虑兼容层（长期，按需）
- 🤔 根据用户反馈决定是否实现
- 🤔 可作为独立模块提供
- 🤔 需权衡投入产出比

## 7. 迁移示例

### 从 iObjects Java 迁移到 udbx4j

#### 读取数据
```java
// iObjects Java
Datasource ds = new Datasource(EngineType.UDBX);
ds.open(connInfo);
DatasetVector dataset = (DatasetVector) ds.getDatasets().get("points");
Recordset rs = dataset.getRecordset();
while (rs.moveNext()) {
    Point p = rs.getGeometry();
    System.out.println(p.getX() + "," + p.getY());
}
rs.dispose();
ds.close();

// udbx4j（推荐）
try (UdbxDataSource ds = UdbxDataSource.open("data.udbx")) {
    PointDataset dataset = (PointDataset) ds.getDataset("points");
    for (PointFeature f : dataset.getFeatures()) {
        System.out.println(f.geometry().getX() + "," + f.geometry().getY());
    }
}

// udbx4j（使用迁移工具，兼容 Recordset 模式）
try (UdbxDataSource ds = UdbxDataSource.open("data.udbx")) {
    PointDataset dataset = (PointDataset) ds.getDataset("points");
    try (RecordsetCursor<PointFeature> rs = RecordsetCompat.cursor(dataset)) {
        while (rs.moveNext()) {
            PointFeature f = rs.getCurrent();
            System.out.println(f.geometry().getX() + "," + f.geometry().getY());
        }
    }
}
```

#### 写入数据
```java
// iObjects Java
ds.beginTrans();
Recordset rs = dataset.getRecordset();
rs.addNew();
rs.setGeometry(new Point(116, 39));
rs.setFieldValue("name", "Beijing");
rs.update();
ds.commitTrans();

// udbx4j
PointDataset dataset = ds.createPointDataset("points", 4326);
dataset.addFeature(1, geometryFactory.createPoint(new Coordinate(116, 39)),
    Map.of("name", "Beijing"));
```

## 8. 总结

| 方案 | 开发成本 | 维护成本 | 迁移成本 | 性能影响 | 推荐度 |
|------|----------|----------|----------|----------|--------|
| A - 完全兼容 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ | ⭐⭐ |
| B - 部分兼容 | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| C - 不兼容 | ⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ |

**最终建议**：
1. **短期**：专注完善 udbx4j 独特价值（简洁、轻量、高性能）
2. **中期**：实现方案 B（迁移工具模块）
3. **长期**：根据用户反馈决定是否实现方案 A

**关键理由**：
- udbx4j 的核心竞争力是简洁轻量，不应牺牲这一优势
- 完全兼容层实现成本高，且难以维护
- 提供迁移工具可以平衡兼容性与简洁性
- 文档和示例是降低迁移成本的有效手段
