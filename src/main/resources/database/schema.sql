USE zjkl_sister;

CREATE TABLE `conver_messages` (
                                   `id` varchar(64) NOT NULL COMMENT '消息 ID（UUID）',
                                   `user_id` varchar(12) NOT NULL COMMENT '用户 ID',
                                   `role` enum('user','assistant') NOT NULL COMMENT '角色：user 或 assistant',
                                   `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标记',
                                   `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `contents` json NOT NULL COMMENT '消息内容列表 JSON',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_user_time` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='聊天记录表'

CREATE TABLE `conversation_memories` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                                         `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                                         `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题（主题词）',
                                         `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容（对话摘要/日记）',
                                         `mood` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '心情标签',
                                         `memory_date` date NOT NULL COMMENT '记忆日期（只有年月日）',
                                         `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的图片 URL',
                                          PRIMARY KEY (`id`),
                                          UNIQUE KEY `uniq_user_date` (`user_id`,`memory_date`),
                                          KEY `idx_user_date` (`user_id`,`memory_date`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记忆表（日记本）'

CREATE TABLE `emotion_anchor_events` (
                                         `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '事件 ID',
                                         `user_id` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
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
                                         `ai_reflection` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'AI 反思/内心独白',
                                         `highlight_traits` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '高亮特质变化摘要：温顺度↑5%，独立性↓5%',
                                         `trigger_reason` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发原因',
                                         `event_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件标题',
                                         `end_reason` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束原因',
                                         `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_user_time` (`user_id`,`start_time` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪锚点事件表'

CREATE TABLE `pending_topics` (
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
                                  KEY `idx_user_status` (`user_id`,`status`),
                                  KEY `idx_anchor_event` (`anchor_event_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='悬念池表'

CREATE TABLE `user_emotions` (
                                 `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '情绪记录 ID',
                                 `user_id` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                                 `pleasure` decimal(5,4) DEFAULT NULL COMMENT '愉悦度 [-1.0, +1.0]',
                                 `arousal` decimal(5,4) DEFAULT NULL COMMENT '唤醒度 [0.0, +1.0]',
                                 `dominance` decimal(5,4) DEFAULT NULL COMMENT '支配感 [-1.0, +1.0]',
                                 `mood_description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '情绪描述',
                                 `ai_type` tinyint DEFAULT NULL COMMENT 'AI 身份（冗余）',
                                 `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_user_time` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户情绪表'

CREATE TABLE `user_interest_tags` (
                                      `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                                      `user_id` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                                      `tag_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
                                      `is_deleted` tinyint(1) DEFAULT '0' COMMENT '软删除标记：0-正常，1-已删除',
                                      `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uniq_user_tag` (`user_id`,`tag_name`),
                                      KEY `idx_user_id` (`user_id`),
                                      KEY `idx_not_deleted` (`user_id`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户趣味标签表（AI 生成）'

CREATE TABLE `user_levels` (
                               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                               `user_id` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                               `current_level` int unsigned DEFAULT '1' COMMENT '当前等级',
                               `current_exp` int unsigned DEFAULT '0' COMMENT '当前经验值',
                               `level_up_exp` int unsigned DEFAULT '100' COMMENT '升级所需经验',
                               `total_exp` int unsigned DEFAULT '0' COMMENT '累计经验值',
                               `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uniq_user_id` (`user_id`),
                               KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户等级表'

CREATE TABLE `user_recommendations` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                                        `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                                        `resource_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型：document/video/article',
                                        `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源标题',
                                        `url` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源 URL',
                                        `description` text COLLATE utf8mb4_unicode_ci COMMENT '资源描述',
                                        `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源：firecrawl/context7',
                                        `relevance_score` decimal(3,2) DEFAULT '0.50' COMMENT '相关性分数（0-1）',
                                        `recommendation_date` date NOT NULL COMMENT '推荐日期',
                                        `is_clicked` tinyint(1) DEFAULT '0' COMMENT '是否已点击：0-否，1-是',
                                        `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_user_date` (`user_id`,`recommendation_date`),
                                        KEY `idx_recommendation_date` (`recommendation_date`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资源推荐表'

CREATE TABLE `users` (
                         `id` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
                         `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录邮箱',
                         `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名/昵称',
                         `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像 URL',
                         `gender` tinyint DEFAULT NULL COMMENT '用户性别：1-男，2-女',
                         `hobbies` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '兴趣爱好（逗号分隔，如：音乐，电影，运动）',
                         `user_profile` text COLLATE utf8mb4_unicode_ci COMMENT '用户画像（AI 生成）',
                         `ai_type` tinyint DEFAULT NULL COMMENT 'AI 身份类型：1-哥哥，2-妹妹，3-姐姐，4-弟弟，5-青梅，6-竹马',
                         `last_active_at` datetime DEFAULT NULL COMMENT '最后活跃时间',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         `birthday` date DEFAULT NULL COMMENT '出生日期',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `email` (`email`),
                         KEY `idx_email` (`email`),
                         KEY `idx_ai_type` (`ai_type`),
                         KEY `idx_gender` (`gender`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表'

