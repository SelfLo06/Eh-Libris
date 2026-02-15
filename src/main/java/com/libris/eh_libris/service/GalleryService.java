package com.libris.eh_libris.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.libris.eh_libris.model.entity.Gallery;

import java.util.List;

public interface GalleryService extends IService<Gallery> {
    // 保存画廊到本地数据库
    //boolean saveGallery(Gallery gallery);

    boolean fetchAndSave(Long gid, String token);

    /**
     * 分页查询画廊，并预取下一页数据
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 包含当前页和下一页的画廊列表
     */
    public List<Gallery> listWithPagination(int pageNum, int pageSize);

    /**
     * 获取单本画廊详情，并附带 tags。
     * 当本地不存在时会尝试实时抓取并落库。
     */
    Gallery getDetailWithTags(Long gid, String token);

    /**
     * 获取本地收藏画廊列表（按收藏更新时间倒序），并附带 tags。
     */
    List<Gallery> listFavoriteGalleries();

    /**
     * 读取远端账户收藏页（依赖登录 Cookie），并返回画廊详情列表。
     */
    List<Gallery> listRemoteFavoriteGalleries(int page, int limit);

    /**
     * 获取最新画廊列表（仅保留评分为 5.0 的条目）。
     */
    List<Gallery> listLatestTopRated(int pageNum, int pageSize);
}
