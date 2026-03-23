package com.supermap.udbx;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.CadDataset;
import com.supermap.udbx.dataset.Dataset;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.LineZDataset;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointZDataset;
import com.supermap.udbx.dataset.RegionDataset;
import com.supermap.udbx.dataset.RegionZDataset;
import com.supermap.udbx.dataset.TabularDataset;
import com.supermap.udbx.system.SmFieldInfoDao;
import com.supermap.udbx.system.SmRegisterDao;
import com.supermap.udbx.system.UdbxSchemaInitializer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * UDBX 数据源入口类。
 *
 * <p>负责打开或创建 .udbx 文件（SQLite 数据库），管理 Connection 生命周期，
 * 并根据 SmRegister 元信息创建具体数据集实例。
 *
 * <p>使用示例（读取）：
 * <pre>{@code
 * try (UdbxDataSource ds = UdbxDataSource.open("path/to/file.udbx")) {
 *     PointDataset points = (PointDataset) ds.getDataset("BaseMap_P");
 *     List<PointFeature> features = points.getFeatures();
 * }
 * }</pre>
 *
 * <p>使用示例（创建写入）：
 * <pre>{@code
 * try (UdbxDataSource ds = UdbxDataSource.create("path/to/new.udbx")) {
 *     PointDataset pd = ds.createPointDataset("MyPoints", 4326);
 *     pd.addFeature(1, point, Map.of());
 * }
 * }</pre>
 */
public class UdbxDataSource implements AutoCloseable {

    private final Connection conn;
    private final SmRegisterDao registerDao;

    private UdbxDataSource(Connection conn) {
        this.conn = conn;
        this.registerDao = new SmRegisterDao(conn);
    }

