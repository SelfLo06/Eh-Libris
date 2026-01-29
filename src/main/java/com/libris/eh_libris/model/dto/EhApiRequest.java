package com.libris.eh_libris.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EhApiRequest {
    // 文档要求：必须是 "gdata"
    private String method;

    // 文档要求：[[gid, "token"], ...] 这种嵌套列表格式
    private List<List<Object>> gidlist;

    // 文档要求：1 表示包含命名空间标签
    private Integer namespace;
}