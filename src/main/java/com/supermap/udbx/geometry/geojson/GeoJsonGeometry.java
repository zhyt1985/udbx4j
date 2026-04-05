package com.supermap.udbx.geometry.geojson;

import java.util.List;
import java.util.Optional;

/**
 * GeoJSON-like 几何对象（udbx4spec 规范）。
 *
 * <p>UDBX 跨语言几何数据交换的 lingua franca。支持三种几何类型：
 * <ul>
 *   <li>{@link PointGeometry} — 2D/3D 点</li>
 *   <li>{@link MultiLineStringGeometry} — 2D/3D 多线</li>
 *   <li>{@link MultiPolygonGeometry} — 2D/3D 多面</li>
 * </ul>
 *
 * <p>与标准 GeoJSON 的区别：增加 {@code srid}、{@code hasZ}、{@code bbox} 扩展字段。
 * 参见 udbx4spec/docs/02-geometry-model.md。
 */
public sealed interface GeoJsonGeometry
        permits GeoJsonGeometry.PointGeometry,
                GeoJsonGeometry.MultiLineStringGeometry,
                GeoJsonGeometry.MultiPolygonGeometry {

    /**
     * 几何类型名称（"Point"、"MultiLineString"、"MultiPolygon"）。
     */
    String type();

    /**
     * 获取可选的 SRID。
     */
    default Optional<Integer> getSrid() {
        return Optional.empty();
    }

    /**
     * 获取可选的 hasZ 标志。
     */
    default Optional<Boolean> getHasZ() {
        return Optional.empty();
    }

    /**
     * 获取可选的包围盒 [minX, minY, maxX, maxY]。
     */
    default Optional<List<Double>> getBbox() {
        return Optional.empty();
    }

    // ── Point ──────────────────────────────────────────────────────────────

    /**
     * GeoJSON-like 点几何。
     *
     * <p>{@code coordinates} 长度为 2 表示 2D，长度为 3 表示 3D。
     *
     * @param coordinates [x, y] 或 [x, y, z]
     * @param srid        空间参考系 ID（可为 null）
     * @param hasZ        是否包含 Z（可为 null）
     * @param bbox        包围盒（可为 null）
     */
    record PointGeometry(
            List<Double> coordinates,
            Integer srid,
            Boolean hasZ,
            List<Double> bbox
    ) implements GeoJsonGeometry {
        public PointGeometry {
            if (coordinates == null || (coordinates.size() != 2 && coordinates.size() != 3)) {
                throw new IllegalArgumentException("coordinates 必须为 [x, y] 或 [x, y, z]");
            }
        }

        @Override
        public String type() {
            return "Point";
        }

        @Override
        public Optional<Integer> getSrid() {
            return Optional.ofNullable(srid);
        }

        @Override
        public Optional<Boolean> getHasZ() {
            return Optional.ofNullable(hasZ);
        }

        @Override
        public Optional<List<Double>> getBbox() {
            return Optional.ofNullable(bbox);
        }

        /**
         * 便捷构造：2D 点。
         */
        public static PointGeometry of(double x, double y) {
            return new PointGeometry(List.of(x, y), null, false, null);
        }

        /**
         * 便捷构造：3D 点。
         */
        public static PointGeometry of(double x, double y, double z) {
            return new PointGeometry(List.of(x, y, z), null, true, null);
        }

        /**
         * 便捷构造：带 SRID 的 2D 点。
         */
        public static PointGeometry of(double x, double y, int srid) {
            return new PointGeometry(List.of(x, y), srid, false, null);
        }

        /**
         * 便捷构造：带 SRID 的 3D 点。
         */
        public static PointGeometry of(double x, double y, double z, int srid) {
            return new PointGeometry(List.of(x, y, z), srid, true, null);
        }
    }

    // ── MultiLineString ────────────────────────────────────────────────────

    /**
     * GeoJSON-like 多线几何。
     *
     * @param coordinates 多线坐标数组
     * @param srid        空间参考系 ID（可为 null）
     * @param hasZ        是否包含 Z（可为 null）
     * @param bbox         包围盒（可为 null）
     */
    record MultiLineStringGeometry(
            List<List<List<Double>>> coordinates,
            Integer srid,
            Boolean hasZ,
            List<Double> bbox
    ) implements GeoJsonGeometry {
        @Override
        public String type() {
            return "MultiLineString";
        }

        @Override
        public Optional<Integer> getSrid() {
            return Optional.ofNullable(srid);
        }

        @Override
        public Optional<Boolean> getHasZ() {
            return Optional.ofNullable(hasZ);
        }

        @Override
        public Optional<List<Double>> getBbox() {
            return Optional.ofNullable(bbox);
        }
    }

    // ── MultiPolygon ───────────────────────────────────────────────────────

    /**
     * GeoJSON-like 多面几何。
     *
     * @param coordinates 多面坐标数组
     * @param srid        空间参考系 ID（可为 null）
     * @param hasZ        是否包含 Z（可为 null）
     * @param bbox         包围盒（可为 null）
     */
    record MultiPolygonGeometry(
            List<List<List<List<Double>>>> coordinates,
            Integer srid,
            Boolean hasZ,
            List<Double> bbox
    ) implements GeoJsonGeometry {
        @Override
        public String type() {
            return "MultiPolygon";
        }

        @Override
        public Optional<Integer> getSrid() {
            return Optional.ofNullable(srid);
        }

        @Override
        public Optional<Boolean> getHasZ() {
            return Optional.ofNullable(hasZ);
        }

        @Override
        public Optional<List<Double>> getBbox() {
            return Optional.ofNullable(bbox);
        }
    }
}
