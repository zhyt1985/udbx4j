# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - Performance Optimization (2025-01-24)

#### Phase 0: Performance Baseline
- JMH (Java Microbenchmark Harness) integration
- Baseline performance benchmarks:
  - Read 10K points: 4.516 ms
  - Geometry decode: 9.36 nanoseconds
- Performance testing infrastructure

#### Phase 1: Basic Optimization
- **GeometryFactoryPool** - Object pooling to reduce 70-80% object allocation
- **ArrayList pre-allocation** - Eliminate dynamic array resizing overhead
- **Performance improvement**: **92.5% faster** (0.340ms vs 4.5ms baseline)
- Geometry decode benchmark suite

#### Phase 2: Streaming & Pagination
- **AutoCloseableStream** - Resource-safe stream wrapper with automatic cleanup
- **FeatureSpliterator** - Lazy-loading iterator for large datasets
- **Pagination API** - `getFeatures(offset, limit)` and `getCount()` methods
- **Memory efficiency**: Pagination uses only +22% memory vs full load
- Streaming read benchmark suite

#### Phase 3: Concurrency Optimization
- **UdbxDataSourcePool** - HikariCP-based connection pool
- **Batch write API** - `addFeaturesBatch()` with transaction management
- **WAL mode** - Write-Ahead Logging for improved concurrency
- **Concurrency improvement**: **3.49x scalability** (7.94M vs 2.27M ops/s)
- Concurrent read benchmark suite
- Optional Micrometer performance monitoring integration

### Changed
- Refactored `GaiaGeometryReader` to use `GeometryFactoryPool`
- All Dataset classes now use ArrayList pre-allocation
- Improved test coverage: 315 tests passing

### Performance Comparison

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Single-thread read | 4.5 ms | 0.34 ms | **92.5%** |
| Concurrent throughput | 2.27M ops/s | 7.94M ops/s | **3.49x** |
| Batch write | 1x | 10-50x | **10-50x** |
| Memory (pagination) | 100% | 122% | **+22%** |

### Technical Highlights
- **Zero JNI overhead** - Pure Java + JDBC implementation
- **Thread-safe** - ConcurrentHashMap, AtomicInteger, parameterized queries
- **Resource-safe** - AutoCloseable, try-with-resources, @TearDown
- **SQL injection protection** - PreparedStatement, table name escaping
- **Observability** - Micrometer integration (Prometheus/Grafana ready)

### Documentation
- Phase 0 baseline report: `docs/performance-roadmap/phase0-baseline.md`
- Phase 1 optimization report: `docs/performance-roadmap/phase1-basics.md`
- Phase 2 streaming report: `docs/performance-roadmap/phase2-streaming.md`
- Phase 3 concurrency report: `docs/performance-roadmap/phase3-summary.md`
- iObjects Java comparison: `docs/comparison/vs-iobjects-java.md`

## [1.0.0] - Initial Release

### Features
- Read/write support for UDBX format (Point, Line, Region, CAD datasets)
- GAIA geometry encoding/decoding (SpatiaLite compatible)
- Spec-based testing with comprehensive coverage
- Integration tests with SampleData.udbx

### Architecture
- Pure Java implementation (no native dependencies)
- JDBC-based SQLite access
- JTS geometry integration
- Immutable metadata classes (Java Records)

---

## Links

- [Specification](docs/superpowers/specs/2025-01-24-performance-optimization-plan.md)
- [Implementation Plan](docs/superpowers/plans/2025-01-24-performance-optimization.md)
- [Performance Roadmap](docs/performance-roadmap/)
