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
}