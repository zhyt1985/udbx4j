# Java 设计模式规范

> 本文扩展 [common/patterns.md](../../../.claude/rules/common/patterns.md)，定义 udbx4j 中使用的 Java 模式。

---

## 数据访问对象（DAO）模式

系统表读写通过 DAO 封装 SQL，业务层不直接操作 JDBC。

```java
// 接口定义（便于测试 Mock）
public interface SmRegisterDao {
    List<DatasetInfo> findAll();
    Optional<DatasetInfo> findById(int datasetId);
    void insert(DatasetInfo info);
    void updateObjectCount(int datasetId, int count);
}

// 实现类：注入 Connection
public class SmRegisterDaoImpl implements SmRegisterDao {

    private final Connection connection;

    public SmRegisterDaoImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<DatasetInfo> findById(int datasetId) {
        final String sql = """
            SELECT * FROM SmRegister WHERE SmDatasetID = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new UdbxIOException("Failed to query SmRegister", e);
        }
    }

    private DatasetInfo mapRow(ResultSet rs) throws SQLException {
        return new DatasetInfo(
            rs.getInt("SmDatasetID"),
            rs.getString("SmDatasetName"),
            // ...
        );
    }
}
```

---

## Factory 模式（数据集创建）

```java
// 根据 DatasetType 枚举创建对应 Dataset 实例
public final class DatasetFactory {

    private DatasetFactory() {} // 工具类，禁止实例化

    public static Dataset create(DatasetInfo info, Connection connection) {
        return switch (info.datasetType()) {
            case Tabular  -> new TabularDataset(info, connection);
            case Point,
                 PointZ   -> new PointDataset(info, connection);
            case Line,
                 LineZ    -> new LineDataset(info, connection);
            case Region,
                 RegionZ  -> new RegionDataset(info, connection);
            case CAD      -> new CadDataset(info, connection);
            default -> throw new UdbxException(
                "Unsupported dataset type: " + info.datasetType());
        };
    }
}
```

---

## Builder 模式（复杂写操作）

```java
// 创建新数据集时，通过 Builder 配置参数
PointDataset dataset = ds.createDataset(
    DatasetCreateOptions.forPoint("MyPoints")
        .srid(4326)
        .description("测试点数据集")
        .build()
);
```

---

## Visitor / Pattern Matching（几何类型分发）

Java 17 的 sealed interface + switch 表达式替代传统 Visitor：

```java
// CAD 几何对象编码：switch on sealed type
public byte[] encode(CadGeometry geometry) {
    return switch (geometry) {
        case GeoPoint p      -> encodeGeoPoint(p);
        case GeoLine l       -> encodeGeoLine(l);
        case GeoRegion r     -> encodeGeoRegion(r);
        case GeoRect r       -> encodeGeoRect(r);
        case GeoCircle c     -> encodeGeoCircle(c);
        case GeoEllipse e    -> encodeGeoEllipse(e);
        case GeoPie pie      -> encodeGeoPie(pie);
        case GeoArc arc      -> encodeGeoArc(arc);
        // ... 编译器强制枚举所有 case
    };
}
```

---

## 二进制读写工具类

```java
// BinaryReader: 封装 ByteBuffer 常用操作（静态工具类）
public final class BinaryReader {

    private BinaryReader() {}

    public static ByteBuffer wrap(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** 读取 SuperMap String: int32(字节数) + UTF-8 bytes */
    public static String readString(ByteBuffer buf) {
        int len = buf.getInt();
        if (len < 0) throw new UdbxFormatException("Negative string length: " + len);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 读取 Color: ABGR 字节顺序（白皮书 4.1.10）*/
    public static int readColorArgb(ByteBuffer buf) {
        int a = buf.get() & 0xFF;
        int b = buf.get() & 0xFF;
        int g = buf.get() & 0xFF;
        int r = buf.get() & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b; // Java ARGB
    }

    /** 读取 Point（2D）*/
    public static double[] readPoint2D(ByteBuffer buf) {
        return new double[]{buf.getDouble(), buf.getDouble()};
    }

    /** 读取 PointZ（3D）*/
    public static double[] readPoint3D(ByteBuffer buf) {
        return new double[]{buf.getDouble(), buf.getDouble(), buf.getDouble()};
    }
}

// BinaryWriter: 封装写入操作
public final class BinaryWriter {

    private BinaryWriter() {}

    /** 写入 SuperMap String */
    public static void writeString(ByteBuffer buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    /** 写入 Color（ABGR 顺序）*/
    public static void writeColor(ByteBuffer buf, int argb) {
        buf.put((byte) ((argb >> 24) & 0xFF)); // a
        buf.put((byte) (argb & 0xFF));          // b
        buf.put((byte) ((argb >> 8) & 0xFF));   // g
        buf.put((byte) ((argb >> 16) & 0xFF));  // r
    }
}
```

---

## 事务管理

```java
// 写操作必须包裹在事务中
public void addFeatures(List<Feature> features) {
    try {
        connection.setAutoCommit(false);
        for (Feature f : features) {
            insertFeature(f);
        }
        connection.commit();
    } catch (SQLException e) {
        try { connection.rollback(); } catch (SQLException ignored) {}
        throw new UdbxIOException("Failed to insert features", e);
    } finally {
        try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
    }
}
```

---

## 禁止使用的模式

| 禁止 | 原因 | 替代 |
|------|------|------|
| `Statement`（拼接 SQL） | SQL 注入风险 | `PreparedStatement` |
| `e.printStackTrace()` | 日志不可控 | 包装为 `UdbxException` 重新抛出 |
| 可变的 public 字段 | 破坏封装 | Record / getter |
| `null` 作为返回值 | NullPointerException 风险 | `Optional<T>` |
| `instanceof` + 强转 | 类型不安全 | sealed interface + switch |
