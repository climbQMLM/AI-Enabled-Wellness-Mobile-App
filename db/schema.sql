-- ============================================================================
-- schema.sql — AI-Enabled Wellness App
-- @author Xie Maonan
--
-- 用法：
--   docker exec -i <mysql-container> mysql -uroot -p<password> wellness < schema.sql
-- 或本地：
--   mysql -u root -p wellness < schema.sql
--
-- 设计要点（详见 PROJECT_SPEC.md §2）：
--   1. 三个 RingConn CSV (Activity / Vital Signs / Sleep) 按日期合并成一张
--      每日表 wellness_logs，每个 (user_id, log_date) 唯一一行，字段可空。
--   2. 手动录入只填子集字段；RingConn 导入做"字段级合并"——已有手动字段不被
--      覆盖，只填充 NULL 字段（合并逻辑在后端实现，schema 本身不做约束）。
--   3. 全部使用 InnoDB（支持外键 + 事务），字符集统一 utf8mb4（防止 emoji /
--      中文聊天记录乱码）。
-- ============================================================================

-- 如果数据库不存在则创建；项目统一用 wellness 这个库名
CREATE DATABASE IF NOT EXISTS wellness
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE wellness;

-- 开发环境每次重跑脚本要能干净重建，按外键依赖的反顺序 DROP
-- （生产环境不会这样用，但这是课程作业，图省事第一）
DROP TABLE IF EXISTS chatbot_messages;
DROP TABLE IF EXISTS recommendations;
DROP TABLE IF EXISTS wellness_logs;
DROP TABLE IF EXISTS users;

