package com.libris.eh_libris.controller;

import com.libris.eh_libris.model.dto.CommentDto;
import com.libris.eh_libris.model.dto.GalleryPageDto;
import com.libris.eh_libris.service.ReaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reader")
public class ReaderController {

    @Autowired
    private ReaderService readerService;

    /**
     * 获取画廊的分页列表 (懒加载模式：前端一页一页请求目录)
     * GET /api/reader/pages?gid=...&token=...&page=0
     */
    @GetMapping("/pages")
    public List<GalleryPageDto> getPages(
            @RequestParam Long gid,
            @RequestParam String token,
            @RequestParam(defaultValue = "0") int page
    ) {
        try {
            return readerService.getGalleryPages(gid, token, page);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("获取目录失败: " + e.getMessage());
        }
    }

    /**
     * 解析具体某张图片的真实链接
     * GET /api/reader/image?url=https://e-hentai.org/s/....
     */
    @GetMapping("/image")
    public Map<String, String> getImageUrl(@RequestParam String url) {
        try {
            String trueUrl = readerService.getTrueImageUrl(url);
            // 返回 JSON 对象
            return Map.of("url", trueUrl);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("解析图片失败: " + e.getMessage());
        }
    }

    @GetMapping("/comments")
    public List<CommentDto> getComments(@RequestParam Long gid, @RequestParam String token) {
        try {
            return readerService.getGalleryComments(gid, token);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("获取评论失败: " + e.getMessage());
        }
    }
}