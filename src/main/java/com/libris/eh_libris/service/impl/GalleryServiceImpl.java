package com.libris.eh_libris.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libris.eh_libris.dao.GalleryMapper;
import com.libris.eh_libris.dao.GalleryTagMapMapper;
import com.libris.eh_libris.model.dto.EhApiRequest;
import com.libris.eh_libris.model.dto.EhApiResponse;
import com.libris.eh_libris.model.dto.EhGalleryMetadata;
import com.libris.eh_libris.model.entity.Gallery;
import com.libris.eh_libris.model.entity.GalleryTagMap;
import com.libris.eh_libris.model.entity.Tag;
import com.libris.eh_libris.service.GalleryService;
import com.libris.eh_libris.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//@Override
//public boolean saveGallery(Gallery gallery) {
//    // 调用 MyBatis-Plus 的 insert 方法
//    // rows > 0 表示成功插入
//    return galleryMapper.insert(gallery) > 0;
//}

@Service
// 继承 ServiceImpl<Mapper, Entity> 是 MP 的标准写法，它会自动帮你把 Mapper 注入进去
public class GalleryServiceImpl extends ServiceImpl<GalleryMapper, Gallery> implements GalleryService {

    @Autowired
    private GalleryMapper galleryMapper;

    @Autowired
    private WebClient ehWebClient; // 注入我们在 Config 里配置好的外交官

    @Autowired
    private GalleryTagMapMapper galleryTagMapMapper;

    @Autowired
    private TagService tagService;

