package com.libris.eh_libris.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GalleryPageDto {
    private int pageIndex;    // 页码 (0, 1, 2...)
    private String pageUrl;   // 图片详情页的链接 (https://e-hentai.org/s/token/gid-1)
    private String thumbUrl;  // 这一页的缩略图链接 (可选，用于做预览条)
}