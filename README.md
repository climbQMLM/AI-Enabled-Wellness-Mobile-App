# AI-Enabled Wellness App

> **NUS-ISS Mobile Application Development CA — Solo Coursework**
>
> This project was completed entirely through **vibe coding** — every line of backend Java, Android Kotlin, SQL schema, and configuration was generated through iterative AI-assisted development sessions with Claude Code, without manually writing code. Architecture decisions, debugging, and module design were all driven through natural language conversation.

---

## What It Does

A full-stack mobile wellness application that:

- Lets users **log daily health metrics** (HRV, resting heart rate, sleep, steps, SpO2, readiness score)
- **Imports RingConn wearable CSV exports** (activity, vital signs, sleep — multi-file or ZIP)
- Provides an **AI wellness chatbot** backed by a local Ollama LLM, with 7-day health context injected into every prompt
- Runs a **5-step agentic workflow** (retrieve → analyse → decide → generate → save) that produces personalised recovery recommendations, scheduled weekly and triggerable on demand
- Displays a **Dashboard** with readiness score and 7-day trend charts (HRV, sleep, steps)

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Mobile | Android (Kotlin), Retrofit 2, OkHttp, MPAndroidChart, EncryptedSharedPreferences |
| Backend | Java 17, Spring Boot 3.5.0, Spring Security, JPA / Hibernate |
| Database | MySQL 8 (Docker) |
| AI | Ollama — `llama3:latest` (local, no cloud dependency) |
| Auth | JWT (HS256, 7-day token) |

---

## Project Structure

```
Team6/
├── backend/          Spring Boot API (auth, wellness CRUD, CSV import, chatbot, agent)
├── android/          Kotlin app (single-Activity + 5 Fragments + BottomNav)
├── db/
│   ├── schema.sql    Table definitions
│   └── seed.sql      Demo user + sample data
├── docker-compose.yml
├── 环境配置清单.docx   Full environment configuration reference
├── 调试文档.docx       Debug & demo walkthrough
└── README.md
```

---

## Prerequisites

| Software | Version | Notes |
|----------|---------|-------|
| JDK | 17 | Eclipse Temurin recommended |
| Docker Desktop | 4.x+ | Must be running before backend starts |
| Ollama | latest | Install from https://ollama.com |
| Android Studio | Meerkat+ | AGP 9.2.1 bundled |

Pull the LLM model once (≈ 4.7 GB, requires Wi-Fi):

```bash
ollama pull llama3:latest
```

---

## Quick Start

```bash
# 1. Start MySQL
cd ~/Desktop/Team6
docker compose up -d

# 2. Start backend (first run downloads Maven dependencies)
cd backend
./mvnw spring-boot:run
# Wait for: Started WellnessBackendApplication

# 3. Open android/ in Android Studio, select an AVD (API 26+), press Run ▶
```

Login with the pre-seeded demo account:

```
Email:    demo@wellness.app
Password: password123
```

---

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Login, returns JWT |
| GET | `/api/wellness` | List logs (`?from=&to=`) |
| POST | `/api/wellness` | Create log entry |
| PUT | `/api/wellness/{id}` | Update log entry |
| DELETE | `/api/wellness/{id}` | Delete log entry |
| POST | `/api/wellness/import` | Import RingConn CSV / ZIP |
| POST | `/api/chat` | Send message to AI chatbot |
| GET | `/api/chat/history` | Full conversation history |
| POST | `/api/agent/run` | Trigger agentic analysis now |
| GET | `/api/recommendations` | List AI recommendations |

All endpoints except register/login require `Authorization: Bearer <token>`.

---

## Android App Pages

| Tab | Page | Key Feature |
|-----|------|-------------|
| Dashboard | Health summary | Readiness score + 3 line charts |
| Wellness | Log list | CRUD for daily health records |
| Import | CSV import | Multi-file picker, multipart upload |
| Chat | AI chatbot | LLM replies with health context |
| Insights | Recommendations | Agent-generated weekly advice |

---

## Agentic Workflow

The agent runs automatically every **Monday at 02:00** (Spring `@Scheduled`) and can also be triggered manually via `POST /api/agent/run`.

```
Step 1 — Retrieve   last 7 days of wellness logs
Step 2 — Analyse    HRV trend slope, RHR slope, avg sleep, avg readiness
Step 3 — Decide     rule-based state (overtraining / under-recovery / optimal)
Step 4 — Generate   structured JSON recommendation from Ollama
Step 5 — Save       persisted to recommendations table
```

---

## Environment Notes

- **Emulator networking**: `10.0.2.2` inside the Android emulator maps to the host machine's `localhost`, so the backend URL is `http://10.0.2.2:8080/`.
- **Real device**: change `BASE_URL` in `network/ApiClient.kt` to the host LAN IP.
- **HTTP cleartext**: `android:usesCleartextTraffic="true"` is set in `AndroidManifest.xml` for local dev. Remove for production.
- **MySQL port**: mapped to host `3307` (not `3306`) to avoid conflicts with any locally installed MySQL instance.

---

## Vibe Coding Note

Every module in this project — from the Spring Security JWT filter to the Ollama agentic pipeline to the Android EncryptedSharedPreferences session manager — was built entirely through conversational AI-assisted development. No boilerplate was typed by hand. The entire development process, including architecture planning, debugging 220 compile errors, fixing Gradle plugin conflicts, and translating the UI to English, happened through natural language prompts.

---

