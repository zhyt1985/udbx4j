# Java 编码风格

> 本文扩展 [common/coding-style.md](../../../.claude/rules/common/coding-style.md)，添加 Java 17 特定规范。

---

## 命名规范

| 元素 | 规范 | 示例 |
|------|------|------|
| 类/接口/枚举 | UpperCamelCase | `GaiaGeometryReader` |
| 方法/变量 | lowerCamelCase | `readPoint`, `numPoints` |
| 常量/枚举值 | UPPER_SNAKE_CASE | `GAIA_START_BYTE`, `GAIAPoint` |
| 包名 | 全小写，无下划线 | `com.supermap.udbx.geometry.gaia` |
| 测试类 | `被测类名 + SpecTest / Test` | `GaiaPointSpecTest` |
| 测试方法 | `should_动词_条件` 或 `动词_what_when` | `should_decode_point_with_correct_coordinates` |

---

## Java 17 特性使用规范

### Record（优先用于不可变数据载体）

```java
// CORRECT: 元信息 POJO 用 Record
public record DatasetInfo(
    int datasetId,
    String datasetName,
    String tableName,
    DatasetType datasetType,
    int objectCount,
    double left, double right, double top, double bottom,
    int srid
) {}

// WRONG: 用传统 class + getter/setter
public class DatasetInfo {
    private int datasetId;
    // ...setters...
}
```

**适用范围：** DatasetInfo, FieldInfo, 所有从系统表读取的元信息

### Sealed Classes（用于多态几何对象）

```java
// CAD 几何对象层次结构
public sealed interface CadGeometry
    permits GeoPoint, GeoLine, GeoRegion, GeoRect, GeoCircle,
            GeoEllipse, GeoPie, GeoArc, GeoEllipticArc,
            GeoCurve, GeoBSpline, GeoCardinal {}

public record GeoPoint(GeoHeader header, double x, double y) implements CadGeometry {}
public record GeoCircle(GeoHeader header, double cx, double cy, double radius) implements CadGeometry {}
```

**好处：** 编译器强制穷举 switch，避免遗漏类型处理

### Switch 表达式（替代 switch 语句）

```java
// CORRECT: switch 表达式
CadGeometry geometry = switch (geoType) {
    case 1  -> readGeoPoint(buf, header);
    case 3  -> readGeoLine(buf, header);
    case 5  -> readGeoRegion(buf, header);
    case 12 -> readGeoRect(buf, header);
    case 15 -> readGeoCircle(buf, header);
    default -> throw new UdbxFormatException("Unknown CAD geo type: " + geoType);
};

// WRONG: 传统 switch + return
switch (geoType) {
    case 1: return readGeoPoint(buf, header);
    // ...
}
```

### Text Blocks（用于 SQL）

```java
// CORRECT: Text Block 写 SQL
private static final String FIND_BY_DATASET_ID = """
    SELECT SmID, SmFieldName, SmFieldCaption, SmFieldType,
           SmFieldSign, SmFieldSize, SmFieldDefaultValue
    FROM   SmFieldInfo
    WHERE  SmDatasetID = ?
    ORDER  BY SmID
    """;

// WRONG: 字符串拼接
String sql = "SELECT SmID, SmFieldName " +
             "FROM SmFieldInfo " +
             "WHERE SmDatasetID = ?";
```

---

## 不可变性（CRITICAL）

```java
// CORRECT: 解码器返回新对象，不修改输入
public static Point decodePoint(byte[] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN);
    // 只读 buf，返回新的 Point
    return geometryFactory.createPoint(new Coordinate(x, y));
}

// WRONG: 修改传入的 ByteBuffer position 并作为副作用
public void decodePoint(ByteBuffer buf, Point result) {
    result.x = buf.getDouble(); // 修改入参
}
```

---

## 文件大小限制

- 单文件最多 **600 行**（Java 比通用规范宽松，因为有 import/注释）
- 超出时拆分：将 Reader/Writer 逻辑分离，不同 Geometry 类型放独立类

---

## 异常处理

```java
// 定义项目专属异常（非检查异常）
public class UdbxException extends RuntimeException {
    public UdbxException(String message) { super(message); }
    public UdbxException(String message, Throwable cause) { super(message, cause); }
}

public class UdbxFormatException extends UdbxException {
    // 二进制格式违反规范时抛出
}

public class UdbxIOException extends UdbxException {
    // SQLite / 文件 IO 错误时抛出
}
```

**规则：**
- 永远不要捕获并忽略异常（`catch (Exception e) {}`）
- SQLite 异常包装为 `UdbxIOException` 后重新抛出
- 格式验证失败（如 GAIA magic byte 不符）抛出 `UdbxFormatException`

---

## 资源管理

```java
// CORRECT: DataSource 实现 AutoCloseable，用 try-with-resources
try (UdbxDataSource ds = UdbxDataSource.open(path)) {
    Dataset dataset = ds.getDataset("BaseMap_P");
    // ...
} // 自动关闭 SQLite connection

// Dataset 也实现 AutoCloseable
try (PointDataset points = (PointDataset) ds.getDataset("BaseMap_P")) {
    // ...
}
```

---

## 二进制 I/O 规范

```java
// 所有 SuperMap 二进制数据使用 LITTLE_ENDIAN
ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

// 封装常用操作（在 BinaryReader 工具类中）
public static String readString(ByteBuffer buf) {
    int length = buf.getInt();          // int32: 字节长度
    byte[] bytes = new byte[length];
    buf.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
}

public static Color readColor(ByteBuffer buf) {
    // 白皮书 4.1.10: 顺序为 a, b, g, r（ABGR）
    int a = buf.get() & 0xFF;
    int b = buf.get() & 0xFF;
    int g = buf.get() & 0xFF;
    int r = buf.get() & 0xFF;
    return new Color(r, g, b, a);
}
```

---

## 代码质量检查清单

完成每个功能单元前确认：

- [ ] 所有 `byte` 转 `int` 时用 `& 0xFF` 避免符号扩展
- [ ] ByteBuffer 明确设置了 `LITTLE_ENDIAN`
- [ ] Record 用于不可变数据载体
- [ ] Switch 表达式覆盖所有枚举值（含 `default` 兜底）
- [ ] AutoCloseable 资源都在 try-with-resources 中使用
- [ ] SQL 用 PreparedStatement（不拼接字符串）
- [ ] 测试方法名清晰描述测试的行为和期望
