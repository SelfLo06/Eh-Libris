package com.libris.eh_libris.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDto {
    private String author;
    private String time;
    private String content;
    private String score;
    private boolean isUploader; // 是否是上传者评论
}