    /**
     * 打开指定路径的已有 UDBX 文件。
     *
     * @param path .udbx 文件路径
     * @return UdbxDataSource 实例
     * @throws RuntimeException 若无法打开文件或建立连接
     */
    public static UdbxDataSource open(String path) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            return new UdbxDataSource(conn);
        } catch (SQLException e) {
            throw new RuntimeException("无法打开 UDBX 文件: " + path, e);
        }
    }

    /**
     * 创建一个全新的 UDBX 文件并初始化系统表。
     *
     * <p>若文件已存在则抛出异常，不覆盖已有数据。
     *
     * @param path 新文件路径（不能已存在）
     * @return 已初始化的 UdbxDataSource 实例
     * @throws RuntimeException 若文件已存在或创建失败
     */
    public static UdbxDataSource create(String path) {
        File file = new File(path);
        if (file.exists()) {
            throw new RuntimeException("文件已存在，无法创建新 UDBX 文件: " + path);
        }
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            UdbxSchemaInitializer.initialize(conn);
            return new UdbxDataSource(conn);
        } catch (SQLException e) {
            throw new RuntimeException("创建 UDBX 文件失败: " + path, e);
        }
    }

    /**
     * 根据数据集名称获取数据集实例。
     *
     * @param name 数据集名称（SmDatasetName）
     * @return 数据集实例，若不存在则返回 {@code null}
     * @throws RuntimeException 若元信息查询失败或数据集类型不支持
     */
    public Dataset getDataset(String name) {
        try {
            Optional<DatasetInfo> infoOpt = registerDao.findByName(name);
            if (infoOpt.isEmpty()) {
                return null;
            }
            DatasetInfo info = infoOpt.get();
            return createDataset(info);
        } catch (UnsupportedOperationException e) {
            return null;  // 不支持的数据集类型，视同不存在
        } catch (SQLException e) {
            throw new RuntimeException("查询数据集 [" + name + "] 元信息失败", e);
        }
    }

    /**
     * 列出当前数据源中所有数据集的元信息。
     *
     * @return 所有数据集的 {@link DatasetInfo} 列表，若数据源为空则返回空列表
     * @throws RuntimeException 若查询失败
     */
    public List<DatasetInfo> listDatasetInfos() {
        try {
            return registerDao.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("列出数据集元信息失败", e);
        }
    }

    // ── 数据集创建工厂方法 ────────────────────────────────────────────────────

    /**
     * 在当前数据源中创建一个新的点数据集（DatasetType=Point）。
     *
     * @param name 数据集名称（同时作为数据表名）
     * @param srid 坐标系 ID（建议 4326）
     * @return 可立即使用的 PointDataset 实例
     * @throws RuntimeException 若建表或注册失败
     */
    public PointDataset createPointDataset(String name, int srid) {
        return createPointDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的点数据集（DatasetType=Point），并附带用户字段定义。
     *
     * @param name   数据集名称
     * @param srid   坐标系 ID
     * @param fields 用户字段列表（不含系统字段）
     * @return 可立即使用的 PointDataset 实例
     */
    public PointDataset createPointDataset(String name, int srid, List<FieldInfo> fields) {
        return (PointDataset) createVectorDataset(name, DatasetType.Point, srid,
                "POINT", 1, 2, fields);
    }

    /**
     * 在当前数据源中创建一个新的线数据集（DatasetType=Line）。
     *
     * @param name 数据集名称
     * @param srid 坐标系 ID
     * @return 可立即使用的 LineDataset 实例
     */
    public LineDataset createLineDataset(String name, int srid) {
        return createLineDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的线数据集（DatasetType=Line），并附带用户字段定义。
     */
    public LineDataset createLineDataset(String name, int srid, List<FieldInfo> fields) {
        return (LineDataset) createVectorDataset(name, DatasetType.Line, srid,
                "MULTILINESTRING", 5, 2, fields);
    }

    /**
     * 在当前数据源中创建一个新的面数据集（DatasetType=Region）。
     *
     * @param name 数据集名称
     * @param srid 坐标系 ID
     * @return 可立即使用的 RegionDataset 实例
     */
    public RegionDataset createRegionDataset(String name, int srid) {
        return createRegionDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的面数据集（DatasetType=Region），并附带用户字段定义。
     */
    public RegionDataset createRegionDataset(String name, int srid, List<FieldInfo> fields) {
        return (RegionDataset) createVectorDataset(name, DatasetType.Region, srid,
                "MULTIPOLYGON", 6, 2, fields);
    }

    // ── 三维矢量数据集工厂方法 ─────────────────────────────────────────────────

    /**
     * 在当前数据源中创建一个新的三维点数据集（DatasetType=PointZ=101）。
     */
    public PointZDataset createPointZDataset(String name, int srid) {
        return createPointZDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的三维点数据集，并附带用户字段定义。
     */
    public PointZDataset createPointZDataset(String name, int srid, List<FieldInfo> fields) {
        return (PointZDataset) createVectorDataset(name, DatasetType.PointZ, srid,
                "POINT", 1001, 3, fields);
    }

    /**
     * 在当前数据源中创建一个新的三维线数据集（DatasetType=LineZ=103）。
     */
    public LineZDataset createLineZDataset(String name, int srid) {
        return createLineZDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的三维线数据集，并附带用户字段定义。
     */
    public LineZDataset createLineZDataset(String name, int srid, List<FieldInfo> fields) {
        return (LineZDataset) createVectorDataset(name, DatasetType.LineZ, srid,
                "MULTILINESTRING", 1005, 3, fields);
    }

    /**
     * 在当前数据源中创建一个新的三维面数据集（DatasetType=RegionZ=105）。
     */
    public RegionZDataset createRegionZDataset(String name, int srid) {
        return createRegionZDataset(name, srid, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的三维面数据集，并附带用户字段定义。
     */
    public RegionZDataset createRegionZDataset(String name, int srid, List<FieldInfo> fields) {
        return (RegionZDataset) createVectorDataset(name, DatasetType.RegionZ, srid,
                "MULTIPOLYGON", 1006, 3, fields);
    }

    /**
     * 在当前数据源中创建一个新的属性表数据集（DatasetType=Tabular，无几何）。
     *
     * @param name 数据集名称
     * @return 可立即使用的 TabularDataset 实例
     */
    public TabularDataset createTabularDataset(String name) {
        return createTabularDataset(name, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的属性表数据集（DatasetType=Tabular，无几何），并附带用户字段定义。
     *
     * @param name   数据集名称
     * @param fields 用户字段列表
     * @return 可立即使用的 TabularDataset 实例
     */
    public TabularDataset createTabularDataset(String name, List<FieldInfo> fields) {
        try {
            StringBuilder ddl = new StringBuilder("CREATE TABLE \"").append(name).append("\" (")
                    .append("  SmID     INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,")
                    .append("  SmUserID INTEGER DEFAULT 0");
            for (FieldInfo f : fields) {
                ddl.append(", \"").append(f.fieldName()).append("\" ").append(sqlTypeFor(f.fieldType()));
            }
            ddl.append(")");
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(ddl.toString());
            }
            int datasetId = registerDao.insert(name, DatasetType.Tabular, 0, "SmID", null);
            if (!fields.isEmpty()) {
                new SmFieldInfoDao(conn).insertAll(datasetId, fields);
            }
            DatasetInfo info = new DatasetInfo(datasetId, name, DatasetType.Tabular, 0, 0);
            return new TabularDataset(conn, info);
        } catch (SQLException e) {
            throw new RuntimeException("创建属性表数据集 [" + name + "] 失败", e);
        }
    }

    public CadDataset createCadDataset(String name) {
        return createCadDataset(name, Collections.emptyList());
    }

    /**
     * 在当前数据源中创建一个新的 CAD 数据集（DatasetType=CAD=149），并附带用户字段定义。
     *
     * <p>CAD 数据集不注册到 geometry_columns（使用 SuperMap 自定义 GeoHeader 格式）。
     */
    public CadDataset createCadDataset(String name, List<FieldInfo> fields) {
        try {
            StringBuilder ddl = new StringBuilder("CREATE TABLE \"").append(name).append("\" (")
                    .append("  SmID       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,")
                    .append("  SmUserID   INTEGER DEFAULT 0,")
                    .append("  \"SmGeometry\" BLOB");
            for (FieldInfo f : fields) {
                ddl.append(", \"").append(f.fieldName()).append("\" ").append(sqlTypeFor(f.fieldType()));
            }
            ddl.append(")");
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(ddl.toString());
            }
            int datasetId = registerDao.insert(name, DatasetType.CAD, 0, "SmID", "SmGeometry");
            if (!fields.isEmpty()) {
                new SmFieldInfoDao(conn).insertAll(datasetId, fields);
            }
            DatasetInfo info = new DatasetInfo(datasetId, name, DatasetType.CAD, 0, 0);
            return new CadDataset(conn, info);
        } catch (SQLException e) {
            throw new RuntimeException("创建 CAD 数据集 [" + name + "] 失败", e);
        }
    }

    /**
     * 关闭底层数据库连接。
     */
    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("关闭 UDBX 数据源失败", e);
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 根据 DatasetInfo 中的 DatasetType 创建具体数据集实例。
     */
    private Dataset createDataset(DatasetInfo info) {
        return switch (info.datasetType()) {
            case Tabular -> new TabularDataset(conn, info);
            case Point -> new PointDataset(conn, info);
            case PointZ -> new PointZDataset(conn, info);
            case Line -> new LineDataset(conn, info);
            case LineZ -> new LineZDataset(conn, info);
            case Region -> new RegionDataset(conn, info);
            case RegionZ -> new RegionZDataset(conn, info);
            case CAD -> new CadDataset(conn, info);
            default -> throw new UnsupportedOperationException(
                    "暂不支持的数据集类型: " + info.datasetType() + "（数据集: " + info.datasetName() + "）");
        };
    }

    /**
     * 创建矢量数据集（Point/Line/Region）的公共逻辑：建表 + 注册。
     *
     * @param name         数据集名称
     * @param type         数据集类型
     * @param srid         坐标系 ID
     * @param geoTypeName  SQLite 列类型名称（POINT / MULTILINESTRING / MULTIPOLYGON）
     * @param geometryType geometry_columns.geometry_type 值
     * @param coordDim     坐标维度（2 或 3）
     */
    private Dataset createVectorDataset(String name, DatasetType type, int srid,
                                         String geoTypeName, int geometryType, int coordDim) {
        return createVectorDataset(name, type, srid, geoTypeName, geometryType, coordDim,
                Collections.emptyList());
    }

    private Dataset createVectorDataset(String name, DatasetType type, int srid,
                                         String geoTypeName, int geometryType, int coordDim,
                                         List<FieldInfo> fields) {
        try {
            // 1. 建数据表
            StringBuilder ddl = new StringBuilder("CREATE TABLE \"").append(name).append("\" (")
                    .append("  SmID     INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,")
                    .append("  SmUserID INTEGER DEFAULT 0,")
                    .append("  \"SmGeometry\" ").append(geoTypeName).append(" DEFAULT ''");
            for (FieldInfo f : fields) {
                ddl.append(", \"").append(f.fieldName()).append("\" ").append(sqlTypeFor(f.fieldType()));
            }
            ddl.append(")");
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(ddl.toString());
            }
            // 2. 注册到 SmRegister
            int datasetId = registerDao.insert(name, type, srid, "SmID", "SmGeometry");

            // 3. 注册到 geometry_columns（f_table_name 必须小写）
            try (var stmt = conn.prepareStatement(
                    "INSERT INTO geometry_columns " +
                    "(f_table_name, f_geometry_column, geometry_type, coord_dimension, srid, spatial_index_enabled) " +
                    "VALUES (?, 'smgeometry', ?, ?, ?, 0)")) {
                stmt.setString(1, name.toLowerCase());
                stmt.setInt(2, geometryType);
                stmt.setInt(3, coordDim);
                stmt.setInt(4, srid);
                stmt.executeUpdate();
            }

            // 4. 注册字段元信息（如有）
            if (!fields.isEmpty()) {
                new SmFieldInfoDao(conn).insertAll(datasetId, fields);
            }

            DatasetInfo info = new DatasetInfo(datasetId, name, type, 0, srid);
            return createDataset(info);
        } catch (SQLException e) {
            throw new RuntimeException("创建矢量数据集 [" + name + "] 失败", e);
        }
    }

    /**
     * 将 SuperMap FieldType 映射为 SQLite 列类型名称。
     */
    private static String sqlTypeFor(FieldType type) {
        return switch (type) {
            case Boolean, Byte, Int16, Int32, Int64 -> "INTEGER";
            case Single, Double -> "REAL";
            case Binary, Geometry -> "BLOB";
            default -> "TEXT";  // Char, NText, Text, Date, Time
        };
    }
}
