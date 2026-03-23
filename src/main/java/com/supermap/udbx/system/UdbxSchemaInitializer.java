package com.supermap.udbx.system;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * UDBX 文件系统表初始化器。
 *
 * <p>负责在新建 .udbx 文件时创建所有系统表并写入初始数据。
 * 对应白皮书 §2 节系统表定义。
 *
 * <p>创建的表：
 * <ul>
 *   <li>{@code spatial_ref_sys} — SpatiaLite 坐标系表（含 WGS84 SRID=4326）</li>
 *   <li>{@code geometry_columns} — 矢量数据集几何列注册表</li>
 *   <li>{@code SmDataSourceInfo} — 数据源元信息表</li>
 *   <li>{@code SmRegister} — 数据集注册表</li>
 *   <li>{@code SmFieldInfo} — 字段元信息表</li>
 * </ul>
 *
 * <p>注意：新建文件不包含 SpatiaLite 扩展的几何约束触发器（如 GeometryConstraints）。
 * 几何数据验证由应用层保证。
 */
public final class UdbxSchemaInitializer {

    private UdbxSchemaInitializer() {}

    /**
     * 在给定连接上初始化所有系统表。
     *
     * @param conn 已打开的 SQLite 数据库连接
     * @throws SQLException DDL 执行失败时抛出
     */
    public static void initialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA journal_mode = WAL");
            createSpatialRefSys(stmt);
            createGeometryColumns(stmt);
            createSmDataSourceInfo(stmt);
            createSmRegister(stmt);
            createSmFieldInfo(stmt);
            insertInitialData(stmt);
        }
    }

    // ── 建表 DDL ─────────────────────────────────────────────────────────────

    private static void createSpatialRefSys(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "CREATE TABLE spatial_ref_sys (" +
                "  srid       INTEGER NOT NULL PRIMARY KEY," +
                "  auth_name  TEXT    NOT NULL," +
                "  auth_srid  INTEGER NOT NULL," +
                "  ref_sys_name TEXT NOT NULL DEFAULT 'Unknown'," +
                "  proj4text  TEXT    NOT NULL," +
                "  srtext     TEXT    NOT NULL DEFAULT 'Undefined'" +
                ")");
    }

    private static void createGeometryColumns(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "CREATE TABLE geometry_columns (" +
                "  f_table_name          TEXT    NOT NULL," +
                "  f_geometry_column     TEXT    NOT NULL," +
                "  geometry_type         INTEGER NOT NULL," +
                "  coord_dimension       INTEGER NOT NULL," +
                "  srid                  INTEGER NOT NULL," +
                "  spatial_index_enabled INTEGER NOT NULL," +
                "  CONSTRAINT pk_geom_cols PRIMARY KEY (f_table_name, f_geometry_column)" +
                ")");
    }

    private static void createSmDataSourceInfo(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "CREATE TABLE SmDataSourceInfo (" +
                "  SmFlag            INTEGER DEFAULT 0 NOT NULL PRIMARY KEY AUTOINCREMENT," +
                "  SmVersion         INTEGER," +
                "  SmDsDescription   TEXT," +
                "  SmProjectInfo     BLOB," +
                "  SmLastUpdateTime  DATE DEFAULT CURRENT_TIMESTAMP NOT NULL," +
                "  SmDataFormat      INTEGER DEFAULT 0 NOT NULL" +
                ")");
    }

    private static void createSmRegister(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "CREATE TABLE SmRegister (" +
                "  SmDatasetID             INTEGER DEFAULT 0 NOT NULL PRIMARY KEY," +
                "  SmDatasetName           TEXT," +
                "  SmTableName             TEXT," +
                "  SmOption                INTEGER," +
                "  SmEncType               INTEGER," +
                "  SmParentDTID            INTEGER DEFAULT 0 NOT NULL," +
                "  SmDatasetType           INTEGER," +
                "  SmObjectCount           INTEGER DEFAULT 0 NOT NULL," +
                "  SmLeft                  REAL," +
                "  SmRight                 REAL," +
                "  SmTop                   REAL," +
                "  SmBottom                REAL," +
                "  SmIDColName             TEXT," +
                "  SmGeoColName            TEXT," +
                "  SmMinZ                  REAL," +
                "  SmMaxZ                  REAL," +
                "  SmSRID                  INTEGER DEFAULT 0," +
                "  SmIndexType             INTEGER DEFAULT 1," +
                "  SmToleRanceFuzzy        REAL," +
                "  SmToleranceDAngle       REAL," +
                "  SmToleranceNodeSnap     REAL," +
                "  SmToleranceSmallPolygon REAL," +
                "  SmToleranceGrain        REAL," +
                "  SmMaxGeometrySize       INTEGER DEFAULT 0 NOT NULL," +
                "  SmOptimizeCount         INTEGER DEFAULT 0 NOT NULL," +
                "  SmOptimizeRatio         REAL," +
                "  SmDescription           TEXT," +
                "  SmExtInfo               TEXT," +
                "  SmCreateTime            TEXT," +
                "  SmLastUpdateTime        TEXT," +
                "  SmProjectInfo           BLOB" +
                ")");
    }

    private static void createSmFieldInfo(Statement stmt) throws SQLException {
        stmt.executeUpdate(
                "CREATE TABLE SmFieldInfo (" +
                "  SmID                INTEGER DEFAULT 0 NOT NULL PRIMARY KEY AUTOINCREMENT," +
                "  SmDatasetID         INTEGER," +
                "  SmFieldName         TEXT," +
                "  SmFieldCaption      TEXT," +
                "  SmFieldType         INTEGER," +
                "  SmFieldFormat       TEXT," +
                "  SmFieldSign         INTEGER," +
                "  SmFieldDomain       TEXT," +
                "  SmFieldUpdatable    INTEGER," +
                "  SmFieldbRequired    INTEGER," +
                "  SmFieldDefaultValue TEXT," +
                "  SmFieldSize         INTEGER" +
                ")");
    }

    // ── 初始数据 ──────────────────────────────────────────────────────────────

    private static void insertInitialData(Statement stmt) throws SQLException {
        // SmDataSourceInfo: version=0, dataFormat=1
        stmt.executeUpdate(
                "INSERT INTO SmDataSourceInfo (SmVersion, SmDataFormat) VALUES (0, 1)");

        // spatial_ref_sys: SRID=0（无坐标系占位符）
        stmt.executeUpdate(
                "INSERT INTO spatial_ref_sys (srid, auth_name, auth_srid, ref_sys_name, proj4text, srtext) " +
                "VALUES (0, 'none', 0, 'Undefined', '', 'Undefined')");

        // spatial_ref_sys: SRID=4326 WGS84
        stmt.executeUpdate(
                "INSERT INTO spatial_ref_sys (srid, auth_name, auth_srid, ref_sys_name, proj4text, srtext) " +
                "VALUES (4326, 'epsg', 4326, 'WGS 84'," +
                " '+proj=longlat +datum=WGS84 +no_defs'," +
                " 'GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4326\"]]')");
    }
}