    // 注入 Jackson 的工具类，用于手动解析 JSON
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional // 必须加事务！保证画廊和标签要么一起存，要么一起失败
    public boolean fetchAndSave(Long gid, String token) {
        // 1. 构建请求体 (根据文档：method=gdata, gidlist=[[gid, token]])
        EhApiRequest requestBody = EhApiRequest.builder()
                .method("gdata")
                .gidlist(List.of(List.of(gid, token)))
                .namespace(1)
                .build();

        try {
            // 2. 只发送一次请求，并以 String 形式接收
            // 这样无论服务器返回 JSON 还是报错文本，都不会直接抛出异常
            String rawResponse = ehWebClient.post()
                    .uri("/api.php")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println(">>> EH API 原始响应: " + rawResponse);

            //  关键检查：如果响应为空或者不以 "{" 开头（说明不是 JSON 对象）
            if (rawResponse == null || !rawResponse.trim().startsWith("{")) {
                System.err.println(">>> 严重错误：服务器返回了非 JSON 数据。可能原因：IP 被 Ban、参数错误或服务器维护。");
                System.err.println(">>> 返回内容: " + rawResponse);
                return false; // 直接返回失败，不再尝试解析，避免报错
            }

            //  手动将 String 解析为对象
            EhApiResponse response = objectMapper.readValue(rawResponse, EhApiResponse.class);

            // 3. 检查响应并转换存库，即业务处理逻辑
            if (response != null && response.getGmetadata() != null && !response.getGmetadata().isEmpty()) {
                EhGalleryMetadata metadata = response.getGmetadata().get(0);

                // 如果 API 返回了错误（比如 token 不对）
                if (metadata.getError() != null) {
                    System.err.println("API 错误: " + metadata.getError());
                    return false;
                }

                // 4. DTO -> Entity
                Gallery gallery = convertToEntity(metadata);
                // 5. 使用 MP 提供的 saveOrUpdate 存画廊，此时画廊不包含 Tag 信息
                // 它会根据主键 gid 自动判断：数据库里有了就 UPDATE，没有就 INSERT
                this.saveOrUpdate(gallery);

                // 更新事务， 完成 Gallery - GalleryTagMap - Tag 三表联动
                List<String> rawTag = metadata.getTags();
                if (rawTag != null) {
                    for (String fullTag : rawTag) {
                        // 拆分 Tag 字段
                        String[] parts = fullTag.split(":", 2);
                        String ns = parts.length > 1 ? parts[0] : "misc"; // 防止裸标签
                        String name = parts.length > 1 ? parts[1] : parts[0];

                        // a. 维护 Tag 词典， 查不到就存， 查得到获取 tid
                        // 使用 MyBatis-Plus 的 LambdaQuery 保证类型安全
                        Tag tag = tagService.getOne(new LambdaQueryWrapper<Tag>()
                                .eq(Tag::getNamespace, ns)
                                .eq(Tag::getRawName, name));

                        if (tag == null) {
                            tag = Tag.builder().namespace(ns).rawName(name).build();
                            tagService.saveOrUpdate(tag);
                        }

                        // b. 维护中间关系表（ g-t表）
                        GalleryTagMap mapping = new GalleryTagMap(gallery.getGid(), tag.getTid());
                        // 检查是否已经存在
                        Long count = galleryTagMapMapper.selectCount(new LambdaQueryWrapper<GalleryTagMap>()
                                .eq(GalleryTagMap::getGid, mapping.getGid())
                                .eq(GalleryTagMap::getTid, mapping.getTid()));

                        if (count == 0) {
                            galleryTagMapMapper.insert(mapping);
                        }
                    }
                }

                return true;
            }
        } catch (Exception e) {
            System.err.println(">>> 请求处理发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // 内部翻译工具类
    private Gallery convertToEntity(EhGalleryMetadata dto) {
        return Gallery.builder()
                .gid(dto.getGid())
                .token(dto.getToken())
                .title(dto.getTitle())
                .titleJpn(dto.getTitleJpn())
                .category(dto.getCategory())
                .uploader(dto.getUploader())
                .rating(Double.valueOf(dto.getRating()))
                .posted(Long.valueOf(dto.getPosted()))
                .thumb(dto.getThumb())
                .filesize(dto.getFilesize())
                .filecount(Integer.valueOf(dto.getFilecount()))
                .expunged(dto.getExpunged())
                .build();
    }


//    @Override
//public boolean saveGallery(Gallery gallery) {
//    // 调用 MyBatis-Plus 的 insert 方法
//    // rows > 0 表示成功插入
//    return galleryMapper.insert(gallery) > 0;
//}

    /**
     * 分页查询画廊，并预取下一页数据
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 包含当前页和下一页的画廊列表
     */
    @Override
    public List<Gallery> listWithPagination(int pageNum, int pageSize) {
        // 1. 先查询所有画廊
        Page<Gallery> galleryPage = new Page<>(pageNum, pageSize * 2);
        // 执行分页查询 (按 GID 倒序排列，通常最新抓取的在前面)
        this.page(galleryPage, new LambdaQueryWrapper<Gallery>().orderByDesc(Gallery::getGid));

        List<Gallery> galleries = galleryPage.getRecords();
        if (galleries.isEmpty()) {
            return galleries;
        }

        // --- 调试日志 START ---
        System.out.println(">>> 查到了 " + galleries.size() + " 本画廊");
        // --- 调试日志 END ---

        // 2. 查出所有关联关系 (g-t-map)
        // 收集当前查询到的所有 GID，缩小标签关联表的查询范围
        List<Long> gids = galleries.stream().map(Gallery::getGid).collect(Collectors.toList());
        List<GalleryTagMap> relations = galleryTagMapMapper.selectList(
                new LambdaQueryWrapper<GalleryTagMap>().in(GalleryTagMap::getGid, gids));

        // --- 调试日志 START ---
        System.out.println(">>> 查到了 " + relations.size() + " 条标签关联数据");
        // --- 调试日志 END ---

        // 3. 根据 tid 查出所有标签定义
        List<Tag> tags = tagService.list();
        // tid -> Tag, 一次查找确定所有 tid 对应的 Tag
        Map<Integer, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getTid, tag -> tag));
        // 4. 在内存中封装
        // 先把关联关系按 gid 分组：gid -> List<tid>
        Map<Long, List<Integer>> galleryTagMap = relations.stream()
                .collect(Collectors.groupingBy(
                        GalleryTagMap::getGid,
                        Collectors.mapping(GalleryTagMap::getTid, Collectors.toList())
                ));

        // 5. 遍历画廊，把对应标签填入
        for (Gallery gallery : galleries) {
            List<Integer> tids = galleryTagMap.get(gallery.getGid());
            List<String> stringTags = new ArrayList<>();

            if (tids != null) {
                for (Integer tid : tids) {
                    Tag t = tagMap.get(tid);
                    if (t != null) {
                        stringTags.add(t.getNamespace() + ":" + t.getRawName());
                    }
                }
            }
            gallery.setTags(stringTags);
        }

        // --- 调试日志 START ---
        if (!galleries.isEmpty()) {
            System.out.println(">>> 第一本画廊的标签: " + galleries.get(0).getTags());
        }
        // --- 调试日志 END ---

        return galleries;
    }
}