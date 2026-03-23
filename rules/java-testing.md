# Java 测试规范

> 本文扩展 [common/testing.md](../../../.claude/rules/common/testing.md)，添加 Java 17 + JUnit 5 + AssertJ 具体规范。

---

## 测试框架栈

| 框架 | 版本 | 用途 |
|------|------|------|
| JUnit 5 (Jupiter) | 5.10.x | 测试框架 |
| AssertJ | 3.25.x | 流式断言（替代 JUnit 原生 assert） |
| JaCoCo | 0.8.x | 覆盖率统计 |

---

## 测试分类与目录

```
src/test/java/com/supermap/udbx/
├── spec/          # Spec 测试：基于白皮书二进制规范
│   ├── GaiaPointSpecTest.java
│   ├── GaiaLineSpecTest.java
│   └── ...
└── integration/   # 集成测试：基于 SampleData.udbx 已知数据
    ├── DataSourceReadTest.java
    └── ...
```

**Spec 测试：**
- 输入：手动构造符合白皮书规范的字节数组（硬编码）
- 断言：解码结果的每个字段精确匹配
- 无外部依赖（无文件 IO，无数据库）

**集成测试：**
- 输入：`src/test/resources/SampleData.udbx`
- 断言：从已知数据集中读取，验证对象数、字段值、坐标
- 使用 `@Tag("integration")` 标注

---

## 测试命名规范

```java
// 格式：should_[期望行为]_when_[条件]
// 或：[动词]_[被测对象]_[期望结果]

@Test
void should_decode_gaia_point_with_correct_xy_coordinates() { ... }

@Test
void should_throw_format_exception_when_start_byte_is_not_0x00() { ... }

@Test
void should_read_15_features_from_BaseMap_P() { ... }
```

---

## AssertJ 使用规范

```java
// 数值精度比较（浮点坐标）
assertThat(point.getX()).isCloseTo(116.3912, within(1e-9));
assertThat(point.getY()).isCloseTo(39.9075, within(1e-9));

// 集合断言
assertThat(features).hasSize(15);
assertThat(features).isNotEmpty();
assertThat(features).extracting(Feature::getSmId).containsExactly(1, 2, 3);

// 类型断言
assertThat(geometry).isInstanceOf(Point.class);

// 异常断言
assertThatThrownBy(() -> GaiaGeometryReader.readPoint(invalidBytes))
    .isInstanceOf(UdbxFormatException.class)
    .hasMessageContaining("Invalid GAIA start byte");

// 字节数组断言（二进制规范验证）
assertThat(encoded[0]).isEqualTo((byte) 0x00);  // GAIA start
assertThat(encoded[encoded.length - 1]).isEqualTo((byte) 0xFE);  // GAIA end
```

---

## Spec 测试模板

```java
/**
 * 验证 GAIAPoint 二进制格式符合白皮书 4.2.1 节规范。
 */
class GaiaPointSpecTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    // ---- 解码测试 ----

    @Test
    void should_decode_2d_point_matching_spec_section_4_2_1() {
        // 构造符合白皮书 4.2.1 的字节序列
        // 结构: 0x00 | byteOrder(1B) | srid(4B) | MBR(32B) | 0x7c(1B) | geoType=1(4B) | x(8B) | y(8B) | 0xFE(1B)
        byte[] bytes = GaiaTestHelper.buildPointBytes(116.3912, 39.9075, 4326);

        Point point = GaiaGeometryReader.readPoint(bytes);

        assertThat(point.getX()).isCloseTo(116.3912, within(1e-10));
        assertThat(point.getY()).isCloseTo(39.9075, within(1e-10));
        assertThat(point.getSRID()).isEqualTo(4326);
    }

    @Test
    void should_throw_format_exception_when_start_byte_is_invalid() {
        byte[] bytes = GaiaTestHelper.buildPointBytes(0, 0, 0);
        bytes[0] = 0x01; // 破坏 start byte

        assertThatThrownBy(() -> GaiaGeometryReader.readPoint(bytes))
            .isInstanceOf(UdbxFormatException.class);
    }

    // ---- 编码测试 ----

    @Test
    void should_encode_point_with_correct_start_and_end_bytes() {
        Point point = GF.createPoint(new Coordinate(116.3912, 39.9075));

        byte[] encoded = GaiaGeometryWriter.writePoint(point, 4326);

        assertThat(encoded[0]).isEqualTo((byte) 0x00);
        assertThat(encoded[encoded.length - 1]).isEqualTo((byte) 0xFE);
    }

    @Test
    void roundtrip_encode_decode_should_preserve_coordinates() {
        Point original = GF.createPoint(new Coordinate(116.3912, 39.9075));

        byte[] encoded = GaiaGeometryWriter.writePoint(original, 4326);
        Point decoded = GaiaGeometryReader.readPoint(encoded);

        assertThat(decoded.getX()).isCloseTo(original.getX(), within(1e-10));
        assertThat(decoded.getY()).isCloseTo(original.getY(), within(1e-10));
    }
}
```

---

## 集成测试模板

```java
/**
 * 集成测试：验证从真实 SampleData.udbx 读取数据正确。
 */
@Tag("integration")
class PointDatasetReadTest {

    private static final String SAMPLE_DB =
        "src/test/resources/SampleData.udbx";

    @Test
    void should_read_object_count_of_BaseMap_P() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            PointDataset dataset = (PointDataset) ds.getDataset("BaseMap_P");

            assertThat(dataset.getObjectCount()).isEqualTo(15);
        }
    }

    @Test
    void should_read_srid_of_BaseMap_P() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            DatasetInfo info = ds.getDatasetInfo("BaseMap_P");

            assertThat(info.srid()).isEqualTo(4326);
        }
    }
}
```

---

## 测试辅助工具规范

```
src/test/java/com/supermap/udbx/testutil/
├── GaiaTestHelper.java        # 构造 GAIA 二进制字节序列
├── CadTestHelper.java         # 构造 CAD GeoHeader 字节序列
└── TempUdbxFile.java          # JUnit 5 Extension，创建临时 .udbx 文件
```

```java
// TempUdbxFile 使用示例
@ExtendWith(TempUdbxFile.class)
class DatasetWriteTest {

    @TempUdbx
    Path tempFile;

    @Test
    void should_write_and_read_point_dataset() {
        try (UdbxDataSource ds = UdbxDataSource.create(tempFile)) {
            // ...写入数据...
        }
        try (UdbxDataSource ds = UdbxDataSource.open(tempFile)) {
            // ...读取验证...
        }
    }
}
```

---

## 覆盖率要求

| 包 | 最低行覆盖率 |
|----|-------------|
| `geometry.gaia` | 90%（核心二进制格式） |
| `geometry.cad` | 85% |
| `system` | 80% |
| `dataset` | 80% |
| 整体 | 80% |

配置 JaCoCo 在 `mvn verify` 时检查：覆盖率不足则构建失败。
