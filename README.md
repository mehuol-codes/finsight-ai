# FinSight AI

An AI-powered financial assistant built with Spring Boot and Spring AI. Upload financial reports and ask questions about them, get structured report summaries, analyse live gold (XAU/USD) market data, or send a chart screenshot for a technical read.

> **Disclaimer:** FinSight AI provides educational analysis only. It is not financial advice or a trading signal service.

## Features

- **Chat with your documents (RAG)** – upload up to 5 PDF, TXT or Markdown files per chat. Answers cite the source file and page.
- **Report summaries** – one click turns a full financial report into an overview, key-figures table, highlights, risks and outlook.
- **Live gold analysis** – XAU/USD candles from Twelve Data with SMA, EMA, RSI, MACD and ATR computed server-side. The assistant fetches this data itself through tool calling, so quoted prices are real.
- **Chart screenshot analysis** – paste or attach a chart image to get trend, support/resistance and pattern analysis.
- **Chat history** – conversations, documents and assistant memory are stored in PostgreSQL, so chats survive restarts. Each chat only sees its own documents.
- **Streaming responses** and background document processing with progress tracking and automatic retry on API rate limits.

## Tech stack

| Area | Technology |
|---|---|
| Backend | Java 23, Spring Boot 4.1, Spring AI 2.0 |
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
├── service/      business logic (chat, documents, summaries, market data)
├── repository/   SQL queries (JdbcTemplate)
├── client/       external API clients (Twelve Data)
├── tool/         functions the LLM can call (gold market data)
├── config/       chat clients and prompt setup
├── dto/          request and response records
├── event/        application events between services
├── exception/    custom exceptions and global error handling
└── util/         technical indicators and helpers
```

Prompts are kept as plain text files in `src/main/resources/prompts/`.

**How document Q&A works:** an uploaded file is split into chunks, each chunk is embedded and stored in pgvector together with its chat id and file name. When a user asks a question in a chat that has documents, the most relevant chunks from that chat are retrieved and passed to the model along with the question.

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

Spring Boot starts the PostgreSQL container from `compose.yaml` automatically and creates the tables on startup. Open [http://localhost:8080](http://localhost:8080).

### Tests

```bash
./mvnw test
```

`FinSightApplicationTests` loads the full application context, so it needs Docker and a `GEMINI_API_KEY`.

## API overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | Send a message (optional chart image); streams the answer as NDJSON |
| `GET` | `/api/chats` | List chats |
| `GET` | `/api/chats/{id}/messages` | Messages of a chat |
| `PATCH` | `/api/chats/{id}` | Rename a chat |
| `DELETE` | `/api/chats/{id}` | Delete a chat with its documents and memory |
| `GET` | `/api/documents?conversationId=` | Documents of a chat, including upload progress |
| `POST` | `/api/documents?conversationId=` | Upload a file (processed in the background) |
| `POST` | `/api/documents/{name}/summary?conversationId=` | Stream a summary of a document |
| `DELETE` | `/api/documents/{name}?conversationId=` | Delete a document or cancel its upload |
| `GET` | `/api/gold/chart?interval=1h` | XAU/USD candles with SMA 20/50 |

## Limitations

- The Gemini free tier limits requests per minute and per day. Large PDFs may take a few minutes to process; the UI shows progress while the app waits out rate limits.
- Scanned PDFs without a text layer are not supported.
- There is no authentication yet, so this is meant for local, single-user use.

## Roadmap

- SIP, EMI and retirement calculators as assistant tools
- Excel and Word document support
- More markets (stocks, other forex pairs)
- User accounts with Spring Security
- Docker image and cloud deployment
