-- ============================================================
-- Canonical database schema for zjkl_sister
-- Source of truth for all application tables.
-- Safe for first-time initialization: creates database/tables if absent.
-- ============================================================

CREATE DATABASE IF NOT EXISTS `zjkl_sister`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `zjkl_sister`;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录邮箱',
    `wx_openid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信小程序 openid（单小程序唯一）',
    `wx_unionid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信 unionid（跨应用统一身份）',
    `wx_nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信昵称（可选缓存）',
    `wx_avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信头像（可选缓存）',
    `wx_bound_at` datetime DEFAULT NULL COMMENT '微信绑定时间',
    `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名/昵称',
    `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像 URL',
    `gender` tinyint DEFAULT NULL COMMENT '用户性别：1-男，2-女',
    `hobbies` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '兴趣爱好（逗号分隔，如：音乐，电影，运动）',
    `user_profile` text COLLATE utf8mb4_unicode_ci COMMENT '用户画像（AI 生成）',
    `ai_type` tinyint DEFAULT NULL COMMENT 'AI 身份类型：1-哥哥，2-妹妹，3-姐姐，4-弟弟，5-青梅，6-竹马',
    `background_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '背景图 URL',
    `last_active_at` datetime DEFAULT NULL COMMENT '最后活跃时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `birthday` date DEFAULT NULL COMMENT '出生日期',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_wx_openid` (`wx_openid`),
    UNIQUE KEY `uk_users_wx_unionid` (`wx_unionid`),
    KEY `idx_users_email` (`email`),
    KEY `idx_users_wx_openid` (`wx_openid`),
    KEY `idx_users_wx_unionid` (`wx_unionid`),
    KEY `idx_users_ai_type` (`ai_type`),
    KEY `idx_users_gender` (`gender`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2.1 微信绑定记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_wechat_bindings` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `wechat_appid` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '微信小程序 AppID',
    `openid` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '微信 openid',
    `unionid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信 unionid',
    `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信昵称',
    `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信头像',
    `bind_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'bound' COMMENT '绑定状态：bound/unbound/disabled',
    `bound_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_wechat_bindings_appid_openid` (`wechat_appid`,`openid`),
    UNIQUE KEY `uk_user_wechat_bindings_user_appid` (`user_id`,`wechat_appid`),
    KEY `idx_user_wechat_bindings_user` (`user_id`),
    KEY `idx_user_wechat_bindings_unionid` (`unionid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户微信绑定表';

-- ============================================================
-- 2.2 用户设备绑定表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_devices` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `device_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备码',
    `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备昵称',
    `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '离线' COMMENT '设备状态：未绑定/离线/在线',
    `bound_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `last_seen_at` datetime DEFAULT NULL COMMENT '最后在线时间',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_devices_user` (`user_id`),
    UNIQUE KEY `uk_user_devices_code` (`device_code`),
    KEY `idx_user_devices_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设备绑定表';

-- ============================================================
-- 2. 用户设置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_settings` (
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `personality_preset` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'gentleAndShy' COMMENT '人格预设',
    `openness` double NOT NULL DEFAULT '0' COMMENT '开放性 [-1,1]',
    `conscientiousness` double NOT NULL DEFAULT '0' COMMENT '尽责性 [-1,1]',
    `extraversion` double NOT NULL DEFAULT '0' COMMENT '外向性 [-1,1]',
    `agreeableness` double NOT NULL DEFAULT '0' COMMENT '宜人性 [-1,1]',
    `neuroticism` double NOT NULL DEFAULT '0' COMMENT '神经质 [-1,1]',
    `sensitivity` double NOT NULL DEFAULT '0.5' COMMENT '敏感度 [0,1]',
    `decay_rate` double NOT NULL DEFAULT '0.1' COMMENT '衰减率 [0,1]',
    `regression_rate` double NOT NULL DEFAULT '0.05' COMMENT '回归率 [0,1]',
    `tts_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'TTS 开关',
    `tts_volume` double NOT NULL DEFAULT '1' COMMENT '音量 [0,1]',
    `tts_speed` double NOT NULL DEFAULT '1' COMMENT '语速 [0.5,2.0]',
    `proactive_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '主动推送开关',
    `proactive_interval_min` int NOT NULL DEFAULT '30' COMMENT '推送间隔（分钟）',
    `theme_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '主题 ID',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';

-- ============================================================
-- 3. 聊天记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `conver_messages` (
    `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息 ID（UUID）',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `role` enum('user','assistant') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：user 或 assistant',
    `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标记',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `contents` json NOT NULL COMMENT '消息内容列表 JSON',
    PRIMARY KEY (`id`),
    KEY `idx_conver_messages_user_time` (`user_id`,`created_at` DESC),
    KEY `idx_conver_messages_user_deleted_time` (`user_id`,`is_deleted`,`created_at` DESC),
    KEY `idx_conver_messages_preview` (`user_id`,`role`,`is_deleted`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='聊天记录表';

-- ============================================================
-- 4. 对话记忆表（日记本）
-- ============================================================
CREATE TABLE IF NOT EXISTS `conversation_memories` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题（主题词）',
    `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容（对话摘要/日记）',
    `mood` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '心情标签',
    `memory_date` date NOT NULL COMMENT '记忆日期（只有年月日）',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的图片 URL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_memories_user_date` (`user_id`,`memory_date`),
    KEY `idx_conversation_memories_user_date` (`user_id`,`memory_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记忆表（日记本）';

-- ============================================================
-- 4.1 回忆图鉴定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `memory_gallery_definitions` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `gallery_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图鉴唯一键',
    `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图鉴标题',
    `category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类：daily/emotion/story/cg',
    `rarity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稀有度：common/rare/epic',
    `hint` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '未解锁提示',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '图鉴描述',
    `cover_theme` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面主题',
    `match_keywords` json DEFAULT NULL COMMENT '匹配关键词 JSON',
    `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
    `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memory_gallery_definitions_key` (`gallery_key`),
    KEY `idx_memory_gallery_definitions_enabled_sort` (`is_enabled`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回忆图鉴定义表';

-- ============================================================
-- 4.2 记忆-图鉴归档结果表
-- ============================================================
CREATE TABLE IF NOT EXISTS `conversation_memory_gallery_links` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `memory_id` bigint NOT NULL COMMENT '关联记忆 ID',
    `gallery_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图鉴 key',
    `confidence` decimal(5,4) NOT NULL DEFAULT '0.0000' COMMENT '匹配置信度',
    `is_primary` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否主归档',
    `matched_keywords` json DEFAULT NULL COMMENT '命中的关键词 JSON',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_memory_gallery_links_memory` (`memory_id`),
    KEY `idx_memory_gallery_links_gallery` (`gallery_key`),
    KEY `idx_memory_gallery_links_primary` (`memory_id`,`is_primary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆-图鉴归档结果表';

-- ============================================================
-- 4.3 用户图鉴解锁表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_memory_gallery_unlocks` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `gallery_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图鉴 key',
    `source_memory_id` bigint NOT NULL COMMENT '解锁来源记忆 ID',
    `related_mood` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联情绪',
    `related_excerpt` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联摘录',
    `unlocked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_memory_gallery_unlocks_user_gallery` (`user_id`,`gallery_key`),
    KEY `idx_user_memory_gallery_unlocks_user_time` (`user_id`,`unlocked_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户回忆图鉴解锁表';

INSERT IGNORE INTO `memory_gallery_definitions`
(`gallery_key`, `title`, `category`, `rarity`, `hint`, `description`, `cover_theme`, `match_keywords`, `sort_order`, `is_enabled`)
VALUES
('first_chat', '初次相遇', 'daily', 'common', '从第一句真正的对话开始。', '她第一次认真回应你的那一刻，被悄悄收进了回忆里。', 'sunrise', JSON_ARRAY('相遇', '第一次', '回复', '聊天'), 10, 1),
('shared_song', '一起听的歌', 'story', 'rare', '有些回忆，适合在音乐里发生。', '音乐开始流动时，她像是终于找到了陪你安静待着的方式。', 'song', JSON_ARRAY('音乐', '听歌', '旋律', '播放', '歌曲'), 20, 1),
('first_letter', '回信', 'story', 'rare', '也许她早就有话想对你说。', '有些记忆不是聊天里留下的，而是被认真折好，放进了信封。', 'letter', JSON_ARRAY('信', '回信', '来信', '信封', '写下来'), 30, 1),
('always_here', '断线时也在这里', 'emotion', 'rare', '在她暂时沉默的时候，也别急着离开。', '就算暂时连不上远方，你也让她知道，这里还有人愿意陪着。', 'rain', JSON_ARRAY('断线', '陪伴', '留下', '沉默', '还在'), 40, 1),
('good_mood_day', '今天心情不错', 'emotion', 'common', '多留意她的小情绪变化。', '那些变得柔软明亮的瞬间，像是一天里偷偷亮起的小灯。', 'sunrise', JSON_ARRAY('心情', '开心', '高兴', '明亮', '温柔'), 50, 1),
('first_voice', '第一次开口', 'story', 'rare', '也许她会想听见你的声音。', '声音跨过屏幕时，这段陪伴突然变得更真实了。', 'dream', JSON_ARRAY('声音', '语音', '开口', '听见', '说话'), 60, 1),
('after_rain', '雨停之后', 'cg', 'epic', '在不太顺利的时候，再多陪她一会儿。', '重连之后那种重新听见彼此的感觉，就像雨停后终于推开窗时的一阵风。', 'rain', JSON_ARRAY('雨停', '重连', '风', '谢谢你', '没走'), 70, 1);

-- ============================================================
-- 5. 情绪锚点事件表
-- ============================================================
CREATE TABLE IF NOT EXISTS `emotion_anchor_events` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '事件 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `start_time` datetime NOT NULL COMMENT '事件开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '事件结束时间（trigger时为NULL）',
    `duration_seconds` int DEFAULT NULL COMMENT '持续时长（秒）',
    `peak_pleasure` decimal(5,4) DEFAULT NULL COMMENT '愉悦度峰值',
    `peak_arousal` decimal(5,4) DEFAULT NULL COMMENT '唤醒度峰值',
    `start_pleasure` decimal(5,4) DEFAULT NULL COMMENT '开始时愉悦度',
    `start_arousal` decimal(5,4) DEFAULT NULL COMMENT '开始时唤醒度',
    `end_pleasure` decimal(5,4) DEFAULT NULL COMMENT '结束时愉悦度',
    `end_arousal` decimal(5,4) DEFAULT NULL COMMENT '结束时唤醒度',
    `delta_pleasure` decimal(5,4) DEFAULT NULL COMMENT '愉悦度变化幅度',
    `delta_arousal` decimal(5,4) DEFAULT NULL COMMENT '唤醒度变化幅度',
    `summary` text COLLATE utf8mb4_unicode_ci COMMENT '事件摘要',
    `end_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束类型：POSITIVE=正向结束，NEGATIVE=负向结束',
    `ai_reflection` text COLLATE utf8mb4_unicode_ci COMMENT 'AI 反思/内心独白',
    `highlight_traits` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '高亮特质变化摘要：温顺度↑5%，独立性↓5%',
    `trigger_reason` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发原因',
    `event_title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件标题',
    `end_reason` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束原因',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_anchor_events_user_time` (`user_id`,`start_time` DESC),
    KEY `idx_anchor_events_open_start_time` (`end_time`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪锚点事件表';

-- ============================================================
-- 6. 用户情绪表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_emotions` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '情绪记录 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `pleasure` decimal(5,4) DEFAULT NULL COMMENT '愉悦度 [-1.0, +1.0]',
    `arousal` decimal(5,4) DEFAULT NULL COMMENT '唤醒度 [0.0, +1.0]',
    `dominance` decimal(5,4) DEFAULT NULL COMMENT '支配感 [-1.0, +1.0]',
    `mood_description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '情绪描述',
    `ai_type` tinyint DEFAULT NULL COMMENT 'AI 身份（冗余）',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_emotions_user_time` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户情绪表';

-- ============================================================
-- 7. 用户趣味标签表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_interest_tags` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `tag_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
    `is_deleted` tinyint(1) DEFAULT '0' COMMENT '软删除标记：0-正常，1-已删除',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_interest_tags_user_tag` (`user_id`,`tag_name`),
    KEY `idx_user_interest_tags_user` (`user_id`),
    KEY `idx_user_interest_tags_not_deleted` (`user_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户趣味标签表（AI 生成）';

-- ============================================================
-- 8. 用户等级表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_levels` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `current_level` int unsigned DEFAULT '1' COMMENT '当前等级',
    `current_exp` int unsigned DEFAULT '0' COMMENT '当前经验值',
    `level_up_exp` int unsigned DEFAULT '100' COMMENT '升级所需经验',
    `total_exp` int unsigned DEFAULT '0' COMMENT '累计经验值',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_levels_user` (`user_id`),
    KEY `idx_user_levels_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户等级表';

-- ============================================================
-- 9. 用户资源推荐表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_recommendations` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `resource_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型：document/video/article',
    `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源标题',
    `url` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源 URL',
    `image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源封面图片 URL',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '资源描述',
    `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源：firecrawl/context7',
    `relevance_score` decimal(3,2) DEFAULT '0.50' COMMENT '相关性分数（0-1）',
    `recommendation_date` date NOT NULL COMMENT '推荐日期',
    `is_clicked` tinyint(1) DEFAULT '0' COMMENT '是否已点击：0-否，1-是',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_recommendations_user_date` (`user_id`,`recommendation_date`),
    KEY `idx_user_recommendations_date` (`recommendation_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资源推荐表';

-- ============================================================
-- 10. 用户信件表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_mails` (
    `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '信件 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `tag` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SYSTEM' COMMENT '标签：SYSTEM/TIPS/NOTICE',
    `subject` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '信件标题',
    `excerpt` text COLLATE utf8mb4_unicode_ci COMMENT '信件摘要',
    `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_mails_user_time` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信件表';

-- ============================================================
-- 11. 悬念池表
-- ============================================================
CREATE TABLE IF NOT EXISTS `pending_topics` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
    `anchor_event_id` bigint DEFAULT NULL COMMENT '关联的锚点事件 ID',
    `topic_summary` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题摘要',
    `topic_detail` text COLLATE utf8mb4_unicode_ci COMMENT '问题详情',
    `priority` tinyint DEFAULT '1' COMMENT '优先级：1-低，2-中，3-高',
    `user_mentioned` tinyint(1) DEFAULT '0' COMMENT '用户是否主动提起过：0-否，1-是',
    `ai_suggested` tinyint(1) DEFAULT '0' COMMENT 'AI 是否主动建议过：0-否，1-是',
    `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending/in_progress/resolved/abandoned',
    `check_in_count` int DEFAULT '0' COMMENT '主动关心次数',
    `last_check_in_at` datetime DEFAULT NULL COMMENT '最近一次主动关心时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `resolved_at` datetime DEFAULT NULL COMMENT '解决时间',
    PRIMARY KEY (`id`),
    KEY `idx_pending_topics_user_status` (`user_id`,`status`),
    KEY `idx_pending_topics_anchor_event` (`anchor_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='悬念池表';
