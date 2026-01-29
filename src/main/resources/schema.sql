-- 如果表已经存在，先删掉，方便开发阶段反复修改字段
DROP TABLE IF EXISTS gallery;

CREATE TABLE gallery
(
    gid       BIGINT PRIMARY KEY,    -- EH 的画廊 ID
    token     VARCHAR(255) NOT NULL, -- 访问令牌
    title     VARCHAR(500),          -- 英文标题
    title_jpn VARCHAR(500),          -- 日文标题
    category  VARCHAR(50),           -- 分类
    thumb     VARCHAR(500),          -- 缩略图链接
    uploader  VARCHAR(100),          -- 上传者
    posted    VARCHAR(50),           -- 发布时间戳
    filecount VARCHAR(20),           -- 文件数量
    filesize  BIGINT,                -- 文件大小 (Bytes)
    expunged  BOOLEAN,               -- 是否已删除
    rating    VARCHAR(10)            -- 评分 (如 "4.78")
);

-- 1. 标签表：存储原始标签、汉化名称、描述及所属命名空间
CREATE TABLE IF NOT EXISTS tag (
        tid INT AUTO_INCREMENT PRIMARY KEY,
        namespace VARCHAR(50) NOT NULL,   -- 命名空间 (如: female, artist, mixed)
        raw_name VARCHAR(100) NOT NULL,   -- 原始英文名 (如: ponytail)
        name_zh VARCHAR(100),             -- 汉化名 (如: 马尾)
        intro_zh TEXT,                    -- 详细中文介绍 (汉化库里带的描述)
        links TEXT,                       -- 相关链接
        UNIQUE(namespace, raw_name)       -- 确保同一个命名空间下标签不重复
    );

-- 2. 画廊-标签关联表：多对多关系的桥梁
CREATE TABLE IF NOT EXISTS gallery_tag_map (
        gid BIGINT NOT NULL,              -- 画廊 ID
        tid INT NOT NULL,                 -- 标签 ID
        PRIMARY KEY (gid, tid)
    );