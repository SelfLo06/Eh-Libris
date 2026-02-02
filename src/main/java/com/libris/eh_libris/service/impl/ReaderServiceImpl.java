package com.libris.eh_libris.service.impl;

import com.libris.eh_libris.model.dto.CommentDto;
import com.libris.eh_libris.model.dto.GalleryPageDto;
import com.libris.eh_libris.service.ReaderService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReaderServiceImpl implements ReaderService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Value("${eh.cookie.memberId}")
    private String memberId;

    @Value("${eh.cookie.passHash}")
    private String passHash;

    @Value("${eh.cookie.uconfig}")
    private String uconfig;

    private Proxy getProxy() {
        return Proxy.NO_PROXY;
    }

    @Override
    public List<GalleryPageDto> getGalleryPages(Long gid, String token, int pageIndex) throws IOException {
        // === 核心修复 ===
        // 1. 移除 &nw=always (防止重定向回 Page 0)
        // 2. 对所有页面强制使用 &inline_set=ts_l (确保每一页都是大缩略图，解决雪碧图问题)
        // 3. 移除 &inline_set=dm_t (通常 ts_l 已经隐含了缩略图模式，少传一个参数减少重定向风险)
        String url = String.format("https://e-hentai.org/g/%d/%s/?p=%d&inline_set=ts_m", gid, token, pageIndex);

        System.out.println(">>> [Reader] 正在抓取 (Page " + pageIndex + "): " + url);

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .proxy(getProxy())
                    .cookie("nw", "1") // Cookie 已经处理了警告，无需 URL 参数
                    .cookie("ipb_member_id", memberId)
                    .cookie("ipb_pass_hash", passHash)
                    // .cookie("uconfig", uconfig) // 暂时注释，避免与 inline_set 冲突，优先信任 URL 强制参数
                    .timeout(30000)
                    .get();
        } catch (IOException e) {
            System.err.println(">>> [Reader] 网络请求异常: " + e.getMessage());
            throw e;
        }

        List<GalleryPageDto> pages = new ArrayList<>();

        // 优先解析大缩略图 (.gdtl)
        Elements elements = doc.select(".gdtm a");
        // 备用：标准模式
        if (elements.isEmpty()) elements = doc.select(".gdtl a");
        // 备用：紧凑模式
        if (elements.isEmpty()) elements = doc.select("#gdt a");

        if (elements.isEmpty()) {
            System.err.println(">>> [Reader] 解析失败，HTML预览: " + doc.body().text().substring(0, Math.min(doc.body().text().length(), 200)));
            throw new RuntimeException("未找到图片元素 (Page " + pageIndex + ")");
        }

        // 排序索引 (每页40张是 EH 的上限，以此递增)
        int currentSort = pageIndex * 40;

        for (Element link : elements) {
            String pageUrl = link.attr("href");
            String thumbStyle = "";
            String thumbUrl = "";

            // 1. 尝试解析 img (Normal/Large 模式)
            Element img = link.select("img").first();
            if (img != null) {
                thumbUrl = img.attr("src");
                // data-src 处理...
                if (thumbUrl == null || thumbUrl.isEmpty()) thumbUrl = img.attr("data-src");

                // [关键] 如果是雪碧图，img 标签上会有 style="margin:..." 用于定位
                // 或者父级 div 会有 style
                String imgStyle = img.attr("style");
                if (!imgStyle.isEmpty()) {
                    thumbStyle = imgStyle;
                }
            }

            // 2. 尝试解析 Compact 模式 (div + style background)
            if (thumbUrl.isEmpty()) {
                Element div = link.select("div").first(); // Compact 模式通常是个 div
                if (div != null) {
                    String style = div.attr("style");
                    thumbStyle = style; // 把整个 style 拿下来
                    if (style.contains("url(")) {
                        Matcher m = Pattern.compile("url\\((.*?)\\)").matcher(style);
                        if (m.find()) thumbUrl = m.group(1);
                    }
                }
            }

            System.out.println("Thumb: " + thumbUrl);

            if (!pageUrl.isEmpty()) {
                pages.add(new GalleryPageDto(currentSort++, pageUrl, thumbUrl, thumbStyle));
            }
        }

        // 调试日志：打印首图 URL 确认是否真的翻页了
        if (!pages.isEmpty()) {
            System.out.println(">>> [Reader] Page " + pageIndex + " 首张: " + pages.get(0).getPageUrl());
        }

        return pages;
    }

    @Override
    public String getTrueImageUrl(String pageUrl) throws IOException {
        // 大图解析页可以保留 nw=always，因为单页重定向影响不大，且必须跳过警告
        if (!pageUrl.contains("nw=always")) {
            pageUrl += (pageUrl.contains("?") ? "&" : "?") + "nw=always";
        }

        Document doc = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .proxy(getProxy())
                .cookie("nw", "1")
                .cookie("ipb_member_id", memberId)
                .cookie("ipb_pass_hash", passHash)
                .timeout(30000)
                .get();

        Element imgElement = doc.select("img#img").first();
        if (imgElement == null) imgElement = doc.select("#i3 img").first();

        if (imgElement != null) {
            return imgElement.attr("src");
        }
        throw new RuntimeException("无法解析大图: " + pageUrl);
    }

    @Override
    public List<CommentDto> getGalleryComments(Long gid, String token) throws IOException {
        // 评论就在画廊详情页（即 p=0 的页面）
        String url = String.format("https://e-hentai.org/g/%d/%s/", gid, token);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .proxy(getProxy())
                .cookie("nw", "1")
                .cookie("ipb_member_id", memberId)
                .cookie("ipb_pass_hash", passHash)
                .timeout(30000)
                .get();

        List<CommentDto> comments = new ArrayList<>();
        Elements commentElements = doc.select(".c1"); // 每个评论块

        for (Element el : commentElements) {
            // 解析作者
            String author = el.select(".c3 a").text();
            if (author.isEmpty()) author = el.select(".c3").text(); // 可能是已被删除的用户

            // 解析时间
            String time = el.select(".c3").text().replaceAll("Posted on ", "").trim();
            // 简单截取时间，去掉作者名部分（EH结构比较乱，这里简化处理，取前19个字符通常是时间）
            if (time.length() > 16) time = time.substring(0, 16);

            // 解析内容
            String content = el.select(".c6").html(); // 保留 HTML 以支持换行和表情

            // 解析分数
            String score = el.select(".c5 span").text(); // e.g., "+ 5"
            if (score.isEmpty()) score = el.select(".c4 a").text(); // 旧版结构

            // 判断是否上传者
            boolean isUploader = false; // 简单逻辑，可扩展

            comments.add(new CommentDto(author, time, content, score, isUploader));
        }

        return comments;
    }
}