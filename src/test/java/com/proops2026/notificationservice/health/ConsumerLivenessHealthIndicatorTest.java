package com.proops2026.notificationservice.health;

import com.proops2026.notificationservice.service.impl.TaskEventConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for the consumer-liveness indicator (IRD-004 amended item 3): a recent
 * {@code lastPollAt} → UP; a stale one (older than the threshold) → DOWN so the orchestrator
 * restarts the pod. No Spring context — the {@link TaskEventConsumer} poll clock is mocked.
 */
@ExtendWith(MockitoExtension.class)
class ConsumerLivenessHealthIndicatorTest {

    private static final long THRESHOLD_SECONDS = 30;

    @Mock
    private TaskEventConsumer consumer;

    @Test
    void health_whenPollIsRecent_returnsUp() {
        when(consumer.getLastPollAt()).thenReturn(Instant.now());
        ConsumerLivenessHealthIndicator indicator =
                new ConsumerLivenessHealthIndicator(consumer, THRESHOLD_SECONDS);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("lastPollAt");
    }

    @Test
    void health_whenPollIsStale_returnsDown() {
        when(consumer.getLastPollAt()).thenReturn(Instant.now().minusSeconds(THRESHOLD_SECONDS + 5));
        ConsumerLivenessHealthIndicator indicator =
                new ConsumerLivenessHealthIndicator(consumer, THRESHOLD_SECONDS);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsKey("lastPollAt")
                .containsKey("secondsSinceLastPoll");
    }
}
