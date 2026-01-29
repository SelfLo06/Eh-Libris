package com.libris.eh_libris.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class EhGalleryMetadata {
    private Long gid;
    private String token;
    private String title;

    @JsonProperty("title_jpn")
    private String titleJpn;

    private String category;
    private String thumb;
    private String uploader;
    private String posted;    // UNIX 时间戳
    private String filecount;
    private Long filesize;
    private Boolean expunged;
    private String rating;
    private List<String> tags; // 所有的标签列表

    // 关键：文档提到如果 Token 错误会返回 error 字段
    private String error;
}