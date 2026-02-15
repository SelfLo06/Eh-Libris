package com.libris.eh_libris.controller;

import com.libris.eh_libris.model.entity.Gallery;
import com.libris.eh_libris.model.dto.FavoriteRequestDto;
import com.libris.eh_libris.model.dto.FavoriteStatusDto;
import com.libris.eh_libris.service.FavoriteGalleryService;
import com.libris.eh_libris.service.GalleryService;
import com.libris.eh_libris.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

    @Autowired
    private GalleryService galleryService;

    @Autowired
    private FavoriteGalleryService favoriteGalleryService;

    @Autowired
    private SearchService searchService;


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

    @GetMapping("/latest")
    public List<Gallery> getLatestTopRatedGalleries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size) {
        return galleryService.listLatestTopRated(page, size);
    }

    @GetMapping("/search")
    public List<Gallery> searchGalleries(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "false") boolean strict,
            @RequestParam(defaultValue = "global") String scope
    ) {
        return searchService.search(q, page, size, minRating, strict, scope);
    }

    @GetMapping("/favorites")
    public List<Gallery> getFavoriteGalleries() {
        return galleryService.listFavoriteGalleries();
    }

    @GetMapping("/favorites/remote")
    public List<Gallery> getRemoteFavoriteGalleries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return galleryService.listRemoteFavoriteGalleries(page, limit);
    }

    @GetMapping("/{gid}/{token}")
    public Gallery getGalleryDetail(@PathVariable Long gid, @PathVariable String token) {
        Gallery gallery = galleryService.getDetailWithTags(gid, token);
        if (gallery == null) {
            throw new RuntimeException("获取画廊详情失败，gid=" + gid);
        }
        return gallery;
    }

    @GetMapping("/favorite/{gid}")
    public FavoriteStatusDto getFavoriteStatus(@PathVariable Long gid) {
        return favoriteGalleryService.getStatus(gid);
    }

    @PostMapping("/favorite")
    public FavoriteStatusDto saveFavorite(@RequestBody FavoriteRequestDto request) {
        if (request == null || request.getGid() == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new RuntimeException("参数错误：gid/token 不能为空");
        }
        return favoriteGalleryService.saveFavorite(
                request.getGid(),
                request.getToken(),
                request.getFavCat(),
                request.getFavNote()
        );
    }

    @DeleteMapping("/favorite/{gid}")
    public FavoriteStatusDto removeFavorite(@PathVariable Long gid) {
        favoriteGalleryService.removeFavorite(gid);
        return favoriteGalleryService.getStatus(gid);
    }
}
