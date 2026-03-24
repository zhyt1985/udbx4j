package com.supermap.udbx.geometry;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * GeometryFactory 对象池。
 *
 * <p>缓存 GeometryFactory 实例以减少对象创建开销。
 * GeometryFactory 是线程安全的，可以跨线程共享。
 *
 * <p>使用 SRID 作为键，常用坐标系（4326、4490、3857）在类加载时预初始化。
 */
public final class GeometryFactoryPool {
    private static final ConcurrentHashMap<Integer, GeometryFactory> POOL =
        new ConcurrentHashMap<>();

    private GeometryFactoryPool() {
        // 工具类，禁止实例化
    }

    /**
     * 获取指定 SRID 的 GeometryFactory 实例。
     *
     * <p>如果缓存中不存在，则创建新实例并缓存。
     *
     * @param srid 空间参考系 ID
     * @return GeometryFactory 实例
     */
    public static GeometryFactory getFactory(int srid) {
        return POOL.computeIfAbsent(srid,
            k -> new GeometryFactory(new PrecisionModel(), k));
    }

    /**
     * 预初始化常用坐标系。
     */
    static {
        getFactory(4326);  // WGS84
        getFactory(4490);  // CGCS2000
        getFactory(3857);  // Web Mercator
    }
}
