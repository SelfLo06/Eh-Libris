package com.libris.eh_libris.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Tag {
    @TableId(type = IdType.AUTO)
    private Integer tid;
    private String namespace;
    private String rawName;
    private String nameZh;
    private String introZh;
}