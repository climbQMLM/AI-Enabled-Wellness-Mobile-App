# PROJECT_SPEC.md — AI-Enabled Wellness App (Full Stack, Solo)

> 这是整个项目的纲领文件。Claude Code 在写任何代码前必须先读这份文件，并按这里定义的
> 数据模型、API contract、模块职责实现。**先对齐设计，再分模块实现，不要一次性生成全部。**

## 0. 技术栈

| 层 | 技术 |
|----|------|
| 移动端 | Android, **Kotlin**, Retrofit + OkHttp(JWT 拦截器), Jetpack Compose 或 XML |
| 后端 | **Java Spring Boot**, Spring Security + JWT, JPA/Hibernate |
| 数据库 | **MySQL 8** |
| AI | **Ollama**(本地, `http://localhost:11434`), 模型 `llama3.1:8b` |
| Agentic | Spring Boot 内实现(Scheduler + 按需 API), 调用 Ollama |

> 后端访问 Ollama 用 `localhost:11434`。Android 模拟器访问后端用 `http://10.0.2.2:8080`。
> 后端必须开 CORS。所有 class/method 顶部加 `@author` 

## 1. 仓库结构

```
/backend       Spring Boot (API + JWT + Ollama 转发 + agentic + CSV 解析)
/android       Kotlin app
/db            schema.sql, seed.sql
PROJECT_SPEC.md
README.md      启动步骤
```

## 2. 数据模型 (MySQL)

设计原则：三个 RingConn CSV(Activity / Vital Signs / Sleep)按日期合并成**一张每日表**
`wellness_logs`，每个 (user_id, date) 一行，可空字段。手动录入只填子集，RingConn 导入填满。

```sql
CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,         -- BCrypt
  display_name  VARCHAR(100),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wellness_logs (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  log_date        DATE NOT NULL,
  source          ENUM('manual','ringconn') NOT NULL DEFAULT 'manual',
  -- Activity
  steps           INT,
  calories_kcal   INT,
  -- Vital Signs
  hr_avg          INT, hr_min INT, hr_max INT,        -- bpm
  spo2_avg        INT, spo2_min INT, spo2_max INT,    -- %, 已去掉 % 号
  hrv_avg         INT, hrv_min INT, hrv_max INT,      -- ms
  -- Sleep (按 wake-date 聚合后的当日合计)
  time_asleep_min INT,
  deep_sleep_min  INT,
  rem_sleep_min   INT,
  light_sleep_min INT,
  awake_min       INT,
  sleep_ratio     DECIMAL(5,2),                       -- %, 去 % 号
  -- 自算
  readiness_score INT,                                -- 0-100, 见 §6
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_date (user_id, log_date),        -- 导入靠它 upsert 去重
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE recommendations (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  rec_date    DATE NOT NULL,
  type        ENUM('recovery','chatbot') NOT NULL,
  created_by  ENUM('agent','chatbot') NOT NULL,
  content     TEXT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE chatbot_messages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  role        ENUM('user','assistant') NOT NULL,
  content     TEXT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## 3. RingConn CSV 解析规范（关键，按真实导出写死）

导入接口接收**整个 zip** 或三个 CSV。后端解压后**按表头识别文件类型**(不要依赖文件名/列序号)，
解析后按日期合并 upsert 进 `wellness_logs`，`source='ringconn'`。

### 3.1 三个文件的真实表头

```
Activity:     Date, Steps, Calories(kcal)
Vital Signs:  Date, Avg. Heart Rate(bpm), Min. Heart Rate(bpm), Max. Heart Rate(bpm),
              Avg. Spo2(%), Min. Spo2(%), Max. Spo2(%),
              Avg. HRV(ms), Min. HRV(ms), Max. HRV(ms)
Sleep:        Start Time, End Time, Falling Asleep Time, Wake-up time,
              Sleep Time Ratio(%), Time Asleep(min),
              Sleep Stages - Awake(min), - REM(min), - Light Sleep(min), - Deep Sleep(min)
