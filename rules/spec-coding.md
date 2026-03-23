# Spec-Coding 开发规范

本项目采用 **Spec-Coding** 方法：测试直接从格式规范（白皮书）推导，每一行测试代码都能追溯到白皮书的具体章节和字段定义。

---

## 核心原则

> **规范即测试，测试即文档。**
>
> Spec 测试不只是验证代码正确性，它们是白皮书的可执行版本。

---

## 强制开发流程

每个功能单元**严格**按以下 7 步执行，不可跳步：

```
Step 1. 阅读白皮书  → 定位对应章节，记录精确字节结构
Step 2. 写 Spec 测试 → 用硬编码字节序列或已知数据断言（必须能编译但会失败）
Step 3. 运行测试    → 确认 RED（测试失败，说明测试有效）
Step 4. 实现代码    → 最小化实现，使测试通过
Step 5. 运行测试    → 确认 GREEN
Step 6. 集成验证    → 用 SampleData.udbx 运行集成测试
Step 7. 重构        → 在保持 GREEN 的前提下改善代码质量
```

**绝对禁止：**
- 跳过 Step 3（不确认 RED 就写实现）
- 先写实现再补测试
- 写"空"测试（无断言或只有 `assertTrue(true)`）

---

## Spec 测试的构造方式

### 方式一：手动构造字节序列（二进制解码测试）

基于白皮书的二进制结构定义，手动组装字节数组：

```java
/**
 * 测试依据：白皮书 4.2.1 GAIAPoint 结构：
 *
 *   byte gaiaStart = 0x00;
 *   byte byteOrdering = 1;       // Little-Endian
 *   int32 srid;
 *   Rect mbr;                    // 4 × double = 32 bytes
 *   byte gaiaMBR = 0x7c;
 *   int32 geoType = 1;
 *   double x;
 *   double y;
 *   byte gaiaEnd = 0xFE;
 */
@Test
void should_decode_gaia_point_per_spec_4_2_1() {
    // 已知输入：x=116.3912, y=39.9075, srid=4326
    ByteBuffer buf = ByteBuffer.allocate(60).order(LITTLE_ENDIAN);
    buf.put((byte) 0x00);          // gaiaStart
    buf.put((byte) 0x01);          // byteOrdering = Little-Endian
    buf.putInt(4326);              // srid
    buf.putDouble(116.3912);       // mbr.left
    buf.putDouble(39.9075);        // mbr.bottom
    buf.putDouble(116.3912);       // mbr.right
    buf.putDouble(39.9075);        // mbr.top
    buf.put((byte) 0x7c);          // gaiaMBR
    buf.putInt(1);                 // geoType = GAIAPoint
    buf.putDouble(116.3912);       // x
    buf.putDouble(39.9075);        // y
    buf.put((byte) 0xFE);          // gaiaEnd

    Point point = GaiaGeometryReader.readPoint(buf.array());

    assertThat(point.getX()).isCloseTo(116.3912, within(1e-10));
    assertThat(point.getY()).isCloseTo(39.9075, within(1e-10));
    assertThat(point.getSRID()).isEqualTo(4326);
}
```

### 方式二：从 SampleData.udbx 取已知值（系统表/集成测试）

基于探查 SampleData.udbx 得到的已知数据作为 oracle：

```java
/**
 * 测试依据：白皮书表 7 SmRegister 字段定义。
 * 已知数据（查询 SampleData.udbx 得到）：
 *   BaseMap_P: SmDatasetType=1, SmObjectCount=15, SmSRID=4326
 */
@Test
void should_read_BaseMap_P_metadata_from_SmRegister() {
    try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
        DatasetInfo info = ds.getDatasetInfo("BaseMap_P");

        assertThat(info.datasetType()).isEqualTo(DatasetType.Point);
        assertThat(info.objectCount()).isEqualTo(15);
        assertThat(info.srid()).isEqualTo(4326);
    }
}
```

### 方式三：往返测试（编码器 Spec）

```java
/**
 * 往返测试确保编解码互逆。
 * 白皮书规范保证：decode(encode(data)) == data
 */
@Test
void roundtrip_gaia_point_should_preserve_all_fields() {
    Point original = GF.createPoint(new Coordinate(116.3912, 39.9075));

    byte[] encoded = GaiaGeometryWriter.writePoint(original, 4326);
    Point decoded  = GaiaGeometryReader.readPoint(encoded);

    assertThat(decoded.getX()).isCloseTo(original.getX(), within(1e-10));
    assertThat(decoded.getY()).isCloseTo(original.getY(), within(1e-10));
    assertThat(decoded.getSRID()).isEqualTo(4326);
}
```

---

## Spec 测试的注释规范

每个 Spec 测试类必须有类级 Javadoc，说明：
1. 对应白皮书章节编号
2. 测试覆盖的二进制结构名称

```java
/**
 * Spec 测试：验证 GAIAPoint 二进制格式。
 *
 * <p>对应白皮书章节：4.2.1 GAIAPoint
 *
 * <p>二进制结构：
 * <pre>
 * GAIAPoint {
 *     static byte  gaiaStart = 0x00;
 *     GAIAInfo     info;              // byteOrder + srid + MBR + 0x7c
 *     static int32 geoType = 1;
 *     Point        geoPnt;            // double x; double y;
 *     static byte  gaiaEnd = 0xFE;
 * }
 * </pre>
 */
class GaiaPointSpecTest { ... }
```

---

## 开发阶段与对应白皮书章节

| 阶段 | 功能 | 白皮书章节 |
|------|------|-----------|
| 1 | 枚举类型 | 表 1, 2, 9 |
| 2 | 系统表读取 | §2.1, §2.2（表 3-16）|
| 3 | GAIA 解码 | §4.2（4.2.1-4.2.7）|
| 4 | GAIA 编码 | §4.2（往返验证）|
| 5 | 矢量数据集读取 | §3.1.2-3.1.4 |
| 6 | CAD 几何解码 | §4.3（4.3.1-4.3.15）|
| 7 | CAD 编码+数据集读写 | §4.3, §3.1.6 |
| 8 | 数据集写入 | §2.2, §3.1 |

---

## 禁止行为

| 禁止 | 后果 |
|------|------|
| 先写实现再补测试 | 失去 RED 验证，测试可能永远不会失败 |
| 测试中无断言 | 测试无意义，永远 GREEN |
| 用随机数据代替白皮书已知值 | 无法验证格式规范符合性 |
| 修改测试以通过 | 应修改实现，不是测试（除非测试本身有误） |
| 跳过集成测试 | 可能通过 Spec 测试但与真实文件不兼容 |
