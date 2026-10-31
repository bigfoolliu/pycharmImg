package dev.pycharmimg.preview;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImageLinkDetector {
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}";
    private static final int MAX_LINE_LENGTH = 8192;

    private ImageLinkDetector() {
    }

    static Link at(String line, int column) {
        if (line.length() > MAX_LINE_LENGTH || column < 0 || column >= line.length()) {
            return null;
        }
        Matcher matcher = URL.matcher(line);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && TRAILING.indexOf(line.charAt(end - 1)) >= 0) {
                end--;
            }
            if (column < matcher.start() || column >= end) {
                continue;
            }
            String value = line.substring(matcher.start(), end);
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getUserInfo() == null) {
                    return new Link(value, matcher.start(), end);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid text is not a previewable URL.
            }
        }
        return null;
    }

    record Link(String url, int start, int end) {
    }
}
