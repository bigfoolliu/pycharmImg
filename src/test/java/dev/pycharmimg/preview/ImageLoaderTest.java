package dev.pycharmimg.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.imageio.ImageIO;
import org.junit.Test;

public class ImageLoaderTest {
    @Test
    public void decodesAValidImage() throws IOException {
        BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);

        BufferedImage preview = ImageLoader.decode(bytes.toByteArray());
        assertEquals(2, preview.getWidth());
        assertEquals(3, preview.getHeight());
    }

    @Test
    public void rejectsNonImageResponsesByContent() {
        assertThrows(IOException.class, () -> ImageLoader.decode("<html/>".getBytes()));
        assertThrows(IOException.class, () -> ImageLoader.decode(new byte[1]));
    }

    @Test
    public void rejectsOversizedDimensionsBeforeDecoding() throws IOException {
        BufferedImage source = new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        assertThrows(IOException.class, () -> ImageLoader.decode(bytes.toByteArray()));
    }

    @Test
    public void scalesLargeImagesWithinThePreviewBounds() throws IOException {
        BufferedImage source = new BufferedImage(960, 640, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        BufferedImage preview = ImageLoader.decode(bytes.toByteArray());
        assertEquals(480, preview.getWidth());
        assertEquals(320, preview.getHeight());
    }

    @Test
    public void rejectsResponsesOverFiveMebibytes() {
        assertThrows(IOException.class, () -> ImageLoader.decode(new byte[5 * 1024 * 1024 + 1]));
    }

    @Test
    public void decodesPngDespiteWrongCdnHeaders() throws IOException {
        BufferedImage source = new BufferedImage(280, 280, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        ImageLoader loader = new ImageLoader(url -> response(url, bytes.toByteArray(),
                "application/octet-stream", 6 * 1024 * 1024));

        BufferedImage preview = loader.load("https://example.com/image.png");
        assertEquals(280, preview.getWidth());
        assertEquals(280, preview.getHeight());
    }

    @Test
    public void limitsActualBytesEvenWhenHeaderIsSmall() {
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        ImageLoader loader = new ImageLoader(url -> response(url, bytes, "image/png", 1));
        assertThrows(IOException.class, () -> loader.load("https://example.com/image.png"));
    }

    private static HttpURLConnection response(URL url, byte[] bytes, String contentType, long declaredLength) {
        return new HttpURLConnection(url) {
            @Override
            public int getResponseCode() {
                return 200;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public long getContentLengthLong() {
                return declaredLength;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void connect() {
            }

            @Override
            public void disconnect() {
            }

            @Override
            public boolean usingProxy() {
                return false;
            }
        };
    }
}
