# Social Feed MVP - Event-Driven Architecture

A minimal event-driven social feed application built with Spring Boot, PostgreSQL, and Apache Kafka. Users can follow others, create posts, and view personalized timelines that are materialized asynchronously through Kafka.

## Features

- **User Management**: Registration and JWT-based authentication
- **Follow System**: Users can follow/unfollow other users
- **Post Creation**: Create text posts (up to 1000 characters)
- **Event-Driven Timeline**: Posts are asynchronously materialized to followers' timelines via Kafka
- **Idempotent Consumer**: Kafka consumer handles restarts without duplicating data
- **Paginated Timeline**: Retrieve timeline posts with pagination support

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.2.0
- **Database**: PostgreSQL 15
- **Message Broker**: Apache Kafka 7.4.0
- **Security**: Spring Security with JWT tokens
- **Containerization**: Docker Compose

## Architecture

```
User creates post → Post Service → Kafka Topic (post-created)
                                        ↓
Timeline Consumer → Materializes post to all followers' timelines
                                        ↓
                            Timeline Database (PostgreSQL)
```

### Key Components

1. **Post Service**: Creates posts and publishes `PostCreatedEvent` to Kafka
2. **Timeline Consumer**: Listens to post events and materializes them to followers' timelines
3. **Idempotency**: Uses `ProcessedEvent` table to track processed event IDs
4. **Manual Acknowledgment**: Kafka consumer uses manual acknowledgment to ensure delivery

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Node.js and npm (for the frontend)
- PostgreSQL and Kafka (local services or run via Docker separately)

### Setup and Run

1. **Start Infrastructure**

Ensure PostgreSQL and Kafka are running (managed externally or via Docker).

2. **Build the Backend**

```powershell
cd backend
mvn clean install
```

3. **Run the Backend**

```powershell
cd backend
mvn spring-boot:run
```

The backend will start on `http://localhost:8080` by default (see `backend/.env.example`).

### Frontend (React + Tailwind)

The frontend is located in `frontend/` and talks to the backend API at `http://localhost:8080/api`.

1. **Install frontend dependencies**

```powershell
cd frontend
npm install
```

2. **Run frontend in dev mode**

```powershell
npm run dev
```

3. Open `http://localhost:5173`

If needed, copy the example env files into real `.env` files:

```powershell
copy backend\.env.example backend\.env
copy frontend\.env.example frontend\.env
```

## API Endpoints

### Authentication

**Register**
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123"
}
```

**Login**
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "password123"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "username": "john_doe",
  "userId": 1
}
```

### User Management

**Get User by ID**
```http
GET /api/users/{userId}
Authorization: Bearer {token}
```

**Follow User**
```http
POST /api/users/{userId}/follow
Authorization: Bearer {token}
```

**Unfollow User**
```http
DELETE /api/users/{userId}/unfollow
Authorization: Bearer {token}
```

**Get Followers**
```http
GET /api/users/{userId}/followers
Authorization: Bearer {token}
```

**Get Following**
```http
GET /api/users/{userId}/following
Authorization: Bearer {token}
```

### Posts

**Create Post**
```http
POST /api/posts
Authorization: Bearer {token}
Content-Type: application/json

{
  "content": "Hello, this is my first post!"
}
```

**Get Post by ID**
```http
GET /api/posts/{postId}
Authorization: Bearer {token}
```

**Get User's Posts**
```http
GET /api/posts/user/{userId}
Authorization: Bearer {token}
```

### Timeline

**Get My Timeline**
```http
GET /api/timeline?page=0&size=20
Authorization: Bearer {token}
```

**Get User's Timeline**
```http
GET /api/timeline/{userId}?page=0&size=20
Authorization: Bearer {token}
```

## Testing the Application

### Example Workflow

1. **Register two users** (Alice and Bob)
2. **Login as Alice** and get JWT token
3. **Alice follows Bob**
4. **Login as Bob**
5. **Bob creates a post**
6. **Check Alice's timeline** - Bob's post should appear asynchronously

### Using cURL

```powershell
# Register Alice
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"alice","email":"alice@test.com","password":"pass123"}'

# Register Bob
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"bob","email":"bob@test.com","password":"pass123"}'

# Login as Alice (save the token)
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"alice","password":"pass123"}'

# Alice follows Bob (use Alice's token and Bob's userId)
curl -X POST http://localhost:8080/api/users/2/follow `
  -H "Authorization: Bearer {ALICE_TOKEN}"

# Bob creates a post (use Bob's token)
curl -X POST http://localhost:8080/api/posts `
  -H "Authorization: Bearer {BOB_TOKEN}" `
  -H "Content-Type: application/json" `
  -d '{"content":"Hello from Bob!"}'

# Check Alice's timeline
curl http://localhost:8080/api/timeline `
  -H "Authorization: Bearer {ALICE_TOKEN}"
```

## Database Schema

### Core Tables

- **users**: User accounts
- **follows**: User follow relationships
- **posts**: User posts
- **timeline_entries**: Materialized timeline (denormalized)
- **processed_events**: Tracks processed Kafka events for idempotency

### Idempotency Strategy

The system prevents duplicate timeline entries through:
1. **Event IDs**: Each Kafka event has a unique `eventId` (UUID)
2. **Processed Events Table**: Tracks which events have been processed
3. **Manual Acknowledgment**: Only acknowledges Kafka messages after successful processing
4. **Database Constraints**: Unique constraint on `(user_id, post_id)` in timeline_entries

## Configuration

Key configuration in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/socialfeed
    username: 
    password: 
  
  kafka:
    bootstrap-servers: localhost:9092

jwt:
  secret: your-256-bit-secret
  expiration: 86400000  # 24 hours
```

## Future Enhancements

- Semantic search on posts
- Post likes and comments
- Real-time notifications
- Media attachments
- Hashtags and mentions
- User profiles with avatars
- Caching layer (Redis)
- Monitoring and metrics

## Troubleshooting

### Kafka Consumer Not Processing

- Check Kafka logs: `docker logs social-feed-kafka`
- Verify topic exists: `docker exec social-feed-kafka kafka-topics --list --bootstrap-server localhost:9092`
- Check consumer group: Application logs will show "Received post created event"

### Database Connection Issues

- Ensure PostgreSQL container is running: `docker ps`
- Check connection: `docker exec social-feed-postgres psql -U abc -d socialfeed`

## License

MIT License - feel free to use this for learning or building your own projects!
