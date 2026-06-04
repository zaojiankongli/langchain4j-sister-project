create table conver_messages
(
    id         varchar(64)                         not null comment '消息 ID（UUID）'
        primary key,
    user_id    varchar(64)                         not null comment '用户 ID',
    role       enum ('user', 'assistant')          not null comment '角色：user 或 assistant',
    is_deleted tinyint   default 0                 null comment '软删除标记',
    created_at timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    contents   json                                not null comment '消息内容列表 JSON'
)
    comment '聊天记录表' row_format = DYNAMIC;

create index idx_user_time
    on conver_messages (user_id asc, created_at desc);

create index idx_user_time_deleted
    on conver_messages (user_id asc, is_deleted asc, created_at desc);

create table conversation_memories
(
    id          bigint auto_increment comment '主键 ID'
        primary key,
    user_id     varchar(64)                         not null comment '用户 ID',
    title       varchar(200)                        null comment '标题（主题词）',
    content     text                                not null comment '内容（对话摘要/日记）',
    mood        varchar(50)                         null comment '心情标签',
    memory_date date                                not null comment '记忆日期（只有年月日）',
    created_at  timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    image_url   varchar(512)                        null comment '关联的图片 URL',
    constraint uk_user_date
        unique (user_id, memory_date),
    constraint uniq_user_date
        unique (user_id, memory_date)
)
    comment '对话记忆表（日记本）' collate = utf8mb4_unicode_ci;

create index idx_user_date
    on conversation_memories (user_id, memory_date);

