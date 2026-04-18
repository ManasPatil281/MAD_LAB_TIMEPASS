# ScholarMate — Complete Project Reference

ScholarMate is a full-stack AI-powered research platform consisting of:
- A **native Java Android app** for the UI client layer
- A **Python FastAPI server** as the API and AI execution layer
- A **PostgreSQL database** hosted on **Supabase** for all persistent data

This document explains every folder, every file, and exactly how they work together.

---

## Table of Contents

1. [System Architecture & Request Flow](#1-system-architecture--request-flow)
2. [Android Application](#2-android-application-appsrcmain)
3. [FastAPI Backend](#3-fastapi-backend-appapp)
4. [Database Layer](#4-database-layer-postgresql--supabase)
5. [Local Setup Guide](#5-local-setup-guide)

---

## 1. System Architecture & Request Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android App (Java)                          │
│  User taps a tool → Activity builds request → ApiClient.java   │
│  sends HTTP call (JSON or Multipart/PDF) to FastAPI backend     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS REST (Bearer JWT)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   FastAPI Backend (Python)                       │
│                                                                 │
│  Request → UsageLoggingMiddleware (logs to DB) → Router         │
│  Router dependency: get_current_user() validates JWT session    │
│  Router calls ai_engine.py function                             │
│  Result is saved in ORM model then returned as Pydantic JSON    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ SQLAlchemy (psycopg2 + SSL)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│             PostgreSQL on Supabase (AWS ap-northeast-1)         │
│                                                                 │
│  Tables: users, sessions, submissions, usage_logs              │
│          + 6 result tables (one per feature)                    │
└─────────────────────────────────────────────────────────────────┘
```

### Step-by-step for a typical "Grammar Check" call

1. User types text in `GrammarCheckActivity.java` and clicks **Check**.
2. `ApiClient.grammarCheck(text, language, token, modelType, callback)` is called. It builds a JSON body and fires a **POST** to `https://<backend>/api/v1/submit/grammar-check` with `Authorization: Bearer <jwt>`.
3. On the server, `UsageLoggingMiddleware` intercepts the request, records the endpoint/response time/user into the `usage_logs` table asynchronously.
4. The request reaches `routers/submit.py` → `submit_grammar_check()`. FastAPI first resolves the `get_current_user` dependency: it decodes the JWT, looks up the matching row in the `sessions` table to confirm it is not revoked, then fetches the `User` ORM row.
5. A new `Submission` row is inserted (`status = "processing"`).
6. `ai_engine.grammar_check_text(text)` is called, which sends a prompt to the **Groq LLM API** (`llama-3.3-70b-versatile`) and returns the raw string result.
7. A `GrammarCheckResult` row is inserted, linked to the submission ID.
8. The submission row is updated to `status = "completed"`.
9. A `GrammarCheckResponse` Pydantic model is returned as JSON (HTTP 201).
10. The Android `ApiCallback.onSuccess(body)` fires on the main thread; the activity parses the JSON and renders it inside `ResultBottomSheetFragment`.

---

## 2. Android Application (`app/src/main/`)

### Directory Map

```
app/src/main/
├── AndroidManifest.xml            ← App declaration, permissions, activities, service
├── java/com/example/myapplication/
│   ├── ApiClient.java             ← All HTTP network calls (OkHttp)
│   ├── SessionManager.java        ← JWT token storage (SharedPreferences)
│   ├── SplashActivity.java        ← Entry point / brand screen
│   ├── LoginActivity.java         ← Login form → POST /auth/login
│   ├── SignupActivity.java         ← Register form → POST /auth/register
│   ├── HomeActivity.java           ← Dashboard: stats, recent submissions, sensor, media
│   ├── AiContentDetectionActivity.java   ← AI detection tool UI
│   ├── GrammarCheckActivity.java         ← Grammar check tool UI
│   ├── PlagiarismDetectionActivity.java  ← Plagiarism detection tool UI
│   ├── ParaphrasingActivity.java         ← Paraphrase tool UI
│   ├── SummarizationActivity.java        ← Summarization tool UI (host)
│   ├── SummarizationFragment.java        ← Fragment inside Summarization
│   ├── PaperReviewActivity.java           ← Paper review tool UI (PDF upload)
│   ├── GesturePlaygroundActivity.java    ← Gesture + touch interaction demo
│   ├── ResultBottomSheetFragment.java    ← Slide-up panel for all AI results
│   ├── SensorNotificationService.java   ← Background Android Service
│   └── AdminDashboardActivity.java      ← Admin stats screen (admin role only)
└── res/
    ├── layout/                    ← All XML screen/view layouts
    ├── drawable/                  ← Shape backgrounds, icons, gradients
    └── values/                    ← colors.xml, strings.xml, themes.xml, dimens.xml
```

### File-by-file Explanation

---

#### `AndroidManifest.xml`
The root configuration file for the entire Android app. It declares:
- **Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `POST_NOTIFICATIONS` (for Android 13+).
- **Every Activity** that can be launched — must be declared here or it will crash.
- **`<service android:name=".SensorNotificationService">`** so Android knows this background service exists.
- The `SplashActivity` is marked as `MAIN`/`LAUNCHER`, making it the entry point.

---

#### `ApiClient.java`
The single network layer for the entire app. All other classes call static methods on this class — nothing talks to the internet directly.

**Key internals:**
- `BASE_URL` = `https://manpat-scholarmate-4m4r.onrender.com/api/v1` (the live Render deployment).
- Uses a singleton `OkHttpClient` with **30s connect timeout** and **300s read timeout** (AI calls can be slow).
- Two request formats:
  - `postJson()` — sends a `application/json` body (used for Grammar Check, Login, Register).
  - `postMultipart()` — sends `multipart/form-data` (used for all PDF uploads and text fields alongside files).
- `enqueue()` wraps every call in `OkHttp`'s async callback, then posts results back to the **main thread** via `mainHandler` so activities can safely update UI.
- `ApiCallback` interface with `onSuccess(String responseBody)` and `onError(int code, String error)`.

**Methods mapped to API endpoints:**

| Method | HTTP Call | Used by |
|---|---|---|
| `login()` | POST /auth/login | LoginActivity |
| `register()` | POST /auth/register | SignupActivity |
| `logout()` | POST /auth/logout | HomeActivity menu |
| `detectAiText()` | POST /submit/ai-detection | AiContentDetectionActivity |
| `detectAiPdf()` | POST /submit/ai-detection | AiContentDetectionActivity |
| `grammarCheck()` | POST /submit/grammar-check | GrammarCheckActivity |
| `paraphraseText()` | POST /submit/paraphrase | ParaphrasingActivity |
| `paraphrasePdf()` | POST /submit/paraphrase | ParaphrasingActivity |
| `detectPlagiarism()` | POST /submit/plagiarism | PlagiarismDetectionActivity |
| `summarizeText()` | POST /submit/summarize | SummarizationFragment |
| `summarizePdf()` | POST /submit/summarize | SummarizationFragment |
| `paperReview()` | POST /submit/paper-review | PaperReviewActivity |
| `getDashboardStats()` | GET /users/me/history?limit=1 | HomeActivity |
| `getRecentSubmissions()` | GET /users/me/history?limit=3 | HomeActivity |
| `getAdminStats()` | GET /admin/stats | AdminDashboardActivity |

---

#### `SessionManager.java`
A thin wrapper around `SharedPreferences` (Android's local key-value store). Saves and retrieves the JWT token between app sessions.

- `saveToken(String token)` — stores the token under a fixed key.
- `getToken()` — retrieves it for use in `Authorization: Bearer` headers.
- `isLoggedIn()` — returns `true` if a token is present.
- `clearSession()` — deletes the token (used on logout).

Without this, the user would have to log in every time the app reopened.

---

#### `HomeActivity.java`
The main dashboard screen after login. It has three major jobs:

1. **Navigation** — Each tool card (AI Detection, Grammar, etc.) has an `OnClickListener` that fires an `Intent` to launch the corresponding Activity.
2. **API data loading** — On `onCreate`, it calls `ApiClient.getDashboardStats()` (displays total submission count) and `ApiClient.getRecentSubmissions()` (inflates the recent submissions list dynamically by calling `LayoutInflater` to populate `item_recent_submission.xml` rows).
3. **Sensor + Media (Lab Experiments 7 & 8)**:
   - Registers a `SensorEventListener` on the `TYPE_ACCELEROMETER` sensor. Reads X/Y/Z force values, calculates delta acceleration, and if a shake is detected (`acceleration > 12`), starts `SensorNotificationService` via `startService(intent)`.
   - Registers an `ActivityResultLauncher<String>` with `ActivityResultContracts.GetContent()`. The profile icon (`iv_profile`) click triggers `getContent.launch("image/*")`, opening the system media picker. The returned URI is set as the ImageView source.

---

#### `SensorNotificationService.java`
An Android `Service` (not an Activity — it has no UI). When started by `HomeActivity`, it:
1. Creates a `NotificationChannel` (required on Android 8.0+) named `"SensorNotificationChannel"`.
2. Builds a `NotificationCompat.Builder` notification with title "Motion Detected!" and high priority.
3. Posts it via `NotificationManager.notify()`.
4. Immediately calls `stopSelf()` to terminate (it is a one-shot service, not a long-running daemon).

This demonstrates **Android Services** and **background notifications** (Lab Experiment 8).

---

#### `ResultBottomSheetFragment.java`
A `BottomSheetDialogFragment` used by **every feature activity** to display the AI result. Called like:
```java
ResultBottomSheetFragment.newInstance(title, resultText).show(getSupportFragmentManager(), "result");
```
The fragment renders the result text in a scrollable view and provides a **Copy** and optionally a **Download** button. It is a Fragment (not an Activity), meaning it slides up over the current screen without navigating away.

---

#### Feature Activities
Each tool has its own Activity that follows the same pattern:
1. Show an input field (text `EditText`) and optionally a PDF picker button.
2. On submit, call the relevant `ApiClient` method.
3. In `onSuccess`, parse the JSON string and call `ResultBottomSheetFragment.newInstance(...)`.
4. In `onError`, show a `Toast`.

`PaperReviewActivity` and `PlagiarismDetectionActivity` are **PDF-only** (they use `ActivityResultContracts.GetContent()` filtered to `"application/pdf"`).

---

#### `GesturePlaygroundActivity.java`
A standalone activity demonstrating advanced touch handling (Lab Experiment 4):
- `GestureDetector` handles: single tap, double tap, long press, fling/swipe.
- `ScaleGestureDetector` handles: pinch-to-zoom.
- Custom `onTouchEvent` handles two-finger rotation by tracking pointer angles.

---

## 3. FastAPI Backend (`app/app/`)

### Directory Map

```
app/app/
├── main.py            ← App entry point, middleware, router mounting
├── config.py          ← Settings loaded from .env
├── database.py        ← SQLAlchemy engine, session factory, table creation
├── core/
│   ├── ai_engine.py   ← All LLM logic (Groq + HuggingFace + FAISS RAG)
│   ├── security.py    ← bcrypt password hashing, JWT encode/decode
│   └── dependencies.py← FastAPI dependencies: get_current_user, require_admin
├── models/
│   ├── __init__.py    ← Imports all ORM classes (needed for create_all)
│   ├── user.py        ← users table ORM
│   ├── session.py     ← sessions table ORM
│   ├── submission.py  ← submissions table ORM (parent of all results)
│   ├── ai_detection.py
│   ├── grammar_check.py
│   ├── paraphrase.py
│   ├── plagiarism.py
│   ├── summarization.py
│   ├── paper_review.py
│   └── usage_log.py   ← usage_logs table ORM
├── routers/
│   ├── auth.py        ← POST /auth/register, /login, /logout
│   ├── users.py       ← GET /users/me, GET /users/me/history
│   ├── submit.py      ← POST /submit/* (all 6 features)
│   ├── submissions.py ← GET /submissions/{id}
│   ├── admin.py       ← GET /admin/stats, /admin/logs
│   └── health.py      ← GET /health (DB connectivity check)
├── schemas/
│   ├── auth.py        ← Pydantic models for login/register request+response
│   ├── user.py        ← Pydantic models for user profile responses
│   ├── submission.py  ← Request + Response models for all 6 features
│   └── admin.py       ← Pydantic models for admin stats/logs
└── utils/
    └── logging_middleware.py ← Records every request to usage_logs table
```

### File-by-file Explanation

---

#### `main.py`
The top-level FastAPI application file. It:
- Creates the `FastAPI` instance with metadata (title, version, docs URL at `/api/v1/docs`).
- Uses an `@asynccontextmanager` lifespan function to call `create_tables()` on startup — this creates any missing PostgreSQL tables without destroying existing ones.
- Attaches `CORSMiddleware` allowing all origins (safe for dev; restrict in production).
- Attaches `UsageLoggingMiddleware` so every request is logged.
- Mounts all 6 routers under `API_PREFIX = "/api/v1"`.

---

#### `config.py`
Uses `pydantic-settings` `BaseSettings` to load environment variables. Key settings:
- `DATABASE_URL`: PostgreSQL connection string (defaults to the Supabase URL; override via `.env`).
- `SECRET_KEY`: Used to sign JWTs.
- `ACCESS_TOKEN_EXPIRE_MINUTES`: 1440 (24 hours).
- `APP_NAME`, `APP_VERSION`: Used in FastAPI metadata.
- Reads from `.env` file automatically (`env_file = ".env"`).

---

#### `database.py`
Sets up the SQLAlchemy connection. Key components:
- `engine`: A `psycopg2` sync engine connecting to Supabase over SSL (`sslmode=require`). Pool of 10 connections with 20 overflow.
- `SessionLocal`: A `sessionmaker` factory. Each request gets its own session.
- `Base`: The `declarative_base()` that all ORM models inherit from.
- `get_db()`: A FastAPI dependency generator — yields a session and closes it after the request completes (used in every router endpoint via `Depends(get_db)`).
- `create_tables()`: Imports all model modules first (so SQLAlchemy registers them on `Base.metadata`) then calls `Base.metadata.create_all()`.
- `check_db_connection()`: Runs `SELECT 1` to verify connectivity (used by the health router).

---

#### `core/ai_engine.py`
The AI execution layer. All functions here talk to external LLM services.

**Initialization (module-level, runs on import):**
- `HF_TOKEN = os.getenv("HF_TOKEN", "")` — HuggingFace API token from environment.
- `Groq_API = os.getenv("GROQ_API_KEY", "")` — Groq API key from environment.
- `initialize_embeddings()` — creates a `HuggingFaceEndpointEmbeddings` instance using `sentence-transformers/all-mpnet-base-v2`. These are used to convert text chunks into dense vectors for similarity search.
- Two LLM instances: `llm` (`llama-3.3-70b-versatile`, the powerful model) and `llm2` (`openai/gpt-oss-120b` alias on Groq, the faster model).

**`process_pdf_rag(pdf_path, system_prompt, input_prompt, model_type)`** — the core RAG function:
1. `PyPDFLoader` reads and parses the PDF file from disk.
2. `RecursiveCharacterTextSplitter` breaks it into 5000-character chunks with 500-character overlap.
3. `FAISS.from_documents()` embeds all chunks into a local vector store.
4. `create_retrieval_chain` wires up: retriever → `create_stuff_documents_chain` (LangChain) → selected LLM.
5. `.invoke({"input": input_prompt})` retrieves the most relevant chunks and feeds them + the prompt to the LLM.
6. Returns `response["answer"]`.

**Feature functions** (all called from `routers/submit.py`):

| Function | Input | What it does |
|---|---|---|
| `ai_detect_text(text)` | raw string | Sends a prompt asking LLM to assess AI probability of the text |
| `ai_detect_pdf(path)` | PDF path | Uses RAG pipeline with AI detection system prompt |
| `grammar_check_text(text)` | raw string | Asks LLM to correct grammar, rate readability out of 100 |
| `paraphrase_text(text, language)` | raw string | Asks LLM to rephrase while preserving meaning |
| `paraphrase_pdf(path, language)` | PDF path | RAG-based paraphrase on full document |
| `detect_plagiarism_pdf(path)` | PDF path | RAG pipeline with a detailed Turnitin-style plagiarism report prompt |
| `summarize_text(text, language)` | raw string | Summarizes text in specified language |
| `summarize_pdf(path, language)` | PDF path | RAG-based summarization |
| `review_pdf(path)` | PDF path | RAG pipeline producing a 12-section academic peer review report |

---

#### `core/security.py`
Pure utility functions — no database access.
- `hash_password(plain)` → bcrypt hash via `passlib`.
- `verify_password(plain, hashed)` → bcrypt verify.
- `create_access_token(data, expires_delta)` → encodes a JWT with `python-jose`, returns `(token_string, expiry_datetime)`. The `sub` claim is set to `str(user_id)`.
- `decode_access_token(token)` → decodes and verifies the JWT; returns `None` if invalid or expired.
- `hash_token(token)` → SHA-256 hash of the JWT string, used to store a fingerprint in the `sessions` table (never store raw JWTs in a DB).

---

#### `core/dependencies.py`
FastAPI dependency functions injected into route handlers via `Depends(...)`.

**`get_current_user(credentials, db)`**:
1. Extracts the Bearer token from the `Authorization` header.
2. Calls `decode_access_token()` to get the payload.
3. Hashes the token and queries the `sessions` table to confirm the session exists, is not revoked, and has not expired.
4. Fetches the `User` ORM row. Raises HTTP 401 if anything fails.
5. Returns the `User` object — routes that depend on this get the authenticated user "for free".

**`require_admin(current_user)`**:
- Wraps `get_current_user`. Raises HTTP 403 if `current_user.role != "admin"`.
- Used by all `/admin/*` routes.

---

#### `routers/auth.py`
Handles the authentication lifecycle. Prefix: `/api/v1/auth`.

- **POST `/register`**: Checks for duplicate email → hashes password → creates `User` row → creates `Session` row (storing SHA-256 of the token, IP, user-agent, expiry) → returns `RegisterResponse` with the JWT.
- **POST `/login`**: Finds user by email → verifies bcrypt password → creates new session → returns `LoginResponse` with token and expiry.
- **POST `/logout`**: Finds the session row by token hash → sets `is_revoked = True`. The token is now permanently invalid even if not yet expired.

---

#### `routers/submit.py`
The largest router (407 lines). Prefix: `/api/v1/submit`. All 6 feature endpoints live here.

Each endpoint follows the same 5-step pattern:
1. Accept either a `text` form field or an uploaded `file` (PDF).
2. Call `_save_submission()` to insert a `Submission` row with `status = "processing"`.
3. If PDF, call `_save_uploaded_file()` which validates the `.pdf` extension and writes the file to the `uploads/` directory.
4. Call the relevant `_stub_*` function (which in turn calls `ai_engine.py`).
5. Insert the result into the feature-specific result table, call `_complete_submission()` to update status, and return the Pydantic response.

**`_save_submission()`** — inserts into `submissions` table and flushes (gets the UUID without committing yet).  
**`_save_uploaded_file()`** — validates file is PDF, saves bytes to disk, returns `(filename, path, size_kb)`.  
**`_complete_submission()`** — sets `status = "completed"` and `completed_at = now()`, then commits.

---

#### `routers/submissions.py`
Prefix: `/api/v1/submissions`.

- **GET `/{submission_id}`**: Fetches a single submission. Researchers can only access their own; admins can access any. Calls `_extract_result()` which checks `feature_type`, accesses the corresponding relationship (`ai_detection_result`, `grammar_check_result`, etc.), and converts the ORM object to a plain dict. Returns `SubmissionDetailResponse`.

---

#### `routers/users.py`
Prefix: `/api/v1/users`.

- **GET `/me`**: Returns the current user's profile (Pydantic `UserProfile` schema).
- **GET `/me/history`**: Queries the `submissions` table filtered by `user_id`, ordered by `created_at DESC`, with a configurable `limit` parameter. Returns total count and array of submissions. This is the endpoint that powers the HomeActivity dashboard stats and recent submissions list.

---

#### `routers/admin.py`
Prefix: `/api/v1/admin`. Both endpoints require `require_admin` dependency (403 if non-admin).

- **GET `/stats`**: Runs aggregation queries — total user count, active users today (from `usage_logs`), total submissions, submissions grouped by `feature_type`, average response time from `usage_logs`.
- **GET `/logs`**: Paginated query on `usage_logs` with optional filters for `user_id`, `status_code`, and `from_date`.

---

#### `routers/health.py`
Prefix: `/api/v1/health`.

- **GET `/health`**: Calls `check_db_connection()` and returns `{"status": "ok"}` or `{"status": "degraded"}`. Used for uptime monitoring.

---

#### `utils/logging_middleware.py`
A Starlette `BaseHTTPMiddleware` that wraps every single request:
1. Records start time.
2. Calls `await call_next(request)` (passes request through to the actual handler).
3. After response, calculates elapsed milliseconds.
4. Opens a DB session and inserts a `UsageLog` row: endpoint path, HTTP method, status code, response time, IP address, and user ID (decoded from the token if present).
5. Always returns the response — logging failures are silently swallowed so they never break a user request.

---

#### `schemas/submission.py`
Pydantic models defining the exact shape of request bodies and response JSON for all 6 features. For example:
- `GrammarCheckTextRequest` has `text: str`, `language: str`, `model_type: str`.
- `GrammarCheckResponse` has `submission_id`, `status`, `corrected_text`, `error_count`, `errors`, `style_suggestions`, `readability_score`.

FastAPI uses these for automatic request validation, serialization, and API docs generation.

---

## 4. Database Layer (PostgreSQL / Supabase)

### Tables and Their Purpose

```
users ──┬── sessions       (1 user : many sessions)
        └── submissions ───┬── ai_detection_results   (1:1)
                           ├── grammar_check_results   (1:1)
                           ├── paraphrase_results      (1:1)
                           ├── plagiarism_results      (1:1)
                           ├── summarization_results   (1:1)
                           └── paper_review_results    (1:1)

usage_logs (loosely linked to users via user_id — no FK constraint)
```

---

#### `models/user.py` → Table: `users`

| Column | Type | Notes |
|---|---|---|
| `user_id` | UUID (PK) | Auto-generated via `uuid.uuid4` |
| `full_name` | String(150) | |
| `email` | String(255) | Unique constraint |
| `password_hash` | String | bcrypt hash, never plaintext |
| `role` | String(20) | `"researcher"` (default) or `"admin"` |
| `institution` | String(255) | Optional |
| `is_active` | Boolean | Soft-disable accounts without deletion |
| `created_at` | DateTime | Server-generated timestamp |
| `updated_at` | DateTime | Auto-updated on row change |

SQLAlchemy relationships: `sessions`, `submissions`, `usage_logs` (all cascade-delete safe).

---

#### `models/session.py` → Table: `sessions`

| Column | Type | Notes |
|---|---|---|
| `session_id` | UUID (PK) | |
| `user_id` | UUID (FK → users) | Cascade deletes when user is deleted |
| `token_hash` | Text | SHA-256 of the JWT (never raw token) |
| `ip_address` | String(45) | IPv4 or IPv6 |
| `user_agent` | Text | Browser/app identifier |
| `created_at` | DateTime | |
| `expires_at` | DateTime | JWT expiry time |
| `is_revoked` | Boolean | Set to True on logout |

Every login creates a new row. Every request validates against this table (not just the JWT signature). This allows server-side logout by setting `is_revoked = True`.

---

#### `models/submission.py` → Table: `submissions`

| Column | Type | Notes |
|---|---|---|
| `submission_id` | UUID (PK) | |
| `user_id` | UUID (FK → users, SET NULL on delete) | Keeps submission even if user is deleted |
| `feature_type` | String(50) | `"ai_detection"`, `"grammar_check"`, etc. |
| `input_type` | String(10) | `"text"` or `"pdf"` |
| `input_text` | Text | Raw text input (null for PDF submissions) |
| `file_name` | String(255) | Original PDF filename |
| `file_path` | Text | Server-side path to saved PDF |
| `file_size_kb` | Integer | |
| `language` | String(30) | Default English |
| `status` | String(20) | `pending` → `processing` → `completed` / `failed` |
| `created_at` | DateTime | |
| `completed_at` | DateTime | Set when AI finishes |

Relationships: one `Submission` has exactly one result row in the matching feature table (enforced by `uselist=False`).

---

#### Result Tables (`models/ai_detection.py`, `grammar_check.py`, etc.)

Each has a `submission_id` UUID foreign key (PK = FK, 1-to-1 enforced) and feature-specific columns. For example, `grammar_check_results` stores `corrected_text`, `error_count`, `errors` (JSON), `style_suggestions` (JSON), `readability_score`. `plagiarism_results` stores `plagiarism_score`, `unique_score`, `matched_sources` (JSON), `total_words`, `plagiarized_words`.

---

#### `models/usage_log.py` → Table: `usage_logs`

Records every HTTP request. Columns: `log_id`, `user_id` (nullable), `endpoint`, `method`, `feature_type`, `response_time_ms`, `status_code`, `ip_address`, `created_at`. Used for admin analytics and debugging.

---

## 5. Local Setup Guide

### Prerequisites
- **Android Studio** (Hedgehog 2023.1+ recommended), SDK API 33+
- **Python 3.11+**
- **Git**

---

### Backend Setup

```bash
# 1. Navigate to the FastAPI app folder
cd MAD_LAB_TIMEPASS/app

# 2. Create and activate a virtual environment
python -m venv venv
# Windows:
.\venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# 3. Install all dependencies
pip install -r app/requirements.txt

# 4. Create a .env file in the app/ folder
# (this file is gitignored — never commit it)
```

Contents of `app/.env`:
```env
HF_TOKEN=your_huggingface_api_token
GROQ_API_KEY=your_groq_api_key
```

```bash
# 5. Start the development server
uvicorn app.main:app --reload --port 8005

# 6. Open the interactive API docs
# http://127.0.0.1:8005/api/v1/docs
```

---

### Android Setup

1. Open **Android Studio** → **Open Project** → select the `MAD_LAB_TIMEPASS` root folder.
2. Wait for Gradle sync to complete.
3. Open `app/src/main/java/com/example/myapplication/ApiClient.java`.
4. Change `BASE_URL` based on your environment:
   - **Emulator** (connecting to local backend): `http://10.0.2.2:8005/api/v1`
   - **Physical device** (same Wi-Fi): `http://192.168.x.x:8005/api/v1`
   - **Live production**: `https://manpat-scholarmate-4m4r.onrender.com/api/v1` (already set)
5. Run on an emulator (API 33+ recommended) or physical device.

---

### Environment Variables Reference

| Variable | Where used | Description |
|---|---|---|
| `HF_TOKEN` | `ai_engine.py` | HuggingFace API token for embeddings |
| `GROQ_API_KEY` | `ai_engine.py` | Groq API key for LLM inference |
| `DATABASE_URL` | `config.py` (optional override) | PostgreSQL connection string |
| `SECRET_KEY` | `config.py` (optional override) | JWT signing secret |

> **Security**: Never commit `.env` to Git. The `.gitignore` file already excludes it.
