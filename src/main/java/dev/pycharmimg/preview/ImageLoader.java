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
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class ImageLoader {
    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SIDE = 4096;
    private static final int MAX_WIDTH = 480;
    private static final int MAX_HEIGHT = 320;
    private static final int MAX_REDIRECTS = 3;
    private static final long TIMEOUT_NANOS = 5_000_000_000L;

    private final ConnectionFactory connectionFactory;

    private final Map<String, BufferedImage> cache = new LinkedHashMap<>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 16;
        }
    };

    ImageLoader() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    ImageLoader(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    synchronized BufferedImage cached(String url) {
        return cache.get(url);
    }

    BufferedImage load(String url) throws IOException {
        BufferedImage existing = cached(url);
        if (existing != null) {
            return existing;
        }
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        URI current = URI.create(url);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!isHttpUrl(current)) {
                throw new IOException("Unsupported URL");
            }
            HttpURLConnection connection = connectionFactory.open(new URL(current.toString()));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(remainingMillis(deadline));
            connection.setReadTimeout(remainingMillis(deadline));
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif");
            try {
                int status = connection.getResponseCode();
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
                // CDN metadata can be generic or wrong; enforce limits on bytes actually read.
                byte[] bytes;
                try (InputStream input = connection.getInputStream()) {
                    bytes = readLimited(input, deadline);
                }
                BufferedImage image = decode(bytes);
                synchronized (this) {
                    cache.put(url, image);
                }
                return image;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && uri.getHost() != null && uri.getUserInfo() == null;
    }

    private static int remainingMillis(long deadline) throws IOException {
        long remaining = (deadline - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new IOException("Image request timed out");
        }
        return (int) Math.min(remaining, 5000);
    }

    private static byte[] readLimited(InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            remainingMillis(deadline);
            if (output.size() + count > MAX_BYTES) {
                throw new IOException("Image is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

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
                BufferedImage image = reader.read(0);
                double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
                if (scale == 1.0) {
                    return image;
                }
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));
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