-- ----------------------------------------------------------------------------
-- 表1：users — 用户账号
-- ----------------------------------------------------------------------------
CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,

  -- 登录邮箱，唯一索引保证不能重复注册
  email         VARCHAR(255) NOT NULL UNIQUE,

  -- 注意：这里存的是 BCrypt 哈希后的密文，绝对不能存明文密码！
  -- Spring Security 的 BCryptPasswordEncoder 生成的串长度固定 60 字符，
  -- 这里给 255 留足余量。
  password_hash VARCHAR(255) NOT NULL,

  -- 昵称，纯展示用，允许为空
  display_name  VARCHAR(100),

  -- 注册时间，由数据库自动填当前时间，无需后端手动赋值
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 表2：wellness_logs — 每日健康数据（核心表）
--
-- 一行 = 一个用户在某一天的所有可穿戴/手动数据汇总。
-- 字段按来源分成三组（对应 RingConn 的三个 CSV），中间用注释隔开方便对照。
-- ----------------------------------------------------------------------------
CREATE TABLE wellness_logs (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,

  -- 数据属于哪个用户，外键关联 users.id
  user_id         BIGINT NOT NULL,

  -- 数据对应的自然日（不是数据库写入时间！是用户的"那一天"）
  -- 注意：Sleep 数据的归属日期 = 起床时间(Wake-up time)所在的日期，
  -- 不是入睡时间所在的日期，详见 PROJECT_SPEC.md §3.2 第3条。
  log_date        DATE NOT NULL,

  -- 这一行数据最近一次是被手动填的还是 RingConn 导入覆盖/合并过的。
  -- 注意：这是"整行最后写入来源"的标记，不代表每个字段都来自该来源——
  -- 字段级合并规则见 PROJECT_SPEC.md §4（手动字段不会被导入覆盖）。
  source          ENUM('manual','ringconn') NOT NULL DEFAULT 'manual',

  -- ===== Activity（来自 RingConn 的 Activity.csv） =====
  steps           INT,            -- 当日步数
  calories_kcal   INT,            -- 当日消耗卡路里(kcal)

  -- ===== Vital Signs（来自 RingConn 的 Vital Signs.csv） =====
  -- 心率，单位 bpm（次/分钟）
  hr_avg          INT,
  hr_min          INT,            -- 全天最低心率，readiness 算法里当"静息心率"用
  hr_max          INT,

  -- 血氧饱和度，单位 %。原始 CSV 里带 "%" 符号，入库前必须 strip 掉转成纯数字
  spo2_avg        INT,
  spo2_min        INT,
  spo2_max        INT,

  -- 心率变异性 HRV，单位 ms（毫秒）。HRV 越高代表恢复越好
  hrv_avg         INT,
  hrv_min         INT,
  hrv_max         INT,

  -- ===== Sleep（来自 RingConn 的 Sleep.csv，按 wake-date 聚合后的当日合计） =====
  -- 原始 CSV 一天可能有多条睡眠记录（比如午睡 + 夜间睡眠），
  -- 同一个 wake-date 的多段要在导入时先累加，再写入这一行。
  time_asleep_min INT,            -- 总睡眠时长（分钟）
  deep_sleep_min  INT,            -- 深睡时长（分钟）
  rem_sleep_min   INT,            -- REM 睡眠时长（分钟）
  light_sleep_min INT,            -- 浅睡时长（分钟）
  awake_min       INT,            -- 床上清醒时长（分钟）

  -- 睡眠效率比例，单位 %。原始字段名 Sleep Time Ratio(%)，同样要 strip "%"。
  -- 多段睡眠时取加权平均或主睡段值，见 PROJECT_SPEC.md §3.2 第3条
  sleep_ratio     DECIMAL(5,2),

  -- ===== 自算字段（不来自 CSV，后端根据上面的数据算出来的） =====
  -- 0-100 分，根据 hrv_avg / hr_min / time_asleep_min 加权计算，
  -- 具体公式 + 缺项重新归一权重的算法见 PROJECT_SPEC.md §6 / §6.1。
  -- 三项数据全缺时这里为 NULL，不要写死成 0（避免误导用户"今天恢复很差"）。
  readiness_score INT,

  -- 行创建/更新时间，全部交给数据库自动维护，后端不用手动赋值
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  -- 关键约束：同一个用户同一天只能有一行数据。
  -- 这个唯一键是 CSV 导入 upsert（ON DUPLICATE KEY UPDATE）的依据，
  -- 也是 POST /api/wellness 判断"今天是否已存在记录"返回 409 的依据。
  UNIQUE KEY uq_user_date (user_id, log_date),

  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 表3：recommendations — AI 生成的建议（chatbot 回复 + agent 周期性分析结果）
-- ----------------------------------------------------------------------------
CREATE TABLE recommendations (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,

  -- 这条建议对应的日期（agent 跑批时的"今天"，或建议所针对的那一天）
  rec_date    DATE NOT NULL,

  -- 建议类型：
  --   'recovery' = agentic workflow 自动生成的恢复建议（见 PROJECT_SPEC.md §7）
  --   'chatbot'  = 用户主动问 chatbot 时顺带存的一条建议（如果有的话）
  type        ENUM('recovery','chatbot') NOT NULL,

  -- 这条建议是谁触发生成的：
  --   'agent'    = 由 @Scheduled 定时任务或用户点"立即分析"按钮触发
  --   'chatbot'  = 用户在聊天界面里问出来的
  created_by  ENUM('agent','chatbot') NOT NULL,

  -- 建议正文，Ollama 生成的自然语言文本（agentic 场景下来自 JSON 里的
  -- summary + recommendation 字段拼接，具体格式由后端决定）
  content     TEXT NOT NULL,

  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 表4：chatbot_messages — 聊天记录（用于展示历史对话 GET /api/chat/history）
-- ----------------------------------------------------------------------------
CREATE TABLE chatbot_messages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,

  -- 这条消息是用户发的还是 AI 回复的，前端按这个字段决定气泡靠左还是靠右
  role        ENUM('user','assistant') NOT NULL,

  content     TEXT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ============================================================================
-- 注意：索引故意保持极简（除主键和 uq_user_date 外不加额外索引），
-- 这是课程作业级别的数据量，不需要为 chatbot_messages / recommendations
-- 额外建 (user_id, created_at) 索引。见 PROJECT_SPEC.md §2 讨论。
-- ============================================================================
