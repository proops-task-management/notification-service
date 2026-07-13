package com.proops2026.notificationservice.integration;

import com.proops2026.notificationservice.repository.NotificationRepository;
import com.proops2026.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration test against a real MySQL + Redis (Testcontainers).
 *
 * Proves the D8 acceptance criteria for notification-service (IRD-004 amended, ADR-004):
 *   - the dedicated BRPOP consumer thread delivers an LPUSHed event to a persisted notification;
 *   - a duplicate {@code eventId} is skipped via the {@code processed_events} ledger
 *     (restart-mid-process safety = the same idempotency guarantee).
 *
 * Docker must be running locally to execute this test.
 */
@SpringBootTest
@Testcontainers
class NotificationConsumerIntegrationTest {

    private static final String TASK_EVENTS_QUEUE = "task-events";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("notifications.consumer.brpop-timeout-s", () -> "1"); // snappy loop for the test
    }

    @Test
    void consumer_deliversEventToNotification() {
        String eventId = UUID.randomUUID().toString();
        publish(eventId, "task.assigned", "task-1", "user-1");

        awaitNotificationCount(1);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(notificationRepository.findAll().getFirst().getMessage())
                .isEqualTo("You have been assigned to task task-1.");
    }

    @Test
    void consumer_duplicateEventId_isSkipped() {
        String eventId = UUID.randomUUID().toString();
        publish(eventId, "task.created", "task-2", "user-2");
        publish(eventId, "task.created", "task-2", "user-2"); // same eventId → must be deduped

        awaitNotificationCount(1);
        // Give the consumer time to pop + skip the duplicate, then assert it stayed at 1.
        settle();
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    // --- helpers ---

    private void publish(String eventId, String eventType, String taskId, String userId) {
        String payload = ("{\"eventId\":\"%s\",\"eventType\":\"%s\",\"taskId\":\"%s\",\"userId\":\"%s\","
                + "\"timestamp\":\"2026-07-13T09:00:00Z\"}").formatted(eventId, eventType, taskId, userId);
        redisTemplate.opsForList().leftPush(TASK_EVENTS_QUEUE, payload); // producer LPUSHes the head
    }

    private void awaitNotificationCount(long expected) {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            if (notificationRepository.count() >= expected) {
                return;
            }
            sleep(200);
        }
        assertThat(notificationRepository.count()).as("notification not delivered in time").isEqualTo(expected);
    }

    private void settle() {
        sleep(Duration.ofSeconds(2).toMillis());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
