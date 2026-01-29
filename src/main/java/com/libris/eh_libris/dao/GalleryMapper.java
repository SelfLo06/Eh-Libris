package com.libris.eh_libris.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libris.eh_libris.model.entity.Gallery;

/**
 * 继承 BaseMapper 后，你就直接拥有了 insert, delete, update, select 等方法
 * 泛型 <Gallery> 告诉它这个 Mapper 是为哪张表服务的
 */

public interface GalleryMapper extends BaseMapper<Gallery> {
    // 这里暂时不需要写任何代码，MP 已经帮你写好了最基础的增删改查
}