```

### 3.2 解析坑（必须处理）

1. **换行是 CRLF (`\r\n`)** — 用标准 CSV 库(如 Apache Commons CSV / OpenCSV)，别手 split。
2. **百分比字段带 `%`**（`Avg. Spo2(%)`、`Sleep Time Ratio(%)`）— strip `%` 再转数字。
3. **Sleep 一天可能多段**（午睡 + 夜睡，本数据集存在大量重复 wake-date）—
   归属日期 = **Wake-up time 的日期**(`substring(0,10)`)；同一 wake-date 的多段
   **累加** `Time Asleep / 各 Stages / Awake`，`sleep_ratio` 取加权平均或主睡段值。
4. **字段缺失/多余要容错** — 按列名映射，认不出的列跳过，不要因为某列缺失整体失败。
5. **空值** — RingConn 某天没戴可能缺行或空值，入库为 NULL，不要填 0。
6. **upsert** — 按 `(user_id, log_date)` 存在则更新、不存在则插入。

### 3.3 合并逻辑

```
for each date D in (Activity ∪ VitalSigns ∪ Sleep[by wake-date]):
    row = wellness_logs(user, D)  # 取已有或新建
    row.steps/calories        <- Activity[D]
    row.hr_*/spo2_*/hrv_*      <- VitalSigns[D]
    row.*_sleep_min/ratio      <- Σ Sleep[wake-date=D]
    row.source = 'ringconn'
    row.readiness_score        <- compute (§6)
    upsert(row)
```

### 3.4 导入返回体

```json
{ "importedDates": 178, "updated": 12, "inserted": 166,
  "dateRange": ["2026-01-01","2026-06-29"], "skippedRows": 0 }
