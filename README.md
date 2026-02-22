# 🏥 MedBot AI — Enterprise-Grade Medical Assistant

> **An AI-powered multilingual medical assistant** built with Spring Boot 3, Google Gemini 2.5 Flash,
> AssemblyAI, and Vue.js 3 — supporting all 22 Indian scheduled languages with real-time translation,
> voice input, text-to-speech, and streaming responses.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-green?logo=spring)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-42b883?logo=vue.js)](https://vuejs.org/)
[![Gemini](https://img.shields.io/badge/Gemini-2.5%20Flash-blue?logo=google)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Deploy](https://img.shields.io/badge/Deploy-Render-purple?logo=render)](https://render.com)

---

## 📸 Previous Version Screenshots

![WhatsApp Image 2025-09-24 at 10 52 34_c7938592](https://github.com/user-attachments/assets/0267c2f8-e78f-4059-8868-74bdc22d7704)
![WhatsApp Image 2025-09-24 at 10 52 34_cc279283](https://github.com/user-attachments/assets/bd55e710-b5e6-45e5-8920-b742285e1b90)
![WhatsApp Image 2025-09-24 at 10 52 35_ca796fd4](https://github.com/user-attachments/assets/3025ece6-8e76-4538-9d42-e89633ac1baf)

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Deployment](#-deployment)
- [Environment Variables](#-environment-variables)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 🤖 AI-Powered Medical Chat
- **Google Gemini 2.5 Flash** for intelligent, context-aware medical responses
- **Conversation memory** — maintains up to 20 messages per session with 10-message context window
- **Medical system prompt** — specialized for health queries with appropriate disclaimers
- **Streaming responses (SSE)** — real-time typewriter effect via Server-Sent Events

### 🌐 Multilingual Support (22 Indian Languages)
- All 22 Indian Scheduled Languages + English
- **Real-time translation** of any message via Gemini AI
- **Language detection** — auto-identifies input language
- **Smart routing** — non-English input auto-translated to English for AI processing, response translated back
- Languages: Hindi, Bengali, Telugu, Marathi, Tamil, Urdu, Gujarati, Kannada, Malayalam, Odia, Punjabi, Assamese, Maithili, Santali, Kashmiri, Nepali, Sindhi, Dogri, Konkani, Manipuri, Bodo, Sanskrit

### 🔊 Voice & Audio
- **Voice input** — speak your medical queries using Web Speech API
- **"Listen to this"** — text-to-speech for any AI response with adjustable speed (0.5x–2x)
- **AssemblyAI integration** — server-side speech-to-text for audio file uploads

### 🎨 Professional UI/UX
- **Vue.js 3** reactive frontend (CDN-based, no build step)
- **Dark/Light theme** toggle with CSS custom properties
- **Chat sidebar** with session management (create, rename, delete)
- **Markdown rendering** with `marked.js`
- **Copy to clipboard** for any message
- **Quick action buttons** for common health topics
- **Emergency helpline panel** (Indian emergency numbers)
- **Fully responsive** — mobile-first design

### 🏗️ Enterprise Architecture
- **Layered architecture** — controller → service → model
- **Global exception handling** with `@RestControllerAdvice`
- **Input validation** with `jakarta.validation`
- **Caffeine caching** for translations (500 entries, 1-hour TTL)
- **CORS configuration** for cross-origin deployment
- **Health checks** via Spring Actuator (`/actuator/health`)
- **OpenAPI/Swagger** auto-generated docs (`/swagger-ui.html`)
- **Teaching-quality comments** on every Java file

---

## 🏛️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Vue.js 3 Frontend                     │
│              (index.html + app.js + app.css)             │
│         CDN: Vue 3, marked.js, Web Speech API            │
└────────────────────────┬────────────────────────────────┘
                         │  REST / SSE
                         ▼
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot 3.5.6                        │
│  ┌──────────────┐ ┌────────────────┐ ┌───────────────┐  │
│  │ChatController│ │TranslController│ │HealthController│  │
│  └──────┬───────┘ └───────┬────────┘ └───────────────┘  │
│         │                 │                               │
│  ┌──────▼───────┐ ┌───────▼────────┐                     │
│  │GeminiService │ │TranslService   │  ┌───────────────┐  │
│  │              │ │(+Caffeine Cache)│  │ChatMemService │  │
│  └──────┬───────┘ └───────┬────────┘  └───────────────┘  │
│         │                 │                               │
│  ┌──────▼─────────────────▼────────┐                     │
│  │  AssemblyAIService (STT)        │                     │
│  └─────────────────────────────────┘                     │
│                                                           │
│  Config: WebConfig, CacheConfig    │  DTOs, Models, Utils │
│  Exception: GlobalExceptionHandler │  Enums: Language     │
└────────────────────────┬────────────────────────────────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌──────────┐ ┌────────┐ ┌──────────┐
        │Gemini API│ │Assembly│ │Caffeine  │
        │2.5 Flash │ │AI  API │ │In-Memory │
        └──────────┘ └────────┘ └──────────┘
```

### Request Flow (Chat with Translation)

```
User (Hindi) → ChatController → TranslationService.detectLanguage()
    → TranslationService.translate(Hindi→English)
    → GeminiService.generateContent(English prompt + history)
    → TranslationService.translate(English→Hindi)
    → ChatResponse (Hindi) → User
```

---

## 🛠️ Tech Stack

| Layer        | Technology                  | Purpose                              |
|--------------|-----------------------------|--------------------------------------|
| **Backend**  | Spring Boot 3.5.6          | REST API framework                   |
| **Language** | Java 17                    | Text blocks, enhanced switch, etc.   |
| **AI**       | Google Gemini 2.5 Flash    | Chat, translation, language detection|
| **STT**      | AssemblyAI                 | Server-side speech-to-text           |
| **Frontend** | Vue.js 3 (CDN)            | Reactive UI, Options API             |
| **Markdown** | marked.js                  | Chat message rendering               |
| **TTS/STT**  | Web Speech API             | Browser-native voice features        |
| **Cache**    | Caffeine                   | Translation & detection caching      |
| **Docs**     | SpringDoc OpenAPI 2.8      | Auto-generated Swagger UI            |
| **Health**   | Spring Actuator            | Health & info endpoints              |
| **Build**    | Maven                      | Dependency management & build        |
| **Deploy**   | Docker + Render            | Containerized cloud deployment       |

---

## 📁 Project Structure

```
GeminiWebApp/
├── src/main/java/com/subho/medbot/
│   ├── MedBotApplication.java              # Spring Boot entry point
│   ├── config/
│   │   ├── WebConfig.java                  # CORS, RestTemplate, ObjectMapper
│   │   └── CacheConfig.java               # Caffeine cache configuration
│   ├── controller/
│   │   ├── ChatController.java             # Chat endpoints (REST + SSE)
│   │   ├── TranslationController.java      # Translation & language APIs
│   │   └── HealthController.java           # Custom health endpoint
│   ├── service/
│   │   ├── GeminiService.java              # Gemini AI integration
│   │   ├── TranslationService.java         # Multi-language translation
│   │   ├── AssemblyAIService.java          # Speech-to-text
│   │   └── ChatMemoryService.java          # Conversation storage
│   ├── dto/
│   │   ├── request/  (ChatRequest, TranslateRequest)
│   │   └── response/ (ChatResponse, TranslateResponse, LanguageInfo, ErrorResponse)
│   ├── model/
│   │   ├── Language.java                   # 23-language enum
│   │   └── ChatMessage.java               # Chat message POJO
│   ├── exception/
│   │   ├── ApiException.java
│   │   ├── ServiceUnavailableException.java
│   │   └── GlobalExceptionHandler.java
│   └── util/
│       └── TextUtils.java                  # String utilities
├── src/main/resources/
│   ├── application.properties              # Base config (env var placeholders)
│   ├── application-dev.properties          # Dev profile
│   ├── application-prod.properties         # Prod profile
│   └── static/
│       ├── index.html                      # Vue.js 3 SPA
│       ├── css/app.css                     # Themes, animations, responsive
│       └── js/app.js                       # Vue app logic
├── Dockerfile                              # Multi-stage Docker build
├── render.yaml                             # Render deployment config
├── system.properties                       # JDK version for Render/Heroku
├── .env.example                            # Environment variable template
└── pom.xml                                 # Maven dependencies
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** — [Download OpenJDK](https://adoptium.net/)
- **Maven 3.8+** — (included via `mvnw` wrapper)
- **Gemini API Key** — [Get one free](https://aistudio.google.com/apikey)
- **AssemblyAI API Key** — [Sign up](https://www.assemblyai.com/) (free tier available)

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/GeminiWebApp.git
cd GeminiWebApp
```

### 2. Set Environment Variables

```bash
cp .env.example .env

export GEMINI_API_KEY=your_gemini_api_key_here
export ASSEMBLYAI_API_KEY=your_assemblyai_api_key_here
```

### 3. Run the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Open in Browser

- **App:** http://localhost:8084
- **Swagger:** http://localhost:8084/swagger-ui.html
- **Health:** http://localhost:8084/actuator/health

---

## 📡 API Documentation

### Chat Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/chat` | Send a chat message, get AI response |
| `POST` | `/api/chat/voice` | Upload audio for STT + AI response |
| `GET`  | `/api/chat/stream?prompt=...&sessionId=...` | SSE streaming response |
| `GET`  | `/api/chat/sessions` | List all chat sessions |
| `DELETE`| `/api/chat/sessions/{id}` | Delete a chat session |

### Translation Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/languages` | List all supported languages |
| `POST` | `/api/translate` | Translate text between languages |
| `POST` | `/api/detect-language` | Detect input language |

### Example: Chat Request

```json
POST /api/chat
{
  "prompt": "What are the symptoms of diabetes?",
  "sessionId": "optional-session-id",
  "language": "en"
}
```

### Example: Translate Request

```json
POST /api/translate
{
  "text": "What are the symptoms of diabetes?",
  "sourceLanguage": "en",
  "targetLanguage": "hi"
}
```

---

## ☁️ Deployment

### Deploy to Render (Recommended)

1. Push to GitHub
2. Create a [Render](https://render.com) account
3. **New Web Service** → Connect your GitHub repo
4. Render auto-detects the `render.yaml` configuration
5. Add environment variables: `GEMINI_API_KEY`, `ASSEMBLYAI_API_KEY`
6. Deploy — Render builds the Docker image automatically

### Deploy with Docker

```bash
docker build -t medbot-ai .
docker run -p 8084:8084 \
  -e GEMINI_API_KEY=your_key \
  -e ASSEMBLYAI_API_KEY=your_key \
  medbot-ai
```

---

## 🔧 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GEMINI_API_KEY` | ✅ | — | Google Gemini API key |
| `ASSEMBLYAI_API_KEY` | ✅ | — | AssemblyAI API key |
| `PORT` | ❌ | `8084` | Server port |
| `SPRING_PROFILES_ACTIVE` | ❌ | — | `dev` or `prod` |

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push: `git push origin feature/amazing-feature`
5. Open a Pull Request

All Java files include **teaching-quality comments** — please maintain this standard.

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

**Built by Subho** — Full Stack Java Developer

_Enterprise-grade portfolio project demonstrating Spring Boot microservice architecture, AI/LLM integration, multilingual design, Vue.js frontend, and cloud-native deployment._

⭐ **Star this repo if you found it helpful!**
