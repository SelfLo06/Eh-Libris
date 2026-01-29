package com.libris.eh_libris.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("gallery_tag_map")
// 中间表实体，仅包含 gid 和 tid
public class GalleryTagMap {
    private Long gid;
    private Integer tid;
}