```

## 4. REST API Contract

所有 `/api/**`（除 auth）需要 `Authorization: Bearer <jwt>`。

### 通用约定
- **错误响应**统一格式：`{"error": "message"}`，HTTP status 照常用(400/401/404/409 等)。
- **JWT**：单一长期 token，过期时间 7 天，不做 refresh token。登出仅客户端丢弃 token(黑名单为可选 stretch，非必须)。

### Auth
- `POST /api/auth/register` `{email,password,displayName}` → `{token,user}`
- `POST /api/auth/login` `{email,password}` → `{token,user}`
- `POST /api/auth/logout` → 204（客户端丢弃 token；可选服务端黑名单）

### Wellness（mandatory CRUD）
- `GET  /api/wellness?from=YYYY-MM-DD&to=YYYY-MM-DD` → `[WellnessLog]`
  不传 from/to 时默认返回**最近 30 天**(以今天为基准)。
- `POST /api/wellness` 手动录入 `{logDate, steps?, timeAsleepMin?, ...}`
  若 `(user_id, log_date)` 已存在记录 → **409 Conflict**，`{"error":"wellness log already exists for this date, use PUT to update"}`。
- `PUT  /api/wellness/{id}` **PATCH 语义**：body 只需包含要修改的字段，未传字段保持原值不变(后端用 null-aware 部分更新，不要整行覆盖)。
- `DELETE /api/wellness/{id}`
- `POST /api/wellness/import` `multipart/form-data`(zip 或多个 csv) → 见 §3.4
  **字段级合并**：导入(`source='ringconn'`)时若该 (user_id,log_date) 已有手动录入(`source='manual'`)的字段，**不覆盖**已有非空字段，只填充原本为 NULL 的字段；`source` 合并后置为 `'ringconn'`(表示该天含导入数据)。若导入字段和已有手动字段都有值，以已有(手动)为准。

### Chatbot（走后端 → Ollama）
- `POST /api/chat` `{message}` → `{reply}`（后端注入该用户近 7 天数据做上下文）
- `GET  /api/chat/history` → `[chatbot_messages]`

### Agentic
- `POST /api/agent/run` 按需触发，返回新生成的 recommendation
- `GET  /api/recommendations?from&to` → `[recommendation]`
- 后端 Scheduler 每周定时跑一次 agent（`@Scheduled`）

## 5. Ollama 集成

- 后端 `OllamaClient` POST `http://localhost:11434/api/chat`，`stream:false`（demo 求稳）。
- 模型：`qwen2.5:7b`（中文建议）或 `llama3.1:8b`。
- Chatbot prompt 模板（注入真实数据，这就是 prompt engineering / 轻量 RAG）：

```
System: 你是一个健康助理。仅基于用户提供的近 7 天可穿戴数据给出建议，
        数据缺失时不要编造。建议要具体、可执行、简短。
Context: 用户近 7 天指标：
  - 平均睡眠 {avg_sleep} 分钟，深睡 {deep}，REM {rem}
  - 平均 HRV {hrv} ms，静息心率(取 hr_min)均值 {rhr} bpm
  - 平均步数 {steps}，平均血氧 {spo2}%
User: {用户问题}
```

- Agentic 让 Ollama **只返回 JSON**（便于解析入库），prompt 末尾强约束：
  `只输出 JSON：{"summary": "...", "recommendation": "..."}，不要任何多余文字。`

## 6. Readiness Score（自算，确定性，不依赖 LLM）

每日 0–100，三项归一化加权（缺项则该项不计、重新归一权重）：

```
# Normalize HRV to 0-1: 20ms -> 0, 80ms -> 1 (higher HRV = better recovery)
hrv_norm   = clamp((hrv_avg - 20) / (80 - 20), 0, 1)

# Normalize resting HR to 0-1: 70bpm -> 0, 45bpm -> 1 (lower RHR = better)
rhr_norm   = clamp((70 - hr_min) / (70 - 45), 0, 1)

# Normalize sleep to 0-1: 8h (480min) = full score
sleep_norm = clamp(time_asleep_min / 480, 0, 1)

# Weighted score 0-100: HRV 40%, RHR 30%, sleep 30%
readiness  = round(100 * (0.4*hrv_norm + 0.3*rhr_norm + 0.3*sleep_norm))
```

### 6.1 缺项重新归一权重（伪代码）

某项原始字段为 NULL 时，该项不计入计算，权重在剩余项中按原比例重新归一。
若三项全缺，`readiness_score = NULL`（不写死为 0，避免误导）。

```
components = []
if hrv_avg != null:  components.add(("hrv",   hrv_norm,  0.4))
if hr_min  != null:  components.add(("rhr",   rhr_norm,  0.3))
if time_asleep_min != null: components.add(("sleep", sleep_norm, 0.3))

if components.isEmpty(): return null

weightSum = sum(w for (_, _, w) in components)
readiness = round(100 * sum(norm * (w / weightSum) for (_, norm, w) in components))
```

## 7. Agentic Workflow（命中作业五步）

```
1. retrieve  : 拉用户近 7 天 wellness_logs
2. analyze   : 算 HRV 斜率、静息心率斜率、平均睡眠、平均 readiness
3. decide    : 规则判断状态
   - HRV 持续下降 + 静息心率上升 + 睡眠 < 6h  → "疲劳/恢复不足，建议减量休息"
   - readiness 上升 + 睡眠充足               → "状态良好，可正常/加量"
   - 否则                                    → "维持现状"
4. generate  : 把状态 + 数据交给 Ollama 生成一句自然语言个性化建议(JSON)
5. save+show : 存 recommendations(type=recovery, created_by=agent)，App 展示
```

触发：`POST /api/agent/run`（用户点按钮）+ Spring `@Scheduled`（每日定时）。

## 8. Feature 清单

| # | Feature | 类型 |
|---|---------|------|
| 1 | 登录 + 登出 | Mandatory |
| 2 | Wellness CRUD（手动 + 查询/历史） | Mandatory |
| 3 | RingConn CSV 导入 | 自定义 |
| 4 | Chatbot（数据增强，走 Ollama） | Mandatory |
| 5 | Agentic 恢复分析（readiness + 趋势 + 建议） | Bonus |
| 6 | JWT 鉴权 | Bonus |

## 9. Android 页面

1. **登录/注册** — 存 JWT（EncryptedSharedPreferences），OkHttp 拦截器自动带 header
2. **Dashboard** — readiness 分 + 近 7 天 HRV/睡眠/步数小图
3. **Wellness 列表 + 手动录入/编辑/删除**
4. **导入 RingConn CSV** — 文件选择器选 zip/csv → 上传 → 显示导入摘要
5. **Chatbot 聊天页** — 消息流，调 `/api/chat`
6. **Recommendations** — agent 生成的建议列表 + "立即分析"按钮

## 10. 实现顺序

```
1) /db schema.sql + seed（先建表）
2) /backend：User + JWT + Security 过滤器  → 用 curl 测通登录
3) /backend：Wellness CRUD               → curl 测通
4) /backend：CSV import（§3，按真实表头） → 用上传的真实 zip 测通
5) /backend：OllamaClient + /api/chat     → 测通
6) /backend：readiness + agentic + scheduler
7) /android：逐页对接已测通的 API
```

> 每个模块完成后先独立验证再进下一个，不要堆到最后一起 debug。
