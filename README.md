# 🧠 MeetingAI — Backend

> AI-powered meeting summarizer with **RAG**, semantic search, and chat — built with Spring Boot, Ollama, and ChromaDB. Runs 100% locally, no API keys, no cloud.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=springboot)
![Ollama](https://img.shields.io/badge/Ollama-llama3-black)
![ChromaDB](https://img.shields.io/badge/ChromaDB-vector%20store-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

🔗 **Frontend repo:** [meeting-ai-frontend](https://github.com/Shashank-018/meeting-ai-frontend)

🔗 **Live demo:** https://meeting-ai-frontend-khaki.vercel.app
🔗 **Backend API:** https://meeting-ai-backend-s7xe.onrender.com

---

## What it does

Paste an IT project meeting transcript and get a structured breakdown — executive summary, action items with owners and deadlines, risks, decisions, and technical tasks. Every meeting is saved and embedded, so you can then **search past meetings by meaning** and **chat with your meeting history** using Retrieval Augmented Generation (RAG).

---

## Architecture

```
┌──────────────┐     REST      ┌──────────────────────┐
│ React        │ ────────────► │  Spring Boot API     │
│ Frontend     │               │  (port 8000)         │
└──────────────┘               └──────────┬───────────┘
                                           │
              ┌────────────────────────────┼────────────────────────┐
              ▼                            ▼                        ▼
     ┌────────────────┐          ┌──────────────────┐     ┌──────────────────┐
     │ Ollama         │          │ SQLite           │     │ ChromaDB         │
     │ llama3         │          │ meetings.db      │     │ vector store     │
     │ nomic-embed    │          │ (content)        │     │ (embeddings)     │
     │ (port 11434)   │          │                  │     │ (port 8001)      │
     └────────────────┘          └──────────────────┘     └──────────────────┘
```

- **llama3** generates summaries and answers chat questions
- **nomic-embed-text** converts meeting text into 768-dimension vectors
- **SQLite** stores the full readable content
- **ChromaDB** stores the vectors for semantic similarity search

---

## Features

| Feature | Endpoint | Description |
|---------|----------|-------------|
| Summarize | `POST /summarize` | Structured summary + auto-save + auto-embed |
| History | `GET /history` | List all past meetings |
| Get meeting | `GET /history/{id}` | Full details of one meeting |
| Delete | `DELETE /history/{id}` | Remove from SQLite + ChromaDB |
| Semantic search | `GET /search?q=...` | Find meetings by meaning, not keywords |
| RAG chat | `POST /chat` | Ask questions answered from your meeting history |

---

## Tech stack

- **Java 21** + **Spring Boot 3.2**
- **Spring Data JPA** + **SQLite** for persistence
- **Ollama** (llama3 + nomic-embed-text) for local LLM inference
- **ChromaDB** for vector storage and similarity search
- **Spring WebFlux** WebClient for async HTTP calls

---

## Getting started

### Prerequisites

- Java 21+
- [Ollama](https://ollama.com) installed
- Python 3 (for ChromaDB)

### 1. Pull the models

```bash
ollama pull llama3
ollama pull nomic-embed-text
```

### 2. Start ChromaDB

```bash
pip install chromadb
chroma run --host localhost --port 8001 --path ./chroma-data
```

### 3. Run the backend

```bash
./mvnw spring-boot:run
```

The API starts on **http://localhost:8000**. Databases are auto-created on first run.

### 4. Verify

```bash
curl http://localhost:8000
# {"status":"Meeting AI backend running — Phase 2 RAG enabled"}
```

---

## Project structure

```
src/main/java/com/meetingai/
├── MeetingAiApplication.java      # entry point
├── controller/
│   └── SummarizeController.java   # all REST endpoints
├── service/
│   ├── OllamaService.java         # llama3 summarization
│   ├── PromptService.java         # prompt engineering
│   ├── EmbeddingService.java      # text → vector via Ollama
│   ├── ChromaService.java         # vector store operations
│   ├── RagService.java            # retrieval + generation
│   └── MeetingHistoryService.java # SQLite persistence
├── model/
│   ├── MeetingRecord.java         # JPA entity
│   ├── MeetingSummary.java        # AI response model
│   ├── ActionItem.java
│   └── TranscriptRequest.java
└── repository/
    └── MeetingRepository.java     # Spring Data JPA
```

---

## What I learned building this

- Implementing a **RAG pipeline** from scratch — embeddings, vector storage, retrieval, and augmented generation
- Running **local LLMs** with Ollama instead of relying on paid APIs
- Vector similarity search with **cosine distance** in ChromaDB
- Clean layered architecture in Spring Boot (controller → service → repository)

---

## License

MIT
