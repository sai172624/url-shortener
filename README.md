# URL Shortener

A production-style URL shortener with authentication, built with Spring Boot and React.

## Features

- **Base62 Short Codes**: Collision-free short URLs from numeric ID allocation
- **Redis Cache-First Redirects**: High-performance redirects with PostgreSQL fallback
- **Async Click Analytics**: Kafka-powered event processing for analytics
- **JWT Authentication**: Secure auth with refresh token rotation
- **User Ownership**: Per-user isolation with role-based access

## Tech Stack

- **Backend**: Java 21 · Spring Boot 3.x · PostgreSQL · Redis · Apache Kafka
- **Frontend**: React · Vite · Recharts
- **Testing**: JUnit 5 · Mockito · Testcontainers
- **Deployment**: Docker Compose

## Getting Started

### Prerequisites
- JDK 21
- Docker & Docker Compose
- Maven 3.8+

### Local Development

```bash
# Start infrastructure (Postgres, Redis, Kafka)
docker compose up -d

# Run backend
cd backend
./mvnw spring-boot:run

# Run frontend (when ready)
cd frontend
npm install
npm run dev
```

### API Endpoints

```
POST   /api/v1/urls           Create URL (auth required)
GET    /api/v1/urls           List my URLs (auth required)
GET    /{shortCode}           Redirect (public)
POST   /api/v1/auth/register  Register
POST   /api/v1/auth/login     Login
GET    /actuator/health       Health check
```

## Architecture

See [docs/](docs/) for detailed documentation including:
- [Architecture Decision Records (ADRs)](docs/ADRs/)
- [Learnings & Cheat Sheets](docs/LEARNINGS.md)
- [Project Brief](docs/PROJECT_BRIEF.md)

## Testing

```bash
# Run all tests
./mvnw verify

# Run with Testcontainers
./mvnw test -P integration-test
```

## License

MIT
