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
├── backend/                Spring Boot API (auth, wellness CRUD, CSV import, chatbot, agent)
├── android/                Kotlin app (single-Activity + 5 Fragments + BottomNav)
├── db/
│   ├── schema.sql          Table definitions
│   ├── seed.sql            Demo user + sample data
│   └── sample-data/        Anonymised RingConn CSV exports (Jan–Jun 2026)
├── docker-compose.yml
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

## Loading Sample Data

After the backend is running, import the bundled RingConn CSVs to populate the dashboard and enable agent analysis:

```bash
# Get a token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@wellness.app","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Import all three CSV files
curl -s -X POST http://localhost:8080/api/wellness/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@db/sample-data/Activity-2026-01-01-2026-06-29.csv" \
  -F "files=@db/sample-data/Sleep-2026-01-01-2026-06-29.csv" \
  -F "files=@db/sample-data/Vital Signs-2026-01-01-2026-06-29.csv"
```

Or use the **Import** tab in the Android app and pick the files from `db/sample-data/`.

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

### Quick curl test

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@wellness.app","password":"password123","displayName":"Demo"}' | python3 -m json.tool

# Chat
curl -s -X POST http://localhost:8080/api/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"My sleep has been poor lately, any advice?"}' | python3 -m json.tool

# Trigger agent analysis
curl -s -X POST http://localhost:8080/api/agent/run \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

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

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| CLEARTEXT network error on emulator | Android 9+ blocks HTTP | `android:usesCleartextTraffic="true"` already set in AndroidManifest |
| App can't reach backend | Wrong IP | Emulator uses `10.0.2.2:8080`; real device uses host LAN IP |
| Login returns 401 | Token expired or wrong password | Re-login; use `demo@wellness.app / password123` |
| Chat / Agent times out | Ollama not running or model missing | Run `ollama list` — confirm `llama3:latest` is present |
| CSV import returns 400 | Column name mismatch | Confirm CSV headers match RingConn export format |

---

## Environment Notes

- **Emulator networking**: `10.0.2.2` inside the Android emulator maps to the host machine's `localhost`.
- **Real device**: change `BASE_URL` in `network/ApiClient.kt` to the host LAN IP.
- **MySQL port**: mapped to host `3307` to avoid conflicts with any locally installed MySQL instance.
- **Ollama timeout**: read timeout is set to 120 s — `llama3` inference can be slow on CPU.

---

## Reset Database

```bash
docker compose down -v && docker compose up -d
```

This wipes all data and re-runs `schema.sql` + `seed.sql` from scratch.

---

## Vibe Coding Note

Every module in this project — from the Spring Security JWT filter to the Ollama agentic pipeline to the Android EncryptedSharedPreferences session manager — was built entirely through conversational AI-assisted development. No boilerplate was typed by hand. The entire development process, including architecture planning, debugging 220 compile errors, fixing Gradle plugin conflicts, and translating the UI to English, happened through natural language prompts.
