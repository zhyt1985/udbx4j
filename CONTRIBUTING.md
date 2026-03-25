# Contributing to udbx4j

感谢您对 udbx4j 的关注！我们欢迎各种形式的贡献。

## 开发环境

### 环境要求
- Java 17+
- Maven 3.6+

### 构建项目
```bash
mvn clean install
```

### 运行测试
```bash
# 运行所有测试（单元测试 + 集成测试）
mvn verify

# 仅运行单元测试（不含集成测试）
mvn test

# 使用 Makefile（推荐）
make test          # 运行 Spec 测试
make test-all      # 运行所有测试 + 覆盖率报告
make coverage      # 打开覆盖率报告
```

### 代码规范
- 遵循 `rules/` 目录中的编码规范
- 测试覆盖率 ≥ 80%
- 提交格式：`feat/fix/test/refactor/docs: 描述`

## 提交流程

1. **Fork 本仓库**
   - 访问 https://github.com/zhyt1985/udbx4j
   - 点击 "Fork" 按钮

2. **克隆您的 Fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/udbx4j.git
   cd udbx4j
   ```

3. **创建特性分支**
   ```bash
   git checkout -b feature/amazing-feature
   ```

4. **进行更改并提交**
   ```bash
   git add .
   git commit -m "feat: add amazing feature"
   ```

5. **推送到您的 Fork**
   ```bash
   git push origin feature/amazing-feature
   ```

6. **创建 Pull Request**
   - 访问 https://github.com/zhyt1985/udbx4j/pulls
   - 点击 "New Pull Request"
   - 填写 PR 描述模板

## 测试要求

所有新功能必须包含：

### 1. Spec 测试（基于白皮书二进制规范）
```java
// src/test/java/com/supermap/udbx/spec/GaiaPointSpecTest.java
@Test
void shouldDecodePointWithCorrectCoordinates() {
    // Given: 硬编码的二进制输入（来自白皮书）
    byte[] input = {(byte) 0x00, 0x01, 0x00, 0x20, ...};

    // When: 解码几何
    Point point = reader.readPoint(input);

    // Then: 验证结果
    assertThat(point.getX()).isEqualTo(116.404);
    assertThat(point.getY()).isEqualTo(39.915);
}
```

### 2. 集成测试（使用 SampleData.udbx）
```java
// src/test/java/com/supermap/udbx/integration/PointDatasetIT.java
@Test
void shouldReadPointDataFromSampleFile() {
    // Given: 打开示例 UDBX 文件
    try (UdbxDataSource dataSource = UdbxDataSource.open("src/test/resources/SampleData.udbx")) {
        PointDataset dataset = (PointDataset) dataSource.getDataset("PointData");

        // When: 读取要素
        List<PointFeature> features = dataset.getFeatures();

        // Then: 验证结果
        assertThat(features).isNotEmpty();
        assertThat(features.get(0).getGeometry()).isNotNull();
    }
}
```

### 3. 覆盖率检查
```bash
mvn verify
# 确保覆盖率 ≥ 80%，否则构建失败
```

## 性能测试

性能相关的更改需要：

### 1. 添加 JMH 基准测试
```java
// src/test/java/com/supermap/udbx/benchmark/YourFeatureBenchmark.java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class YourFeatureBenchmark {

    @Benchmark
    public void benchmarkYourFeature() {
        // 您的基准测试代码
    }
}
```

### 2. 与基线对比
```bash
# 运行基准测试
mvn clean install
java -jar target/benchmarks.jar

# 与 baseline.json 对比性能
# 如果性能下降超过 10%，需要优化
```

### 3. 更新性能报告
- 如果性能有显著提升，更新 `docs/performance-roadmap/` 目录中的相关报告
- 在 CHANGELOG.md 中记录性能改进

## 文档

更新相关文档：

### 1. 用户可见的更改
- **README.md**：新功能的使用示例
- **CHANGELOG.md**：版本变更记录

### 2. 架构性更改
- **CLAUDE.md**：项目架构说明
- **docs/** 目录：技术文档

### 3. API 变更
- 添加 JavaDoc 注释
- 更新 API 使用示例

## 开发规范

### 编码风格
详见 `rules/java-coding-style.md`：
- 不可变优先（使用 Record）
- 小文件（200-400 行）
- 清晰命名
- 无深嵌套（>4 层）

### 测试规范
详见 `rules/java-testing.md`：
- TDD 流程（先写测试）
- 80% 覆盖率要求
- Spec 测试 + 集成测试

### 提交规范
```
<type>: <description>

[optional body]

Types: feat, fix, refactor, docs, test, chore, perf
```

示例：
```
feat: add batch write API

- add addFeaturesBatch() method
- use single transaction for performance
- 10-50x performance improvement
```

## 获取帮助

### 问题报告
- 提交 Issue：https://github.com/zhyt1985/udbx4j/issues
- 使用 Bug 报告模板

### 功能请求
- 提交 Issue：https://github.com/zhyt1985/udbx4j/issues
- 使用功能请求模板

### 技术讨论
- 查看 `CLAUDE.md` 了解项目架构
- 查看 `docs/` 目录获取技术细节
- 查看 `rules/` 目录了解开发规范

## 代码审查

所有 Pull Request 都需要经过代码审查：

### 审查清单
- [ ] 代码遵循项目规范
- [ ] 测试覆盖率 ≥ 80%
- [ ] 所有测试通过（`mvn verify`）
- [ ] 文档已更新
- [ ] 提交信息格式正确
- [ ] 无不必要的代码变更

### 审查重点
1. **正确性**：功能是否按预期工作
2. **性能**：是否有性能回归
3. **可读性**：代码是否清晰易懂
4. **测试**：测试是否充分
5. **文档**：文档是否完整

## 许可证

贡献的代码将采用 MIT License 许可证。通过提交 PR，您同意您的贡献将使用此许可证。

## 致谢

感谢所有贡献者！您的贡献让 udbx4j 变得更好。

---

**相关文档**：
- [README.md](README.md) - 项目介绍和快速开始
- [CHANGELOG.md](CHANGELOG.md) - 版本变更记录
- [CLAUDE.md](CLAUDE.md) - 项目开发文档
- [rules/](rules/) - 开发规范目录