create table emotion_anchor_events
(
    id               bigint unsigned auto_increment comment '事件 ID'
        primary key,
    user_id          varchar(64)                         not null comment '用户 ID',
    start_time       datetime                            not null comment '事件开始时间',
    end_time         datetime                            null comment '事件结束时间（trigger时为NULL）',
    duration_seconds int                                 null comment '持续时长（秒）',
    peak_pleasure    decimal(5, 4)                       null comment '愉悦度峰值',
    peak_arousal     decimal(5, 4)                       null comment '唤醒度峰值',
    start_pleasure   decimal(5, 4)                       null comment '开始时愉悦度',
    start_arousal    decimal(5, 4)                       null comment '开始时唤醒度',
    end_pleasure     decimal(5, 4)                       null comment '结束时愉悦度',
    end_arousal      decimal(5, 4)                       null comment '结束时唤醒度',
    delta_pleasure   decimal(5, 4)                       null comment '愉悦度变化幅度',
    delta_arousal    decimal(5, 4)                       null comment '唤醒度变化幅度',
    summary          text                                null comment '事件摘要',
    end_type         varchar(20)                         null comment '结束类型：POSITIVE=正向结束，NEGATIVE=负向结束',
    ai_reflection    text                                null comment 'AI 反思/内心独白',
    highlight_traits varchar(500)                        null comment '高亮特质变化摘要：温顺度↑5%，独立性↓5%',
    trigger_reason   varchar(100)                        null comment '触发原因',
    event_title      varchar(200)                        null comment '事件标题',
    end_reason       varchar(100)                        null comment '结束原因',
    created_at       timestamp default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '情绪锚点事件表' collate = utf8mb4_unicode_ci;

create index idx_user_time
    on emotion_anchor_events (user_id asc, start_time desc);

create index idx_open_start_time
    on emotion_anchor_events (end_time, start_time);

create table pending_topics
(
    id               bigint auto_increment comment '主键 ID'
        primary key,
    user_id          varchar(64)                           not null comment '用户 ID',
    anchor_event_id  bigint                                null comment '关联的锚点事件 ID',
    topic_summary    varchar(500)                          not null comment '问题摘要',
    topic_detail     text                                  null comment '问题详情',
    priority         tinyint     default 1                 null comment '优先级：1-低，2-中，3-高',
    user_mentioned   tinyint(1)  default 0                 null comment '用户是否主动提起过：0-否，1-是',
    ai_suggested     tinyint(1)  default 0                 null comment 'AI 是否主动建议过：0-否，1-是',
    status           varchar(20) default 'pending'         null comment '状态：pending/in_progress/resolved/abandoned',
    check_in_count   int         default 0                 null comment '主动关心次数',
    last_check_in_at datetime                              null comment '最近一次主动关心时间',
    created_at       datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at       datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    resolved_at      datetime                              null comment '解决时间'
)
    comment '悬念池表' collate = utf8mb4_unicode_ci;

create index idx_anchor_event
    on pending_topics (anchor_event_id);

create index idx_user_status
    on pending_topics (user_id, status);

create table user_emotions
(
    id               bigint unsigned auto_increment comment '情绪记录 ID'
        primary key,
    user_id          varchar(64)                         not null comment '用户 ID',
    pleasure         decimal(5, 4)                       null comment '愉悦度 [-1.0, +1.0]',
    arousal          decimal(5, 4)                       null comment '唤醒度 [0.0, +1.0]',
    dominance        decimal(5, 4)                       null comment '支配感 [-1.0, +1.0]',
    mood_description varchar(255)                        null comment '情绪描述',
    ai_type          tinyint                             null comment 'AI 身份（冗余）',
    created_at       timestamp default CURRENT_TIMESTAMP null comment '记录时间'
)
    comment '用户情绪表' collate = utf8mb4_unicode_ci;

create index idx_user_time
    on user_emotions (user_id asc, created_at desc);

create table user_interest_tags
(
    id         bigint unsigned auto_increment comment '主键 ID'
        primary key,
    user_id    varchar(64)                          not null comment '用户 ID',
    tag_name   varchar(100)                         not null comment '标签名称',
    is_deleted tinyint(1) default 0                 null comment '软删除标记：0-正常，1-已删除',
    created_at timestamp  default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uniq_user_tag
        unique (user_id, tag_name)
)
    comment '用户趣味标签表（AI 生成）' collate = utf8mb4_unicode_ci;

create index idx_not_deleted
    on user_interest_tags (user_id, is_deleted);

create index idx_user_id
    on user_interest_tags (user_id);

create table user_levels
(
    id            bigint unsigned auto_increment comment '主键 ID'
        primary key,
    user_id       varchar(64)                            not null comment '用户 ID',
    current_level int unsigned default '1'               null comment '当前等级',
    current_exp   int unsigned default '0'               null comment '当前经验值',
    level_up_exp  int unsigned default '100'             null comment '升级所需经验',
    total_exp     int unsigned default '0'               null comment '累计经验值',
    created_at    timestamp    default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at    timestamp    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uniq_user_id
        unique (user_id)
)
    comment '用户等级表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on user_levels (user_id);

create table user_mails
(
    id         varchar(64)                           not null comment '信件 ID'
        primary key,
    user_id    varchar(64)                           not null comment '用户 ID',
    tag        varchar(20) default 'SYSTEM'          null comment '标签：SYSTEM/TIPS/NOTICE',
    subject    varchar(200)                          not null comment '信件标题',
    excerpt    text                                  null comment '信件摘要',
    is_read    tinyint(1)  default 0                 null comment '是否已读',
    created_at timestamp   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '用户信件表' collate = utf8mb4_unicode_ci;

create index idx_user_time
    on user_mails (user_id asc, created_at desc);

create table user_recommendations
(
    id                  bigint auto_increment comment '主键 ID'
        primary key,
    user_id             varchar(64)                             not null comment '用户 ID',
    resource_type       varchar(20)                             not null comment '资源类型：document/video/article',
    title               varchar(500)                            not null comment '资源标题',
    url                 varchar(2000)                           not null comment '资源 URL',
    image_url           varchar(512)                             null comment '资源封面图片 URL',
    description         text                                    null comment '资源描述',
    source              varchar(50)                             null comment '来源：firecrawl/context7',
    relevance_score     decimal(3, 2) default 0.50              null comment '相关性分数（0-1）',
    recommendation_date date                                    not null comment '推荐日期',
    is_clicked          tinyint(1)    default 0                 null comment '是否已点击：0-否，1-是',
    created_at          timestamp     default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '用户资源推荐表' collate = utf8mb4_unicode_ci;

create index idx_recommendation_date
    on user_recommendations (recommendation_date);

create index idx_user_date
    on user_recommendations (user_id, recommendation_date);

create table user_settings
(
    user_id                varchar(64)                           not null comment '用户 ID'
        primary key,
    personality_preset     varchar(30) default 'gentleAndShy'    not null comment '人格预设',
    openness               double      default 0                 not null comment '开放性 [-1,1]',
    conscientiousness      double      default 0                 not null comment '尽责性 [-1,1]',
    extraversion           double      default 0                 not null comment '外向性 [-1,1]',
    agreeableness          double      default 0                 not null comment '宜人性 [-1,1]',
    neuroticism            double      default 0                 not null comment '神经质 [-1,1]',
    sensitivity            double      default 0.5               not null comment '敏感度 [0,1]',
    decay_rate             double      default 0.1               not null comment '衰减率 [0,1]',
    regression_rate        double      default 0.05              not null comment '回归率 [0,1]',
    tts_enabled            tinyint(1)  default 1                 not null comment 'TTS 开关',
    tts_volume             double      default 1                 not null comment '音量 [0,1]',
    tts_speed              double      default 1                 not null comment '语速 [0.5,2.0]',
    proactive_enabled      tinyint(1)  default 1                 not null comment '主动推送开关',
    proactive_interval_min int         default 30                not null comment '推送间隔（分钟）',
    theme_id               varchar(30) default 'default'         not null comment '主题 ID',
    created_at             timestamp   default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at             timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '用户设置表' collate = utf8mb4_unicode_ci;

create table users
(
    id             varchar(64)                        not null comment '用户 ID'
        primary key,
    email          varchar(100)                       not null comment '登录邮箱',
    username       varchar(50)                        null comment '用户名/昵称',
    avatar_url     varchar(255)                       null comment '头像 URL',
    gender         tinyint                            null comment '用户性别：1-男，2-女',
    hobbies        varchar(500)                       null comment '兴趣爱好（逗号分隔，如：音乐，电影，运动）',
    user_profile   text                               null comment '用户画像（AI 生成）',
    ai_type        tinyint                            null comment 'AI 身份类型：1-哥哥，2-妹妹，3-姐姐，4-弟弟，5-青梅，6-竹马',
    last_active_at datetime                           null comment '最后活跃时间',
    created_at     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    birthday       date                               null comment '出生日期',
    constraint email
        unique (email)
)
    comment '用户表' collate = utf8mb4_unicode_ci;

create index idx_ai_type
    on users (ai_type);

create index idx_email
    on users (email);

create index idx_gender
    on users (gender);

