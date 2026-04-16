# notification-service - Claude Agent

## Read these first (via Notion MCP)
- DOP-001: https://www.notion.so/3412ba48f4878140a5ebf3df60896b9b
- IRD-003: https://www.notion.so/3412ba48f487816080cfea3e82c02cdd
- IRD-004: https://www.notion.so/3412ba48f48781bbaf85d69953a8fd07

---

## Scope
This repo contains notification-service only.
Only implement what is defined in IRD-004, plus the shared non-functional requirements in IRD-003. Nothing more.

Update (2026-04-16): Redis/queue-based event consumption was removed. notification-service is now DB-only and exposes only read + mark-read APIs.

---

## Service Contract
- `notification-service` is an internal service behind `api-gateway`
- `api-gateway` is responsible for JWT validation and injects `X-User-Id` and `X-User-Role`
- In this repo, protected endpoints authorize by comparing stored `user_id` with `X-User-Id`
- This service does not consume queues/events (Redis removed)
- This service stores notifications only; it does not own task data or user data

---

## NEVER
- Generate code for `user-service`, `task-service`, `api-gateway`, or `frontend-service`
- Add endpoints not defined in IRD-004
- Re-implement gateway behavior here - do not validate JWT in controllers or service classes; trust `X-User-Id` from `api-gateway`
- Call `task-service` or `user-service` at runtime
- Join `notifications_db` to any other database
- Store task snapshots or user snapshots locally - only store UUIDs, `eventType`, derived `message`, and read state
- Hardcode secrets - all config via env vars
- Use `ddl-auto=create` or `ddl-auto=update` - Flyway manages schema
- Add email, webhook, WebSocket, push notification, digest, retry, DLQ, or preference logic
- Add pagination, unread-only filters, delete endpoints, or bulk mark-read endpoints unless IRD-004 changes
- Call repository directly from controller - always go through service
- Return `@Entity` from controller - always map to DTO via MapStruct
- Use try/catch in controller or request-facing service methods - throw exceptions and let `GlobalExceptionHandler` handle them
- Write methods longer than 20 lines - split into private helpers
- Use `@Data` on JPA entities - use `@Getter` + `@Setter` + `@Builder` separately
- Use `@Autowired` for dependency injection - always use `@RequiredArgsConstructor` + `private final`
- Use `System.out.println` - always use `@Slf4j` + structured logs
- Allow one user to read or mark another user's notification
- Add caching unless the IRD is updated - notification-service does not define a cache layer

---

## Spring Boot Code Conventions

---

### Directory Structure

```text
src/main/java/com/proops2026/notificationservice/
|-- config/            Auth header filter + request logging
|-- controller/        HTTP layer only - receive request, return response, no logic
|   |-- HealthController.java
|   `-- NotificationController.java
|-- service/           Interfaces + implementations
|   |-- NotificationService.java
|   `-- impl/
|       `-- NotificationServiceImpl.java
|-- repository/        JpaRepository interfaces + custom queries
|   `-- NotificationRepository.java
|-- model/             JPA entities - maps to database tables
|   `-- Notification.java
|-- dto/
|   `-- response/      Output objects - what the client receives
|       |-- HealthResponse.java
|       |-- MarkReadResponse.java
|       `-- NotificationResponse.java
|-- mapper/            MapStruct interfaces - entity <-> DTO conversion
|   `-- NotificationMapper.java
|-- exception/         Custom exceptions + global handler
|   `-- GlobalExceptionHandler.java
`-- NotificationServiceApplication.java

src/main/resources/
`-- db/migrations/     Flyway SQL migrations
```

---

### Lombok - Required Annotations

**Entity (`model/`):**
```java
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private String id;

    private String userId;
    private String eventType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
```
> Do NOT use `@Data` on entities - it causes JPA issues with `equals/hashCode` and lazy loading.

**Queue payload DTO (`dto/request/`):** (removed — no queue consumer)

**Response DTO (`dto/response/`):**
```java
@Getter
@Builder
public class NotificationResponse {
    private String id;
    private String eventType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
```

**ServiceImpl:**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
}
```
> Use `@RequiredArgsConstructor` + `private final` everywhere. Never use `@Autowired`.

---

### Layer Rules

**Controller** - HTTP only, no business logic
```java
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.listNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<MarkReadResponse> markRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, id));
    }
}
```
> Protected endpoints use `X-User-Id` from `api-gateway`. Do not parse `Authorization` headers in controllers.

**Service (interface)** - one method per use case
```java
public interface NotificationService {
    List<NotificationResponse> listNotifications(String userId);
    MarkReadResponse markAsRead(String userId, String notificationId);
}
```

**ServiceImpl** - all business logic, ownership checks, DTO mapping
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(notificationMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public MarkReadResponse markAsRead(String userId, String notificationId) {
        Notification notification = findOwnedNotificationOrThrow(notificationId, userId);
        notification.setRead(true);
        return MarkReadResponse.builder()
            .id(notification.getId())
            .isRead(true)
            .build();
    }
}
```

---

### Authorization Rules

| Action | Who |
|---|---|
| List notifications | Authenticated caller - own notifications only |
| Mark notification as read | Notification owner only |
| Health check | Public |

```java
private Notification findOwnedNotificationOrThrow(String notificationId, String userId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!userId.equals(notification.getUserId())) {
        throw new UnauthorizedException("you do not have permission to access this notification");
    }

