package com.example.domesticflightbookingwebbackend.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Sanitize HTML nội dung News (open item #3: content = HTML + jsoup chống XSS).
 * Chỉ giữ thẻ định dạng an toàn; bỏ script/iframe/event handler.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    /** Safelist mở rộng: cho phép định dạng văn bản, ảnh, liên kết, bảng. */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("hr", "figure", "figcaption")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    /** Trả HTML đã làm sạch. Null/rỗng → chuỗi rỗng. */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
