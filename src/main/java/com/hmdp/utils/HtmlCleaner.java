package com.hmdp.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * HtmlCleaner - 清洗博客正文 HTML，去除标签、保留段落换行。
 *
 * 用途：
 *  - 入向量模型前清洗富文本；
 *  - 摘要生成、语义索引前的文本预处理；
 *  - 过滤危险标签（<script>、<style>）。
 */
public class HtmlCleaner {

    /**
     * 清洗 HTML：去标签、保留自然换行。
     *
     * @param html 原始 HTML 内容
     * @return 清洗后的纯文本
     */
    public static String clean(String html) {
        if (html == null || html.isEmpty()) return "";

        // 1. 使用 Jsoup 解析 HTML
        Document doc = Jsoup.parse(html);

        // 2. 去除 <script>、<style> 等危险标签
        doc.select("script, style, iframe, noscript").remove();

        // 3. 保留 <br> <p> 作为换行标记
        for (Element br : doc.select("br")) {
            br.after("\n");
        }
        for (Element p : doc.select("p")) {
            p.prepend("\n");
            p.append("\n");
        }

        // 4. 清理标签，仅保留文本内容
        String text = Jsoup.clean(doc.html(), "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));

        // 5. 替换多余空白，规范换行
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");    // 合并空格
        text = text.replaceAll("\\n{2,}", "\n").trim();       // 合并多余换行

        return text;
    }
}