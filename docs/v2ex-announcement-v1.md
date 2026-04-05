# udbx4j V2EX 推广文章（第一版）

**标题：做了一个 UDBX 空间数据库的 Java 读写库**

---

做 GIS 开发的同学应该对 SuperMap 的 UDBX 格式不陌生。之前在 Java 生态里读写 UDBX 文件必须依赖 SuperMap 的 iObjects Java SDK，最近发现UDBX原来是一个开放数据格式，于是便根据白皮书实现了一个轻量级替代方案：**udbx4j**

**核心特性：**
- 纯 Java 实现，无原生依赖，JAR 体积 < 200KB
- 支持 UDBX 常用的矢量数据集（Point/Line/Region/CAD/Tabular）
- 流式处理 + 分页查询，适合大数据集
- 已发布到 Maven Central

**使用示例：**
```xml
<dependency>
    <groupId>io.github.zhyt1985</groupId>
    <artifactId>udbx4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
try (var datasource = UdbxDataSource.open("data.udbx")) {
    var dataset = datasource.getDataset("cities");
    var cities = dataset.stream()
        .limit(100)
        .toList();
}
```

**GitHub**: https://github.com/zhyt1985/udbx4j

**适用场景**：
- 需要 Java 读写 UDBX 文件，但不想引入 iObjects SDK
- 空间数据 ETL、格式转换、轻量级 GIS 应用

项目刚完成 v1.0.0，如果有需求或发现问题，欢迎提 Issue / PR。

---

**V2EX 风格要点：**
- ✅ 标题直接说明项目 + 价值
- ✅ 开头点出痛点（iObjects SDK 重、付费）
- ✅ 用数据说话（性能指标、体积对比）
- ✅ 提供代码示例（开发者友好）
- ✅ 明确适用场景（不夸大）
- ✅ 真诚低调（"还行"、"刚完成"）
- ✅ 欢迎反馈（社区导向）
