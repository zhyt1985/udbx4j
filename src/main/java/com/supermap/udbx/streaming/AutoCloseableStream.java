package com.supermap.udbx.streaming;

import java.util.stream.Stream;

/**
 * 包装 Stream 和资源，支持自动关闭。
 *
 * <p>用于流式读取数据集时，确保 Stream 和底层资源（如 ResultSet、PreparedStatement）都能正确关闭。
 *
 * <p>使用示例：
 * <pre>{@code
 * try (var stream = new AutoCloseableStream<>(featureStream, resultSet)) {
 *     stream.getStream().forEach(feature -> process(feature));
 * } // 自动关闭
 * }</pre>
 *
 * @param <T> Stream 元素类型
 */
public class AutoCloseableStream<T> implements AutoCloseable {

    private final Stream<T> stream;
    private final AutoCloseable resource;

    /**
     * 创建 AutoCloseableStream。
     *
     * @param stream   流对象
     * @param resource 需要自动关闭的资源（如 ResultSet、PreparedStatement）
     */
    public AutoCloseableStream(Stream<T> stream, AutoCloseable resource) {
        this.stream = stream;
        this.resource = resource;
    }

    /**
     * 获取包装的 Stream。
     *
     * @return 流对象
     */
    public Stream<T> getStream() {
        return stream;
    }

    /**
     * 关闭 Stream 和底层资源。
     *
     * <p>先关闭 Stream，再关闭 resource。即使 Stream 关闭失败，也会尝试关闭 resource。
     */
    @Override
    public void close() {
        try {
            if (stream != null) {
                stream.close();
            }
        } finally {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    // 忽略 resource 关闭时的异常，优先抛出 stream 关闭异常
                }
            }
        }
    }
}
