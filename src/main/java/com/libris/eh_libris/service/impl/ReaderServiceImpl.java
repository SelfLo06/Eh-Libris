package com.libris.eh_libris.service.impl;

import com.libris.eh_libris.model.dto.GalleryPageDto;
import com.libris.eh_libris.service.ReaderService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReaderServiceImpl implements ReaderService {
    // 模拟浏览器 User-Agent，防止被拦截
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 1. 获取画廊某一页的图片列表 (E-Hentai 默认一页显示 20 或 40 张缩略图)
     * @param gid 画廊ID
     * @param token 画廊Token
     * @param pageIndex 画廊的页码 (不是图片页码，而是缩略图翻页的页码，0开始)
     */
    @Override
    public List<GalleryPageDto> getGalleryPages(Long gid, String token, int pageIndex) throws IOException {
        String url = String.format("https://e-hentai.org/g/%d/%s/?p=%d", gid, token, pageIndex);

        // 连接 URL 并下载 HTML
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get();

        List<GalleryPageDto> pages = new ArrayList<>();

        // 解析 HTML: EH 的缩略图列表通常在 .gdtm (小图) 或 .gdtc (大图) div 中
        // 我们查找带有 href 的链接
        Elements elements = doc.select(".gdtm a"); // 这是一个 CSS 选择器

        // 如果 .gdtm 没找到，尝试 .gdtc (取决于用户的 EH 设置，为了稳健我们都试一下)
        if (elements.isEmpty()) {
            elements = doc.select(".gdtc a");
        }

        int currentSort = pageIndex * 40; // 估算当前的图片序号

        for (Element link : elements) {
            String pageUrl = link.attr("href"); // 图片详情页链接 (e-hentai.org/s/...)

            // 尝试获取缩略图
            String thumbUrl = "";
            Element img = link.select("img").first();
            if (img != null) {
                thumbUrl = img.attr("src");
            }

            pages.add(new GalleryPageDto(currentSort++, pageUrl, thumbUrl));
        }

        return pages;
    }

    /**
     * 2. 获取单张图片的真实下载地址 (解析阅读页面)
     * @param pageUrl 图片详情页链接 (e.g. https://e-hentai.org/s/xxxxx/12345-1)
     * @return 真实的图片 URL (e.g. https://ehgt.org/image.jpg)
     */
    @Override
    public String getTrueImageUrl(String pageUrl) throws IOException {
        Document doc = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get();

        // 核心：找到 id="img" 的元素
        Element imgElement = doc.select("img#img").first();

        if (imgElement != null) {
            return imgElement.attr("src");
        }

        throw new RuntimeException("无法解析图片地址，可能是网络问题或IP受限");
    }
}
