# udbx4spec 合规性检查报告

## 项目信息
- 项目：udbx4j
- 类型：java
- 检查时间：2026-04-05T13:25:18.193151

## 检查概览

| 检查项 | 状态 | 通过数/总数 |
|--------|------|-------------|
| DatasetKind | ✅ | 9/9 |
| FieldType | ✅ | 14/14 |
| UdbxDataSource方法 | ✅ | 14/14 |

**总计：37/37 (100%)**

## 详细检查结果

### DatasetKind

| 名称 | 期望值 | 实际值 | 状态 |
|------|--------|--------|------|
| DatasetKind.tabular | tabular = 0 | 已找到 | ✅ |
| DatasetKind.point | point = 1 | 已找到 | ✅ |
| DatasetKind.line | line = 3 | 已找到 | ✅ |
| DatasetKind.region | region = 5 | 已找到 | ✅ |
| DatasetKind.pointZ | pointZ = 101 | 已找到 | ✅ |
| DatasetKind.lineZ | lineZ = 103 | 已找到 | ✅ |
| DatasetKind.regionZ | regionZ = 105 | 已找到 | ✅ |
| DatasetKind.text | text = 7 | 已找到 | ✅ |
| DatasetKind.cad | cad = 149 | 已找到 | ✅ |

### FieldType

| 名称 | 期望值 | 实际值 | 状态 |
|------|--------|--------|------|
| FieldType.boolean | boolean = 1 | 已找到 | ✅ |
| FieldType.byte | byte = 2 | 已找到 | ✅ |
| FieldType.int16 | int16 = 3 | 已找到 | ✅ |
| FieldType.int32 | int32 = 4 | 已找到 | ✅ |
| FieldType.int64 | int64 = 5 | 已找到 | ✅ |
| FieldType.single | single = 6 | 已找到 | ✅ |
| FieldType.double | double = 7 | 已找到 | ✅ |
| FieldType.date | date = 8 | 已找到 | ✅ |
| FieldType.binary | binary = 9 | 已找到 | ✅ |
| FieldType.geometry | geometry = 10 | 已找到 | ✅ |
| FieldType.char | char = 11 | 已找到 | ✅ |
| FieldType.ntext | ntext = 127 | 已找到 | ✅ |
| FieldType.text | text = 128 | 已找到 | ✅ |
| FieldType.time | time = 16 | 已找到 | ✅ |

### UdbxDataSource方法

| 名称 | 期望值 | 实际值 | 状态 |
|------|--------|--------|------|
| open | public ... open(...) | 已找到 | ✅ |
| create | public ... create(...) | 已找到 | ✅ |
| getDataset | public ... getDataset(...) | 已找到 | ✅ |
| listDatasets | public ... listDatasets(...) | 已找到 | ✅ |
| close | public ... close(...) | 已找到 | ✅ |
| createPointDataset | public ... createPointDataset(...) | 已找到 | ✅ |
| createLineDataset | public ... createLineDataset(...) | 已找到 | ✅ |
| createRegionDataset | public ... createRegionDataset(...) | 已找到 | ✅ |
| createPointZDataset | public ... createPointZDataset(...) | 已找到 | ✅ |
| createLineZDataset | public ... createLineZDataset(...) | 已找到 | ✅ |
| createRegionZDataset | public ... createRegionZDataset(...) | 已找到 | ✅ |
| createTabularDataset | public ... createTabularDataset(...) | 已找到 | ✅ |
| createTextDataset | public ... createTextDataset(...) | 已找到 | ✅ |
| createCadDataset | public ... createCadDataset(...) | 已找到 | ✅ |
