# Event Registration & Ticketing Platform

Layered Spring Boot backend: **Controller → Service → Repository**.

Stack: Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, MySQL, Redis, JavaMailSender.

## Quick start

```bash
# 1. Start MySQL + Redis
docker compose up -d

# 2. Set your SMTP credentials (or edit application.yml directly)
export MAIL_USERNAME=you@gmail.com
export MAIL_PASSWORD=your-16-char-app-password   # Gmail: use an App Password, not your login password

# 3. Run
mvn spring-boot:run
```

App runs on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

> Gmail SMTP requires an **App Password** (Google Account → Security → 2-Step Verification → App Passwords), not your normal password. Any other SMTP provider (SendGrid, Mailtrap for testing, etc.) works too — just change `spring.mail.*`.

## Project layout

```
com.eventreg
 ├── config          SecurityConfig, RedisConfig, AsyncConfig, OpenApiConfig
 ├── controller       REST endpoints
 ├── service          Business logic (this is where the interesting code lives)
 ├── repository       Spring Data JPA
 ├── entity           JPA entities + enums
 ├── dto              Request/response DTOs (entities never leave the service layer)
 ├── security         JWT filter, token service, UserDetails
 ├── exception        Custom exceptions + @RestControllerAdvice
 └── scheduler        Reminder emails + email retry reprocessor
```

## Key design decisions (and why)

**Concurrency-safe seat reservation.** `EventRepository.findByIdForUpdate` takes a
`SELECT ... FOR UPDATE` lock on the event row inside the registration transaction.
Every "how many seats are taken right now" read for that event, from any concurrent
request, blocks until the current transaction commits or rolls back. That's what makes
the naive-looking "count confirmed seats → compare to capacity → insert" sequence in
`RegistrationService.reserveOrWaitlist` actually safe under load — no lost updates, no
overselling. Locks are per-event-row, so registrations for *different* events never
block each other.

**Waitlist promotion is synchronous with cancellation**, inside the same lock
(`RegistrationService.cancelRegistration` → `promoteNextWaitlisted`). This guarantees
exactly one promotion per freed seat, even if two people cancel at the same instant.

**Idempotent check-in.** Scanning an already-`USED` ticket returns 200 with the
original check-in time instead of erroring — a double-scan at a busy entrance (flaky
scanner, network retry) shouldn't block anyone at the door. Only a `CANCELLED` ticket
is rejected.

**Redis for single-use tokens** (email verification, password reset). TTL-based expiry
means no scheduled cleanup job is needed — Redis just evicts the key.

**Async email + DB-backed retry queue.** `EmailService.sendAsync` keeps the HTTP
response fast. If SMTP send fails, the message is persisted to
`email_retry_queue` and picked up by `EmailRetryScheduler` every 2 minutes with
exponential backoff (2, 4, 8, 16, 32 min), capped at 5 attempts before being marked
`FAILED_PERMANENTLY`.

**Redis caching.** `GET /api/events` and `GET /api/events/{id}` are `@Cacheable`
(`events` / `eventDetail` caches), evicted on any write to that event. Cheap win for
the highest-traffic read endpoints.

## Open design calls worth knowing about

- **Team registration** (`teamSize > 1` in the register request body) reserves seats
  under the same lock so a team is never split by a competing request — but confirms
  as many seats as are available and waitlists the rest, rather than all-or-nothing.
  Also: only the lead has an account, so every seat in the team is currently recorded
  against the lead's user id. Collecting per-member emails is a natural extension.
- **Unverified users cannot log in at all** (`AppUserPrincipal.isEnabled()` returns
  `user.verified`), which is a stricter reading of "email verification on signup"
  than "verified users get extra features." Easy to relax if you want unverified
  users to browse/register but not, say, check in at the door.
- **Refund eligibility** is currently just a boolean flag set on cancellation
  (`Event.refundEligible`) — there's no real payment/refund integration, since none
  was in the original spec.

## Database schema

See the entity classes under `entity/` — they map directly to: `users`, `events`,
`registrations`, `tickets`, `team_registrations`, `email_retry_queue`.

## What's stubbed / not included

- PDF ticket generation (QR PNG is implemented at `GET /api/tickets/{token}/qr`;
  wrapping it in a PDF is a small addition with a library like OpenPDF).
- Payment gateway integration.
- Refresh-token revocation/blacklisting (refresh tokens are stateless JWTs here;
  add a Redis denylist if you need immediate revocation on logout).
