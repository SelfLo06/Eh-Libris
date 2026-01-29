package com.libris.eh_libris.controller;

import com.libris.eh_libris.model.entity.Gallery;
import com.libris.eh_libris.service.GalleryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

    @Autowired
    private GalleryService galleryService;


//    // 访问 http://localhost:8080/api/gallery/add-test 触发存入
//    @GetMapping("/add-test")
//    public String addTest() {
//        Gallery g = Gallery.builder()
//                .gid(System.currentTimeMillis()) // 用当前时间戳当 ID，防止主键冲突
//                .token("web_test_token")
//                .title("Gallery Added from Controller")
//                .build();
//
//        boolean success = galleryService.saveGallery(g);
//        return success ? "成功存入数据库！" : "存入失败";
//    }

    // 访问：http://localhost:8080/api/gallery/fetch?gid=2231376&token=a7584a5932
    @GetMapping("/fetch")
    public String fetchFromEh(@RequestParam Long gid, @RequestParam String token) {
        boolean success = galleryService.fetchAndSave(gid, token);
        return success ? "成功从 E-Hentai 抓取并存入本地仓库！" : "抓取失败，请检查控制台日志";
    }

    @GetMapping("/all")
    public List<Gallery> getAllGalleries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size) {
        // 调用分页查询方法
        return galleryService.listWithPagination(page, size);
    }
}