    return notification;
}
```

---

### Queue / Events

Removed — notification-service no longer consumes events/queues.

---

### Repository Rules

```java
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
}
```

- Keep repository methods focused on persistence only
- Do not add repository logic to controllers
- Prefer derived queries before custom SQL

---

### MapStruct - Entity <-> DTO

```java
@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
```

- One mapper per entity, lives in `mapper/`
- Never map manually in controllers
- Request/response mapping happens in service layer only

---

### Exception Handling

```java
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String notificationId) {
        super("notification not found");
    }
}

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(UnauthorizedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(401).body(new ErrorResponse("x-user-id header is required"));
    }
}
```

- All errors return `{ "message": "string" }`
- No stack traces in production responses
- No try/catch in controller or request service methods
- `401` = missing gateway identity header
- `403` = notification belongs to another user
- `404` = notification not found

---

### Validation

- There are no request body DTOs in IRD-004's public API
- Validate `X-User-Id` is present on protected routes
- Validate `X-User-Role` is present on protected routes (gateway-injected)

---

### Helper Rules

- If a block appears more than once - extract to a private helper
- If a method exceeds 20 lines - split it
- Name helpers after what they do: `findNotificationOrThrow`, `requireOwner`
- Helpers stay `private` inside service implementations

---

### Naming Conventions

| Type | Pattern | Example |
|---|---|---|
| Class | PascalCase | `NotificationServiceImpl` |
| Method | camelCase, verb-first | `markAsRead`, `buildMessage` |
| Variable | camelCase | `savedNotification`, `eventPayload` |
| Constant | UPPER_SNAKE_CASE | `USER_ID_HEADER` |
| DTO request/event | Noun + Payload/Request | — |
| DTO response | Action/Entity + Response | `NotificationResponse`, `MarkReadResponse` |
| Exception | Noun + Exception | `NotificationNotFoundException` |
| Mapper | Entity + Mapper | `NotificationMapper` |

---

### Logging

```java
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    log.info("Notification marked as read: {}", notificationId);
}
```

- Every inbound HTTP request logged via `LoggingInterceptor`: `[timestamp] METHOD /path -> STATUS (Xms)`
- Log user-facing operations at `info` when helpful
- Never log JWTs, secrets, or full raw payloads at info level
- Never use `System.out.println`

---

### HTTP Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/notifications` | Yes | Return current user's notifications, newest first |
| `PATCH` | `/notifications/{id}/read` | Yes | Mark one notification as read |
| `GET` | `/health` | No | Health check |

**GET `/notifications`**
```json
[
  {
    "id": "uuid",
    "eventType": "task.assigned",
    "message": "You have been assigned a new task",
    "isRead": false,
    "createdAt": "2026-04-14T08:00:00"
  }
]
```

**PATCH `/notifications/{id}/read`**
```json
{
  "id": "uuid",
  "isRead": true
}
```

**GET `/health`**
```json
{
  "status": "ok",
  "service": "notification-service"
}
```

---

### Testing

- Mock-based tests are sufficient for this simplified scope (no Redis/queue)
- Use `@WebMvcTest` + MockMvc for controller tests and Mockito for service mocks
- One test class per public surface:
  - `NotificationControllerTest`
  - `HealthControllerTest`
- Test method naming: `methodName_condition_expectedResult`

```java
@Test
void listNotifications_returnsOnlyCallerNotifications() { ... }

@Test
void listNotifications_whenUserHasNoNotifications_returnsEmptyList() { ... }

@Test
void markAsRead_asOwner_returns200() { ... }

@Test
void markAsRead_asDifferentUser_returns403() { ... }
```

**Required assertions from IRD-004**
- `GET /notifications` returns only caller's notifications
- `GET /notifications` returns empty list when caller has none
- `PATCH /notifications/{id}/read` flips `isRead=true`
- `PATCH /notifications/{id}/read` returns `403` for a different user

---

### Database Rules (MySQL 8, database: `notifications_db`)

```sql
CREATE TABLE notifications (
  id         CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  user_id    CHAR(36)     NOT NULL,
  event_type VARCHAR(50)  NOT NULL,
  message    VARCHAR(255) NOT NULL,
  is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id
  ON notifications (user_id);

CREATE INDEX idx_notifications_created_at
  ON notifications (created_at DESC);

CREATE INDEX idx_notifications_is_read
  ON notifications (user_id, is_read);
```

- Schema managed via Flyway: `db/migrations/V{n}__description.sql`
- `spring.jpa.hibernate.ddl-auto=validate`
- Never create foreign keys to tasks or users
- Store cross-service references as plain UUID strings only

---

### Environment Variables

```env
PORT=8083
SPRING_DATASOURCE_URL=jdbc:mysql://db-notification:3306/notifications_db
SPRING_DATASOURCE_USERNAME=app_user
SPRING_DATASOURCE_PASSWORD=app_pass
```

- Never hardcode any of the above values

---

### Out of Scope

- Email verification
- Password reset
- Real-time notifications
- Notification deletion
- Bulk mark-as-read
- Notification preferences
- External delivery channels
