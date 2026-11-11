-- auto-generated definition
create table t_ai_customer_service_file_storage
(
    id              bigserial
        primary key,
    file_name       varchar(160)                        not null,
    file_md5        varchar(32)                         not null,
    file_path       varchar(500)                        not null,
    file_size       bigint                              not null,
    total_chunks    integer                             not null,
    uploaded_chunks integer   default 0                 not null,
    status          smallint  default 0,
    remark          varchar(200),
    create_time     timestamp default CURRENT_TIMESTAMP not null,
    update_time     timestamp default CURRENT_TIMESTAMP not null
);

comment on table t_ai_customer_service_file_storage is 'AI客服文件存储表';

comment on column t_ai_customer_service_file_storage.id is '主键ID';

comment on column t_ai_customer_service_file_storage.file_name is '原始文件名';

comment on column t_ai_customer_service_file_storage.file_md5 is '文件MD5值，用于秒传和去重';

comment on column t_ai_customer_service_file_storage.file_path is '文件存储路径';

comment on column t_ai_customer_service_file_storage.file_size is '文件大小（字节）';

comment on column t_ai_customer_service_file_storage.total_chunks is '总分片数';

comment on column t_ai_customer_service_file_storage.uploaded_chunks is '已上传分片数';

comment on column t_ai_customer_service_file_storage.status is '处理状态：0-上传中 1-待处理 2-向量化中 3-已完成 4-失败';

comment on column t_ai_customer_service_file_storage.remark is '备注';

comment on column t_ai_customer_service_file_storage.create_time is '创建时间';

comment on column t_ai_customer_service_file_storage.update_time is '更新时间';

alter table t_ai_customer_service_file_storage
    owner to postgres;

create unique index uk_file_md5
    on t_ai_customer_service_file_storage (file_md5);

comment on index uk_file_md5 is '文件MD5唯一索引，用于秒传去重';

create index idx_status
    on t_ai_customer_service_file_storage (status);

comment on index idx_status is '状态索引，用于快速查询不同状态的文件';

create index idx_create_time
    on t_ai_customer_service_file_storage (create_time);

comment on index idx_create_time is '创建时间索引，用于按时间范围查询';

create index idx_file_name
    on t_ai_customer_service_file_storage (file_name);

comment on index idx_file_name is '文件名索引，支持文件名搜索';


-- auto-generated definition
create table t_ai_customer_service_md_storage
(
    id                 bigserial
        primary key,
    original_file_name varchar(160) not null,
    new_file_name      varchar(160) not null,
    file_path          varchar(500) not null,
    file_size          bigint       not null,
    status             smallint  default 0,
    remark             varchar(200),
    create_time        timestamp default CURRENT_TIMESTAMP,
    update_time        timestamp default CURRENT_TIMESTAMP
);

comment on table t_ai_customer_service_md_storage is '问答 Markdown 文件存储表';

comment on column t_ai_customer_service_md_storage.id is '主键ID';

comment on column t_ai_customer_service_md_storage.original_file_name is '原始文件名称';

comment on column t_ai_customer_service_md_storage.new_file_name is '新命名文件名称（防止名称相同导致覆盖）';

comment on column t_ai_customer_service_md_storage.file_path is '文件存储路径';

comment on column t_ai_customer_service_md_storage.file_size is '文件大小(字节)';

comment on column t_ai_customer_service_md_storage.status is '处理状态：0-待处理 1-向量化中 2-已完成 3-失败';

comment on column t_ai_customer_service_md_storage.remark is '备注信息';

comment on column t_ai_customer_service_md_storage.create_time is '创建时间';

comment on column t_ai_customer_service_md_storage.update_time is '更新时间';

alter table t_ai_customer_service_md_storage
    owner to postgres;

create index idx_t_ai_customer_service_md_storage_status
    on t_ai_customer_service_md_storage (status);

create index idx_t_ai_customer_service_md_storage_created_time
    on t_ai_customer_service_md_storage (create_time);

create index idx_t_ai_customer_service_md_storage_original_file_name
    on t_ai_customer_service_md_storage (original_file_name);

-- auto-generated definition
create table t_chat
(
    id          bigserial
        primary key,
    uuid        varchar(60)                         not null
        unique,
    summary     varchar(60),
    create_time timestamp default CURRENT_TIMESTAMP not null,
    update_time timestamp default CURRENT_TIMESTAMP not null
);

comment on table t_chat is '聊天对话表';

comment on column t_chat.id is '主键ID，自增唯一标识';

comment on column t_chat.uuid is '对话UUID，全局唯一标识';

comment on column t_chat.summary is '对话摘要（最大长度60字符）';

comment on column t_chat.create_time is '记录创建时间（默认当前时间）';

comment on column t_chat.update_time is '记录最后更新时间（默认当前时间）';

alter table t_chat
    owner to postgres;

create index idx_chat_update_time
    on t_chat (update_time);

-- auto-generated definition
create table t_chat_message
(
    id                bigserial
        primary key,
    chat_uuid         varchar(60)                         not null,
    content           text                                not null,
    role              varchar(12),
    create_time       timestamp default CURRENT_TIMESTAMP not null,
    reasoning_content text
);

comment on table t_chat_message is '聊天消息记录表';

comment on column t_chat_message.id is '主键ID，自增唯一标识';

comment on column t_chat_message.chat_uuid is '关联的对话UUID，与t_chat表的uuid字段关联';

comment on column t_chat_message.content is '消息内容';

comment on column t_chat_message.role is '消息发送者角色（如：user-用户，assistant-助手）';

comment on column t_chat_message.create_time is '消息创建时间（默认当前时间）';

comment on column t_chat_message.reasoning_content is '推理过程内容（用于存储AI思考过程）';

alter table t_chat_message
    owner to postgres;

create index idx_chat_message_create_time
    on t_chat_message (create_time);

create index idx_chat_message_uuid
    on t_chat_message (chat_uuid);

-- auto-generated definition
create table t_file_chunk_info
(
    id           bigserial
        primary key,
    file_md5     varchar(32)                         not null,
    chunk_number integer                             not null,
    chunk_path   varchar(500)                        not null,
    chunk_size   bigint                              not null,
    create_time  timestamp default CURRENT_TIMESTAMP not null
);

comment on table t_file_chunk_info is '分片信息表';

comment on column t_file_chunk_info.id is '主键ID';

comment on column t_file_chunk_info.file_md5 is '文件MD5值';

comment on column t_file_chunk_info.chunk_number is '分片序号（从0开始）';

comment on column t_file_chunk_info.chunk_path is '分片文件存储路径';

comment on column t_file_chunk_info.chunk_size is '分片大小（字节）';

comment on column t_file_chunk_info.create_time is '创建时间';

alter table t_file_chunk_info
    owner to postgres;

create index idx_file_md5
    on t_file_chunk_info (file_md5);

comment on index idx_file_md5 is '文件MD5索引，用于查询某文件的所有分片';

-- auto-generated definition
create table t_vector_store
(
    id        uuid default uuid_generate_v4() not null
        primary key,
    content   text,
    metadata  json,
    embedding vector(1536)
);

alter table t_vector_store
    owner to postgres;

create index t_vector_store_embedding_idx
    on t_vector_store using hnsw (embedding vector_cosine_ops);

