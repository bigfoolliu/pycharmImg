package dev.pycharmimg.preview;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** 限量下载并解码图片，缓存可复用的预览图。 */
final class ImageLoader {
    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SIDE = 4096;
    private static final int MAX_DECODED_PIXELS = 2_000_000;
    private static final int MAX_WIDTH = 480;
    private static final int MAX_HEIGHT = 320;
    private static final int MAX_REDIRECTS = 3;
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final ScheduledThreadPoolExecutor TIMEOUTS = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "pycharm-image-preview-timeout");
        thread.setDaemon(true);
        return thread;
    });

    static {
        TIMEOUTS.setRemoveOnCancelPolicy(true);
    }

    private final ConnectionFactory connectionFactory;
    private final long timeoutMillis;

    /** 跟踪当前连接，使悬停取消和超时都能主动断开请求。 */
    static final class Request {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();

        void cancel() {
            cancelled.set(true);
            HttpURLConnection current = connection.getAndSet(null);
            if (current != null) {
                current.disconnect();
            }
        }

        void check() throws IOException {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Image request cancelled or timed out");
            }
        }

        void bind(HttpURLConnection current) throws IOException {
            check();
            connection.set(current);
            // 覆盖“检查取消”与“绑定连接”之间发生的取消。
            if (cancelled.get()) {
                cancel();
                throw new IOException("Image request cancelled or timed out");
            }
        }

        void unbind(HttpURLConnection current) {
            connection.compareAndSet(current, null);
        }
    }

    private final Map<String, BufferedImage> cache = new LinkedHashMap<>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 16;
        }
    };

    ImageLoader() {
        this(url -> (HttpURLConnection) url.openConnection(), DEFAULT_TIMEOUT_MILLIS);
    }

    ImageLoader(ConnectionFactory connectionFactory) {
        this(connectionFactory, DEFAULT_TIMEOUT_MILLIS);
    }

    ImageLoader(ConnectionFactory connectionFactory, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.connectionFactory = connectionFactory;
        this.timeoutMillis = timeoutMillis;
    }

    synchronized BufferedImage cached(String url) {
        return cache.get(url);
    }

    BufferedImage load(String url) throws IOException {
        return load(url, new Request());
    }

    /** 使用同一截止时间约束重定向与读取，并在写入缓存前检查请求状态。 */
    BufferedImage load(String url, Request request) throws IOException {
        request.check();
        BufferedImage existing = cached(url);
        if (existing != null) {
            return existing;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        ScheduledFuture<?> timeout = TIMEOUTS.schedule(request::cancel, timeoutMillis, TimeUnit.MILLISECONDS);
        try {
            URI current = URI.create(url);
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                request.check();
                if (!isHttpUrl(current)) {
                    throw new IOException("Unsupported URL");
                }
                remainingMillis(deadline, request);
                HttpURLConnection connection = connectionFactory.open(new URL(current.toString()));
                try {
                    request.bind(connection);
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(remainingMillis(deadline, request));
                    connection.setReadTimeout(Math.min(remainingMillis(deadline, request), 1000));
                    connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif");
                    int status = connection.getResponseCode();
                    remainingMillis(deadline, request);
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null || redirect == MAX_REDIRECTS) {
                            throw new IOException("Too many redirects");
                        }
                        current = current.resolve(location);
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw new IOException("HTTP " + status);
                    }
                    // CDN 响应头可能不准确，以实际读取字节数限制下载量。
                    byte[] bytes;
                    try (InputStream input = connection.getInputStream()) {
                        bytes = readLimited(input, deadline, request);
                    }
                    request.check();
                    BufferedImage image = decode(bytes);
                    remainingMillis(deadline, request);
                    synchronized (this) {
                        cache.put(url, image);
                    }
                    return image;
                } finally {
                    request.unbind(connection);
                    connection.disconnect();
                }
            }
            throw new IOException("Too many redirects");
        } finally {
            timeout.cancel(false);
        }
    }

    private static boolean isHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && uri.getHost() != null && uri.getUserInfo() == null;
    }

    private static int remainingMillis(long deadline, Request request) throws IOException {
        request.check();
        long remaining = (deadline - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0) {
            throw new IOException("Image request timed out");
        }
        return (int) Math.min(remaining, 5000);
    }

    private static byte[] readLimited(InputStream input, long deadline, Request request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (true) {
            remainingMillis(deadline, request);
            count = input.read(buffer);
            remainingMillis(deadline, request);
            if (count == -1) {
                break;
            }
            if (output.size() + count > MAX_BYTES) {
                throw new IOException("Image is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    /** 校验格式与尺寸后读取首帧，并缩放到弹窗尺寸。 */
    static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Image is too large");
        }
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Invalid image data");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!format.equals("PNG") && !format.equals("JPEG") && !format.equals("GIF")) {
                    throw new IOException("Unsupported image format");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
                    throw new IOException("Image dimensions are too large");
                }
                // 在读取阶段降采样，避免为大图创建完整尺寸的解码结果。
                int subsampling = 1;
                while ((long) ((width + subsampling - 1) / subsampling)
                        * ((height + subsampling - 1) / subsampling) > MAX_DECODED_PIXELS) {
                    subsampling++;
                }
                ImageReadParam parameters = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    parameters.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage image = reader.read(0, parameters);
                double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));
                if (image.getWidth() == targetWidth && image.getHeight() == targetHeight) {
                    return image;
                }
                BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    graphics.dispose();
                    image.flush();
                }
                return scaled;
            } finally {
                reader.dispose();
            }
        }
    }
}
