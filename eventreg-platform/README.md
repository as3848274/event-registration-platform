# Event Registration & Ticketing Platform

Layered Spring Boot backend: **Controller → Service → Repository**.

Stack: Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, MySQL, Redis, JavaMailSender.
<img width="938" height="392" alt="image" src="https://github.com/user-attachments/assets/cb8174a4-a8ce-4e35-bd01-e74b0b75f7fd" />


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

**Core Modules**


**1. User & Auth Module**  
• JWT-based authentication with refresh token rotation 
• Role-based access control: ADMIN, ORGANISER, ATTENDEE 
• Email verification on signup via tokenized link (15-minute expiry) 
• Password reset flow with single-use token stored in Redis 

**2. Event Management Module**  
• Organiser can create events with: title, description, venue, date/time, max capacity, ticket price 
• Event states: DRAFT → PUBLISHED → ONGOING → COMPLETED / CANCELLED 
• Organisers can publish, unpublish, or cancel events — cancellation triggers refund eligibility flag 
• Admin can feature or suppress any event platform-wide 
• Soft delete on events; historical data is retained for reporting


**3. Registration & Capacity Module**  
• Atomic seat reservation using database-level row locking (SELECT FOR UPDATE) to prevent 
overselling 
• Waitlist auto-enrolment when event is full; configurable waitlist cap per event 
• On cancellation by a registered attendee, the next waitlisted user is automatically promoted 
• Duplicate registration guard — one active registration per user per event 
• Team registration support: one member registers a group, all receive individual tickets


**4. Ticketing Module** 
• Unique QR-code token generated per confirmed registration (UUID-based) 
• Ticket status: CONFIRMED, CANCELLED, USED Check-in endpoint for organisers: scans token, marks as USED — idempotent (double scan returns 
200, not error) 
• Ticket download endpoint returns ticket metadata; PDF generation can be plugged in


**5. Notification Module**  
• Async email notifications via Spring's @Async + JavaMailSender 
• Triggers: registration confirmed, waitlist joined, waitlist promoted, event cancelled, 24-hour reminder 

**6. Admin & Analytics Module**
• Admin dashboard APIs: total registrations per event, check-in rate, waitlist count 
• Revenue summary per organiser (price × confirmed registrations) 
• Bulk cancel event with notification to all registered attendees 


## Database schema
• users — id, name, email, password_hash, role, is_verified, created_at 
• events — id, organiser_id, title, venue, event_date, max_capacity, price, status, created_at 
• registrations — id, user_id, event_id, status (CONFIRMED/WAITLISTED/CANCELLED), 
registered_at 
• tickets — id, registration_id, token (UUID), status (CONFIRMED/USED/CANCELLED), issued_at 
• team_registrations — id, lead_user_id, event_id, member_count, group_token 
• email_retry_queue — id, recipient, subject, body, attempts, next_retry_at, status 

## What's stubbed / not included

- PDF ticket generation (QR PNG is implemented at `GET /api/tickets/{token}/qr`;
  wrapping it in a PDF is a small addition with a library like OpenPDF).
- Payment gateway integration.
- Refresh-token revocation/blacklisting 
