package com.libris.eh_libris.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * 画廊元数据管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gallery") // 告诉 MP：这个类对应数据库里名为 gallery 的表
public class Gallery {

    @TableId(type = IdType.INPUT)// 告诉 MP：gid 是主键，且由我们手动输入
    private Long gid;            // 核心 ID

    private String token;        // 访问令牌
    private String title;        // 英文/主标题

    @JsonProperty("title_jpn") // 映射 API 中的下划线字段
    @TableField("title_jpn") // 映射数据库中的字段名
    private String titleJpn;     // 日语标题

    private String category;     // 分类
    private String thumb;        // 缩略图 URL
    private String uploader;     // 上传者

    private Long posted;         // UNIX 时间戳（发布时间戳），API 返回的是字符串
    private Integer filecount;   // 文件数量
    private Long filesize;       // 总大小（字节）
    private Boolean expunged;
    private Double rating;       // 评分

    // tags 在数据库里通常需要单独存关联表，我们先让 MP 忽略这个字段
    @TableField(exist = false)
    private List<String> tags;   // 标签列表（暂时存在 Model 里，后面存数据库要拆表）

    // 前端 UI 使用：标记是否收藏，不入库
    @TableField(exist = false)
    private Boolean favorite;
}
