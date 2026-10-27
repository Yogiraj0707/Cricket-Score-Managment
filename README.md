# Boundary — Cricket Score Management

A Spring Boot cricket scoring platform with a responsive live dashboard, REST API, and server-sent events (SSE) for real-time score updates. The demo data is simulated in memory; no external cricket data provider or database is required.

## Requirements

- Java 17 or newer
- Maven 3.8+

## Run

```powershell
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Live sample matches update every eight seconds. Override the interval with the `CRICKET_SIMULATION_INTERVAL_MS` environment variable or the `cricket.simulation-interval-ms` property.

On first visit, create an account with your name, email, and password, or switch to sign in if you already registered. Accounts are stored in the local H2 database and passwords are BCrypt-hashed; browser sessions use an HttpOnly, SameSite=Lax cookie. The light/dark theme preference remains local to the browser. Use the profile edit button to change your display name.

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/matches` | List live, upcoming, and completed matches |
| `GET` | `/api/matches/{id}` | Get match details and current player statistics |
| `GET` | `/api/matches/{id}/scorecard` | Get a match scorecard |
| `POST` | `/api/matches/{id}/balls` | Record a delivery for a live match |
| `GET` | `/api/live` | Subscribe to score-update SSE events |
| `POST` | `/api/auth/register` | Create an account and start a session |
| `POST` | `/api/auth/login` | Sign in with email and password |
| `GET` | `/api/auth/session` | Get the current signed-in profile |
| `PATCH` | `/api/auth/profile` | Update the signed-in user's display name |
| `POST` | `/api/auth/logout` | End the current session |

Example delivery:

```json
{
  "runs": 4,
  "wicket": false
}
```

`runs` must be between 0 and 6. Deliveries can only be recorded for matches with `LIVE` status.

Example SSE subscription:

```bash
curl -N http://localhost:8080/api/live
```

The sample match feed is in memory; account records persist in the local `data/` H2 database. The sample authentication is a starting point for local use. A public deployment should add HTTPS, CSRF protection, rate limiting, account verification, password recovery, and a managed database. For multi-instance match scoring, use shared match storage and publish score events through a broker such as Redis or Kafka.
