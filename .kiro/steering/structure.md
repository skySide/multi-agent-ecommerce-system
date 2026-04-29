# Project Structure

```
multi-agent-ecommerce/
├── java/                          # Java Spring Boot backend
│   ├── src/main/java/com/ecommerce/
│   │   ├── agent/                 # 4 specialized agents
│   │   │   ├── BaseAgent.java     # Abstract base with retry/timeout/fallback
│   │   │   ├── UserProfileAgent.java
│   │   │   ├── ProductRecAgent.java
│   │   │   ├── MarketingCopyAgent.java
│   │   │   └── InventoryAgent.java
│   │   ├── controller/            # REST API endpoints
│   │   ├── service/               # Business logic layer
│   │   ├── mapper/                # MyBatis-Plus mappers
│   │   ├── entity/                # JPA entities
│   │   ├── dto/                   # Data transfer objects
│   │   ├── vo/                    # View objects for responses
│   │   ├── config/                # Spring configuration
│   │   ├── common/                # Shared utilities, Result wrapper
│   │   │   └── enums/ErrorCode.java
│   │   └── exception/             # Global exception handling
│   └── src/main/resources/
│       └── application.yml        # Configuration
│
├── frontend/                      # React frontend
│   └── src/
│       ├── components/            # Reusable components
│       │   └── ChatWidget.jsx     # AI chat interface
│       ├── pages/                 # Route pages
│       │   ├── HomePage.jsx
│       │   ├── LoginPage.jsx
│       │   ├── ProductDetailPage.jsx
│       │   ├── SearchPage.jsx
│       │   └── UserCenterPage.jsx
│       └── services/
│           └── api.js             # Centralized API client
│
├── database/
│   ├── mysql/
│   │   ├── schema.sql             # Database schema
│   │   └── init_data.sql          # Seed data
│   └── milvus/
│       └── collections.yaml       # Vector collection config
│
├── docs/                          # Documentation
│   ├── architecture.md            # System design
│   ├── project-plan.md            # Implementation phases
│   └── interview-guide.md         # Interview materials
│
└── docker-compose.yml             # Infrastructure services
```

## Key Conventions

### Package Naming
- Base package: `com.ecommerce`
- Layered by function: `controller`, `service`, `mapper`, `entity`, `dto`, `vo`

### Entity IDs
- Use business IDs (e.g., `productId`, `userId`) as VARCHAR(32)
- Auto-increment `id` as internal primary key
- ID format: prefix + timestamp (e.g., `P1735123456789`)

### API Response Format
All endpoints return unified `Result<T>` wrapper:
```json
{
  "code": 200,
  "message": "success",
  "data": {...},
  "timestamp": 1735123456789
}
```

### Agent Pattern
All agents extend `BaseAgent`:
- Implement `execute(Map<String, Object> params)`
- Use `runAsync()` for parallel execution
- Provide `fallback()` for graceful degradation
- Track error rates for circuit breaking

### Frontend API
- Centralized in `services/api.js`
- Auto-includes JWT token from localStorage
- Throws on non-200 response codes
