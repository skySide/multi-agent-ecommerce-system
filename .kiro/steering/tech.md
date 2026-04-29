# Technology Stack

## Backend (Java)

- **Framework**: Spring Boot 3.2.5 + Spring AI 1.0.0-M6
- **Java Version**: 17
- **ORM**: MyBatis-Plus 3.5.7
- **LLM Integration**: Spring AI OpenAI (compatible with SiliconFlow/MiniMax APIs)
- **Reactive**: Spring WebFlux for async operations

## Frontend

- **Framework**: React 18 + Vite 5
- **UI Library**: Ant Design 5
- **Routing**: React Router 6
- **Build**: Vite with ES modules

## Data Stores

- **MySQL 8.0** - Business data (users, products, orders, behaviors)
- **Redis 7** - Real-time feature store, session cache
- **Milvus v2.4** - Vector database for product/user embeddings

## LLM Provider

- **Primary**: SiliconFlow API (DeepSeek-V3 for chat, BAAI/bge-large-zh-v1.5 for embeddings)
- **Compatible**: OpenAI API format

## Build Commands

### Java Backend
```bash
cd java
mvn clean install          # Build project
mvn spring-boot:run        # Run development server (port 8080)
mvn test                   # Run tests
```

### Frontend
```bash
cd frontend
npm install                # Install dependencies
npm run dev                # Development server
npm run build              # Production build
npm run lint               # Run ESLint
```

### Infrastructure
```bash
docker-compose up -d       # Start Redis, Milvus, MySQL
docker-compose down        # Stop services
```

## Environment Variables

- `SILICONFLOW_API_KEY` - LLM API key
- `SILICONFLOW_BASE_URL` - API endpoint (default: https://api.siliconflow.cn)
- `SILICONFLOW_CHAT_MODEL` - Chat model (default: deepseek-ai/DeepSeek-V3)
- `SILICONFLOW_EMBEDDING_MODEL` - Embedding model (default: BAAI/bge-large-zh-v1.5)

## API Base URL

- Backend: `/api/v1/`
- Frontend proxy configured in Vite
