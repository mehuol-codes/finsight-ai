# FinSight AI

An AI-powered financial assistant built with Spring Boot and Spring AI. Upload financial reports and ask questions about them, get structured report summaries, analyse a portfolio from a holdings spreadsheet, follow live gold (XAU/USD) market data, or send a chart screenshot for a technical read.

> **Disclaimer:** FinSight AI provides educational analysis only. It is not financial advice or a trading signal service.

## Features

- **Chat with your documents (RAG)** – upload up to 5 files per chat: PDF, Word (DOCX/DOC), Excel (XLSX/XLS), CSV, TXT or Markdown. Answers cite the source file (and page for PDFs).
- **Report summaries** – one click turns a full financial report into an overview, key-figures table, highlights, risks and outlook.
- **Portfolio analysis** – upload a holdings export or portfolio sheet (Excel, Word or CSV). The model extracts the holdings as structured data, the backend calculates invested and current value, profit/loss, allocation by asset type and sector, and concentration warnings, and the UI shows them as stat tiles and charts followed by written insights. Values come from the file itself; there are no live stock prices.
- **Live gold analysis** – XAU/USD candles from Twelve Data with SMA, EMA, RSI, MACD and ATR computed server-side. The assistant fetches this data itself through tool calling, so quoted prices are real.
- **Chart screenshot analysis** – paste or attach a chart image to get trend, support/resistance and pattern analysis.
- **Accounts** – sign up with email and password. Every user sees only their own chats, documents and portfolio analyses.
- **Chat history** – conversations, documents and assistant memory are stored in PostgreSQL, so chats survive restarts. Each chat only sees its own documents.
- **Streaming responses** and background document processing with progress tracking and automatic retry on API rate limits.

## Tech stack

| Area | Technology |
|---|---|
| Backend | Java 23, Spring Boot 4.1, Spring AI 2.0, Spring Security 7 |
| LLM | Google Gemini (`gemini-3.8-flash`) |
| Embeddings | `gemini-embedding-001` (1536 dimensions) |
| Vector store | PostgreSQL 16 + pgvector |
| Market data | Twelve Data API |
| Frontend | HTML, CSS, vanilla JavaScript, TradingView Lightweight Charts |

## Architecture

The backend follows a layered architecture:

```
dev.mehuol.finsight
├── controller/   REST endpoints
├── service/      business logic (chat, documents, summaries, portfolio, market data)
├── repository/   SQL queries (JdbcTemplate)
├── client/       external API clients (Twelve Data)
├── tool/         functions the LLM can call (gold market data)
├── config/       chat clients and prompt setup
├── security/     sign-in, sessions, CSRF and password hashing
├── dto/          request and response records
├── event/        application events between services
├── exception/    custom exceptions and global error handling
└── util/         technical indicators, portfolio calculations and helpers
```

Prompts are kept as plain text files in `src/main/resources/prompts/`.

**How document Q&A works:** an uploaded file is read (PDFs page by page, Word and Excel files with Apache Tika), split into chunks, and each chunk is embedded and stored in pgvector together with its chat id and file name. When a user asks a question in a chat that has documents, the most relevant chunks from that chat are retrieved and passed to the model along with the question.

**How portfolio analysis works:** the model only reads the holdings out of the file (structured output). Every number shown – totals, profit/loss, weights, allocation – is calculated in Java, and the model then comments on those calculated figures.

**How accounts work:** Spring Security with session cookies. Passwords are stored as BCrypt hashes. Every chat row has an owner, and each endpoint that takes a chat id checks that it belongs to the signed-in user; another user's chat is answered with 404. Requests that change data are protected against CSRF with the `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header pair. Chats created before accounts were added are given to the first account that signs up.

## Getting started

### Prerequisites

- JDK 23 or newer
- Docker (used to run PostgreSQL + pgvector)
- A Gemini API key – free from [Google AI Studio](https://aistudio.google.com)
- A Twelve Data API key – free from [twelvedata.com](https://twelvedata.com) (only needed for the gold chart and gold analysis)

### Environment variables

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | Yes | Google Gemini API key |
| `TWELVE_DATA_API_KEY` | No | Enables live gold data |
| `GEMINI_CHAT_MODEL` | No | Overrides the chat model (default `gemini-3.8-flash`) |
| `GEMINI_EMBEDDING_MODEL` | No | Overrides the embedding model (default `gemini-embedding-001`) |

### Run

```bash
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`, or run `FinSightApplication` from your IDE.

Spring Boot starts the PostgreSQL container from `compose.yaml` automatically and creates the tables on startup. Open [http://localhost:8080](http://localhost:8080) and create an account.

### Tests

```bash
./mvnw test
```

Docker must be running: the integration tests (application startup, sign-up/sign-in, CSRF, per-user access, and a full browser-style HTTP flow) start a throwaway PostgreSQL container with Testcontainers, so they never touch your development database. No API keys are needed. The unit tests (indicators, portfolio calculations, document reading, error handling) need nothing external.

GitHub Actions runs `mvnw verify` on every push to `main` and on pull requests (`.github/workflows/ci.yml`).

## API overview

All endpoints except sign-up, sign-in and `/api/auth/me` require a signed-in user.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account (JSON `email`, `password`) |
| `POST` | `/api/auth/login` | Sign in (form fields `email`, `password`); starts a session |
| `POST` | `/api/auth/logout` | Sign out |
| `GET` | `/api/auth/me` | The signed-in user, or 401 |
| `POST` | `/api/chat` | Send a message (optional chart image); streams the answer as NDJSON |
| `GET` | `/api/chats` | List chats |
| `GET` | `/api/chats/{id}/messages` | Messages of a chat |
| `PATCH` | `/api/chats/{id}` | Rename a chat |
| `DELETE` | `/api/chats/{id}` | Delete a chat with its documents and memory |
| `GET` | `/api/documents?conversationId=` | Documents of a chat, including upload progress |
| `POST` | `/api/documents?conversationId=` | Upload a file (processed in the background) |
| `POST` | `/api/documents/{name}/summary?conversationId=` | Stream a summary of a document |
| `POST` | `/api/documents/{name}/portfolio?conversationId=` | Analyse holdings: streams the computed metrics, then insights |
| `DELETE` | `/api/documents/{name}?conversationId=` | Delete a document or cancel its upload |
| `GET` | `/api/gold/chart?interval=1h` | XAU/USD candles with SMA 20/50 |

## Limitations

- The Gemini free tier limits requests per minute and per day. Large PDFs may take a few minutes to process; the UI shows progress while the app waits out rate limits.
- Scanned PDFs without a text layer are not supported.
- Portfolio analysis uses the prices and values in the uploaded file; live market data covers gold only.
- Portfolio amounts are shown in the currency detected in the file (from symbols such as ₹, $ or €, header labels, or the exchange the holdings trade on, e.g. PSX → PKR, NSE/BSE → INR). Because "Rs" is used for both the Pakistani and the Indian rupee, it is resolved from context such as the exchange or company names. If the currency can't be detected, USD is assumed and a warning is shown. A file that mixes currencies is reported in the currency most holdings use; amounts are never converted.
- Sessions are kept in memory, so restarting the app signs everyone out.
- There is no password reset or email verification yet.

## Roadmap

- Rate limiting per user
- Docker image and cloud deployment
