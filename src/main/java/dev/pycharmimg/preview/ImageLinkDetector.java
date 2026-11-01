package dev.pycharmimg.preview;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从鼠标所在行提取完整的 HTTP(S) 链接，不解析其他行。 */
final class ImageLinkDetector {
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING_PUNCTUATION = ".,;:!?";
    static final int MAX_LINE_LENGTH = 8192;

    private ImageLinkDetector() {
    }

    /** 返回覆盖指定列的链接及其行内范围；未命中或链接无效时返回 {@code null}。 */
    static Link at(String line, int column) {
        if (line.length() > MAX_LINE_LENGTH || column < 0 || column >= line.length()) {
            return null;
        }
        Matcher matcher = URL.matcher(line);
        while (matcher.find()) {
            int end = matcher.end();
            int outer = matcher.start() - 1;
            // 仅剔除包裹链接的右括号，保留 URL 内成对的括号。
            while (end > matcher.start()) {
                char last = line.charAt(end - 1);
                if (TRAILING_PUNCTUATION.indexOf(last) >= 0) {
                    end--;
                    continue;
                }
                char opening = matchingOpening(last);
                if (opening != 0 && outer >= 0 && line.charAt(outer) == opening
                        && !hasOpeningInsideUrl(line, matcher.start(), end, opening, last)) {
                    end--;
                    outer--;
                    continue;
                }
                break;
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
                // 非法 URI 不进入预览流程。
            }
        }
        return null;
    }

    private static char matchingOpening(char closing) {
        return switch (closing) {
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            default -> 0;
        };
    }

    private static boolean hasOpeningInsideUrl(String line, int start, int end, char opening, char closing) {
        int nestedClosing = 0;
        for (int index = end - 2; index >= start; index--) {
            char current = line.charAt(index);
            if (current == closing) {
                nestedClosing++;
            } else if (current == opening) {
                if (nestedClosing == 0) {
                    return true;
                }
                nestedClosing--;
            }
        }
        return false;
    }

    record Link(String url, int start, int end) {
    }
}
