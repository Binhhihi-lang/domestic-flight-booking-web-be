package com.example.domesticflightbookingwebbackend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test sanitize HTML chống XSS (open item #3). */
class HtmlSanitizerTest {

    @Test
    void stripTheScript() {
        String out = HtmlSanitizer.sanitize("<p>Nội dung</p><script>alert('xss')</script>");

        assertThat(out).contains("<p>Nội dung</p>");
        assertThat(out).doesNotContain("<script>");
        assertThat(out).doesNotContain("alert");
    }

    @Test
    void stripEventHandler() {
        String out = HtmlSanitizer.sanitize("<img src=\"x.jpg\" onerror=\"alert('xss')\">");

        assertThat(out).doesNotContain("onerror");
    }

    @Test
    void stripIframe() {
        String out = HtmlSanitizer.sanitize("<iframe src=\"http://evil.com\"></iframe><p>ok</p>");

        assertThat(out).doesNotContain("<iframe");
        assertThat(out).contains("<p>ok</p>");
    }

    @Test
    void giuTheDinhDangAnToan() {
        String out = HtmlSanitizer.sanitize("<h2>Tiêu đề</h2><p><strong>đậm</strong> và <em>nghiêng</em></p>");

        assertThat(out).contains("<h2>");
        assertThat(out).contains("<strong>");
        assertThat(out).contains("<em>");
    }

    @Test
    void nullVaRongTraChuoiRong() {
        assertThat(HtmlSanitizer.sanitize(null)).isEmpty();
        assertThat(HtmlSanitizer.sanitize("   ")).isEmpty();
    }
}
