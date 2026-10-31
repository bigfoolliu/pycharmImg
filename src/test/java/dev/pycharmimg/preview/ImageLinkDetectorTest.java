package dev.pycharmimg.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class ImageLinkDetectorTest {
    @Test
    public void findsOnlyTheLinkUnderTheMouse() {
        String line = "first = 'https://example.com/a.png'; second = 'https://example.com/b.jpg'";
        ImageLinkDetector.Link first = ImageLinkDetector.at(line, line.indexOf("a.png"));
        ImageLinkDetector.Link second = ImageLinkDetector.at(line, line.indexOf("b.jpg"));
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("https://example.com/a.png", first.url());
        assertEquals("https://example.com/b.jpg", second.url());
        assertNull(ImageLinkDetector.at(line, line.indexOf("second")));
    }

    @Test
    public void removesMarkdownAndSentencePunctuation() {
        String line = "![alt](https://example.com/image?size=2),";
        ImageLinkDetector.Link link = ImageLinkDetector.at(line, line.indexOf("image"));
        assertNotNull(link);
        assertEquals("https://example.com/image?size=2", link.url());
    }

    @Test
    public void preservesBalancedParenthesesInsideUrls() {
        String plain = "https://example.com/image_(final)";
        assertEquals(plain, ImageLinkDetector.at(plain, plain.indexOf("image")).url());

        String markdown = "![alt](https://example.com/image_(final))";
        assertEquals(plain, ImageLinkDetector.at(markdown, markdown.indexOf("image")).url());
    }

    @Test
    public void trimsOnlyAnOuterClosingParenthesis() {
        String wrapped = "See (https://example.com/image.png).";
        assertEquals("https://example.com/image.png",
                ImageLinkDetector.at(wrapped, wrapped.indexOf("image")).url());

        String plain = "https://example.com/image)";
        assertEquals(plain, ImageLinkDetector.at(plain, plain.indexOf("image")).url());
    }

    @Test
    public void rejectsCredentialsAndVeryLongLines() {
        String line = "https://user:secret@example.com/image.png";
        assertNull(ImageLinkDetector.at(line, line.indexOf("image")));
        assertNull(ImageLinkDetector.at("x".repeat(8193), 2));
    }
}
