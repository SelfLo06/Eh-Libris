package com.libris.eh_libris.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libris.eh_libris.dao.GalleryMapper;
import com.libris.eh_libris.dao.GalleryTagMapMapper;
import com.libris.eh_libris.model.dto.EhApiRequest;
import com.libris.eh_libris.model.dto.EhApiResponse;
import com.libris.eh_libris.model.dto.EhGalleryMetadata;
import com.libris.eh_libris.model.dto.RemoteFavoriteItemDto;
import com.libris.eh_libris.model.entity.Gallery;
import com.libris.eh_libris.model.entity.FavoriteGallery;
import com.libris.eh_libris.model.entity.GalleryTagMap;
import com.libris.eh_libris.model.entity.Tag;
import com.libris.eh_libris.service.FavoriteGalleryService;
import com.libris.eh_libris.service.GalleryService;
import com.libris.eh_libris.service.TagService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
// 继承 ServiceImpl<Mapper, Entity> 是 MP 的标准写法，它会自动帮你把 Mapper 注入进去
public class GalleryServiceImpl extends ServiceImpl<GalleryMapper, Gallery> implements GalleryService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Autowired
    private WebClient ehWebClient; // 注入我们在 Config 里配置好的外交官

    @Autowired
    private GalleryTagMapMapper galleryTagMapMapper;

    @Autowired
    private TagService tagService;

    @Autowired
    private FavoriteGalleryService favoriteGalleryService;

    // 注入 Jackson 的工具类，用于手动解析 JSON
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${eh.cookie.memberId:}")
    private String memberId;

    @Value("${eh.cookie.passHash:}")
    private String passHash;

    @Value("${eh.cookie.uconfig:}")
    private String uconfig;

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

            //System.out.println(">>> EH API 原始响应: " + rawResponse);

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

                // 更新事务，完成 Gallery - Tag - GalleryTagMap 三表联动
                syncGalleryTags(gallery.getGid(), metadata.getTags());

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

        // 2. 查出所有关联关系 (g-t-map)
        // 收集当前查询到的所有 GID，缩小标签关联表的查询范围
        List<Long> gids = galleries.stream().map(Gallery::getGid).collect(Collectors.toList());
        List<GalleryTagMap> relations = galleryTagMapMapper.selectList(
                new LambdaQueryWrapper<GalleryTagMap>().in(GalleryTagMap::getGid, gids));

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

        return galleries;
    }

    @Override
    public List<Gallery> listLatestTopRated(int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);

        // 最新 + 满分过滤：减少低质量噪音画廊
        Page<Gallery> galleryPage = new Page<>(safePageNum, safePageSize);
        this.page(galleryPage, new LambdaQueryWrapper<Gallery>()
                .eq(Gallery::getRating, 5.0d)
                .orderByDesc(Gallery::getPosted)
                .orderByDesc(Gallery::getGid));

        List<Gallery> galleries = galleryPage.getRecords();
        if (galleries.isEmpty()) {
            return galleries;
        }

        fillTagsForGalleries(galleries);
        return galleries;
    }

    @Override
    public Gallery getDetailWithTags(Long gid, String token) {
        Gallery gallery = this.getById(gid);
        if (gallery == null || shouldHydrateGallery(gallery)) {
            Gallery hydrated = hydrateGalleryFromRemote(gid, token);
            if (hydrated != null) {
                gallery = hydrated;
            } else if (gallery == null) {
                return null;
            }
        }
        favoriteGalleryService.touchFavorite(gid, token);
        return enrichGalleryForResponse(gallery, false);
    }

    @Override
    public List<Gallery> listFavoriteGalleries() {
        List<FavoriteGallery> favorites = favoriteGalleryService.list(
                new LambdaQueryWrapper<FavoriteGallery>().orderByDesc(FavoriteGallery::getUpdatedAt)
        );
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> favoriteGids = favorites.stream()
                .map(FavoriteGallery::getGid)
                .collect(Collectors.toList());

        List<Gallery> galleries = this.list(new LambdaQueryWrapper<Gallery>().in(Gallery::getGid, favoriteGids));
        Map<Long, Gallery> galleryMap = galleries.stream()
                .collect(Collectors.toMap(Gallery::getGid, g -> g));

        List<Gallery> ordered = new ArrayList<>();
        for (FavoriteGallery favorite : favorites) {
            Long gid = favorite.getGid();
            Gallery g = galleryMap.get(gid);
            if (g == null) {
                // 收藏存在但本地详情尚未落库时，返回轻量占位，避免前端空列表
                g = Gallery.builder()
                        .gid(gid)
                        .token(favorite.getToken())
                        .title("")
                        .titleJpn("")
                        .category("")
                        .thumb("")
                        .uploader("")
                        .posted(0L)
                        .filecount(0)
                        .filesize(0L)
                        .expunged(false)
                        .rating(0.0)
                        .build();
                g.setTags(new ArrayList<>());
            }
            if (g != null) {
                ordered.add(g);
            }
        }

        fillTagsForGalleries(ordered);
        ordered.forEach(g -> g.setFavorite(true));
        return ordered;
    }

    @Override
    public List<Gallery> listRemoteFavoriteGalleries(int page, int limit) {
        List<Gallery> result = new ArrayList<>();
        List<RemoteFavoriteItemDto> items = favoriteGalleryService.listRemoteFavoriteItems(page, limit);
        for (RemoteFavoriteItemDto item : items) {
            Long gid = item.getGid();
            String token = item.getToken();
            if (gid == null || token == null || token.isBlank()) {
                continue;
            }

            Gallery local = this.getById(gid);
            if (local == null || shouldHydrateGallery(local)) {
                Gallery hydrated = hydrateGalleryFromRemote(gid, token);
                if (hydrated != null) {
                    local = hydrated;
                }
            }

            // 命中本地完整数据，直接返回并补 tags
            if (local != null && !isSparseGallery(local)) {
                result.add(enrichGalleryForResponse(local, true));
                continue;
            }

            Gallery lightweight = Gallery.builder()
                    .gid(gid)
                    .token(token)
                    .title(item.getTitle() == null ? "" : item.getTitle())
                    .titleJpn("")
                    .category(item.getCategory() == null ? "" : item.getCategory())
                    .thumb(item.getThumb() == null ? "" : item.getThumb())
                    .uploader("")
                    .posted(0L)
                    .filecount(item.getFilecount() == null ? 0 : item.getFilecount())
                    .filesize(0L)
                    .expunged(false)
                    .rating(item.getRating() == null ? 0.0 : item.getRating())
                    .build();
            lightweight.setFavorite(true);
            lightweight.setTags(new ArrayList<>());
            result.add(lightweight);
        }
        return result;
    }

    private Gallery enrichGalleryForResponse(Gallery gallery, boolean favorite) {
        if (gallery == null) {
            return null;
        }
        List<Gallery> wrapped = new ArrayList<>();
        wrapped.add(gallery);
        fillTagsForGalleries(wrapped);
        Gallery enriched = wrapped.get(0);
        if (favorite) {
            enriched.setFavorite(true);
        }
        return enriched;
    }

    private boolean shouldHydrateGallery(Gallery gallery) {
        if (gallery == null) {
            return true;
        }
        if (isSparseGallery(gallery)) {
            return true;
        }
        if (gallery.getFilecount() == null || gallery.getFilecount() <= 0) {
            return true;
        }
        Long mappingCount = galleryTagMapMapper.selectCount(
                new LambdaQueryWrapper<GalleryTagMap>().eq(GalleryTagMap::getGid, gallery.getGid())
        );
        return mappingCount == null || mappingCount == 0;
    }

    private Gallery hydrateGalleryFromRemote(Long gid, String token) {
        boolean fetched = fetchAndSave(gid, token);
        if (fetched) {
            Gallery saved = this.getById(gid);
            if (saved != null) {
                return saved;
            }
        }

        Gallery fallback = fetchFromGalleryPage(gid, token);
        if (fallback == null) {
            return null;
        }
        this.saveOrUpdate(fallback);
        if (fallback.getTags() != null && !fallback.getTags().isEmpty()) {
            syncGalleryTags(gid, fallback.getTags());
        }
        return this.getById(gid);
    }

    // gdata 不可达时的详情兜底：直接解析画廊详情页基础信息
    private Gallery fetchFromGalleryPage(Long gid, String token) {
        String url = "https://e-hentai.org/g/" + gid + "/" + token + "/";
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .cookie("nw", "1")
                    .cookie("ipb_member_id", memberId)
                    .cookie("ipb_pass_hash", passHash)
                    .cookie("uconfig", uconfig)
                    .timeout(30000)
                    .get();

            String title = textOrEmpty(doc.select("#gn").first());
            String titleJpn = textOrEmpty(doc.select("#gj").first());
            if (title.isBlank() && titleJpn.isBlank()) {
                Element anyTitle = doc.select(".gm .gj, .gm .gn, h1").first();
                if (anyTitle != null) {
                    title = anyTitle.text();
                }
            }

            String thumb = "";
            Element imgEl = doc.select("#gd1 img").first();
            if (imgEl != null) {
                thumb = imgEl.attr("src");
            }

            String category = "";
            for (Element tr : doc.select("#gdd tr")) {
                Element key = tr.select("td.gdt1").first();
                Element val = tr.select("td.gdt2").first();
                if (key == null || val == null) {
                    continue;
                }
                String keyText = key.text().trim();
                if ("Category:".equalsIgnoreCase(keyText)) {
                    category = val.text().trim();
                    break;
                }
            }

            String uploader = textOrEmpty(doc.select("#gdn a").first());
            long posted = 0L;
            int filecount = 0;
            long filesize = 0L;
            double rating = 0.0;

            for (Element tr : doc.select("#gdd tr")) {
                Element key = tr.select("td.gdt1").first();
                Element val = tr.select("td.gdt2").first();
                if (key == null || val == null) {
                    continue;
                }
                String k = key.text().trim();
                String v = val.text().trim();
                if ("Posted:".equalsIgnoreCase(k)) {
                    posted = parsePostedToEpoch(v);
                } else if ("Length:".equalsIgnoreCase(k)) {
                    Matcher m = Pattern.compile("(\\d+)").matcher(v);
                    if (m.find()) {
                        filecount = Integer.parseInt(m.group(1));
                    }
                } else if ("File Size:".equalsIgnoreCase(k)) {
                    filesize = parseFileSizeToBytes(v);
                }
            }

            Element ratingEl = doc.select("#rating_label").first();
            if (ratingEl != null) {
                Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(ratingEl.text());
                if (m.find()) {
                    rating = Double.parseDouble(m.group(1));
                }
            }

            Gallery gallery = Gallery.builder()
                    .gid(gid)
                    .token(token)
                    .title(title)
                    .titleJpn(titleJpn)
                    .category(category)
                    .thumb(thumb)
                    .uploader(uploader)
                    .posted(posted)
                    .filecount(filecount)
                    .filesize(filesize)
                    .expunged(false)
                    .rating(rating)
                    .build();

            gallery.setTags(extractTagsFromDetailDoc(doc));
            return gallery;
        } catch (Exception e) {
            System.err.println(">>> 详情页兜底抓取失败: " + e.getMessage());
            return null;
        }
    }

    private void syncGalleryTags(Long gid, List<String> fullTags) {
        List<GalleryTagMap> existingMappings = galleryTagMapMapper.selectList(
                new LambdaQueryWrapper<GalleryTagMap>().eq(GalleryTagMap::getGid, gid)
        );
        if (fullTags == null || fullTags.isEmpty()) {
            for (GalleryTagMap existing : existingMappings) {
                galleryTagMapMapper.delete(new LambdaQueryWrapper<GalleryTagMap>()
                        .eq(GalleryTagMap::getGid, gid)
                        .eq(GalleryTagMap::getTid, existing.getTid()));
            }
            return;
        }

        Set<Integer> targetTidSet = new LinkedHashSet<>();
        for (String fullTag : fullTags) {
            if (fullTag == null || fullTag.isBlank()) {
                continue;
            }
            String[] parts = fullTag.split(":", 2);
            String ns = parts.length > 1 ? parts[0].trim() : "misc";
            String name = parts.length > 1 ? parts[1].trim() : parts[0].trim();
            if (ns.isBlank() || name.isBlank()) {
                continue;
            }

            Tag tag = tagService.getOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getNamespace, ns)
                    .eq(Tag::getRawName, name));
            if (tag == null) {
                try {
                    tagService.save(Tag.builder().namespace(ns).rawName(name).build());
                } catch (DuplicateKeyException ignored) {
                    // 并发场景下可能已被其他线程插入，回查即可
                }
                tag = tagService.getOne(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getNamespace, ns)
                        .eq(Tag::getRawName, name));
            }
            if (tag == null || tag.getTid() == null) {
                continue;
            }
            targetTidSet.add(tag.getTid());
            Long count = galleryTagMapMapper.selectCount(
                    new LambdaQueryWrapper<GalleryTagMap>()
                            .eq(GalleryTagMap::getGid, gid)
                            .eq(GalleryTagMap::getTid, tag.getTid())
            );
            if (count == null || count == 0) {
                galleryTagMapMapper.insert(new GalleryTagMap(gid, tag.getTid()));
            }
        }

        // 删除目标集合外的旧关系，保持三表关系可追踪且一致
        for (GalleryTagMap existing : existingMappings) {
            if (!targetTidSet.contains(existing.getTid())) {
                galleryTagMapMapper.delete(new LambdaQueryWrapper<GalleryTagMap>()
                        .eq(GalleryTagMap::getGid, gid)
                        .eq(GalleryTagMap::getTid, existing.getTid()));
            }
        }
    }

    private String textOrEmpty(Element element) {
        return element == null ? "" : element.text().trim();
    }

    private long parsePostedToEpoch(String postedText) {
        if (postedText == null || postedText.isBlank()) {
            return 0L;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            Date date = sdf.parse(postedText);
            return date == null ? 0L : date.getTime() / 1000L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long parseFileSizeToBytes(String fileSizeText) {
        if (fileSizeText == null || fileSizeText.isBlank()) {
            return 0L;
        }
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(B|KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE).matcher(fileSizeText);
        if (!m.find()) {
            return 0L;
        }
        double val = Double.parseDouble(m.group(1));
        String unit = m.group(2).toUpperCase(Locale.ROOT);
        double factor;
        switch (unit) {
            case "KB":
                factor = 1024d;
                break;
            case "MB":
                factor = 1024d * 1024d;
                break;
            case "GB":
                factor = 1024d * 1024d * 1024d;
                break;
            case "TB":
                factor = 1024d * 1024d * 1024d * 1024d;
                break;
            default:
                factor = 1d;
                break;
        }
        return (long) (val * factor);
    }

    private List<String> extractTagsFromDetailDoc(Document doc) {
        Set<String> tagSet = new LinkedHashSet<>();
        for (Element row : doc.select("#taglist tr")) {
            Element nsEl = row.select("td.tc").first();
            String namespace = nsEl == null ? "misc" : nsEl.text().trim().replace(":", "").toLowerCase(Locale.ROOT);
            if (namespace.isBlank()) {
                namespace = "misc";
            }

            List<Element> tagEls = row.select("td div[id^=ta_], td a[id^=ta_], td .gt, td a");
            for (Element tagEl : tagEls) {
                String raw = tagEl.text().trim();
                if (raw.isBlank()) {
                    continue;
                }
                String full = raw.contains(":") ? raw : (namespace + ":" + raw);
                tagSet.add(full);
            }
        }
        return new ArrayList<>(tagSet);
    }

    private void fillTagsForGalleries(List<Gallery> galleries) {
        if (galleries == null || galleries.isEmpty()) {
            return;
        }

        List<Long> gids = galleries.stream().map(Gallery::getGid).collect(Collectors.toList());
        List<GalleryTagMap> relations = galleryTagMapMapper.selectList(
                new LambdaQueryWrapper<GalleryTagMap>().in(GalleryTagMap::getGid, gids));

        List<Tag> tags = tagService.list();
        Map<Integer, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getTid, tag -> tag));

        Map<Long, List<Integer>> galleryTagMap = relations.stream()
                .collect(Collectors.groupingBy(
                        GalleryTagMap::getGid,
                        Collectors.mapping(GalleryTagMap::getTid, Collectors.toList())
                ));

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
    }

    private boolean isSparseGallery(Gallery gallery) {
        if (gallery == null) {
            return true;
        }
        boolean noTitle = gallery.getTitle() == null || gallery.getTitle().isBlank();
        boolean noThumb = gallery.getThumb() == null || gallery.getThumb().isBlank();
        boolean noCategory = gallery.getCategory() == null || gallery.getCategory().isBlank();
        return noTitle || noThumb || noCategory;
    }

}
