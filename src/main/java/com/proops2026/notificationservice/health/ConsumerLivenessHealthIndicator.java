package com.proops2026.notificationservice.health;

import com.proops2026.notificationservice.service.impl.TaskEventConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Consumer-liveness signal (IRD-004 amended item 3). If the BRPOP loop's {@code lastPollAt} is
 * older than the threshold (default 30 s), the consumer is wedged → liveness DOWN → the
 * orchestrator restarts the pod. Included in the {@code liveness} health group (application.yml)
 * so it feeds {@code /actuator/health/liveness}. Complements the queue-backlog alert (IRD-020).
 */
@Component
public class ConsumerLivenessHealthIndicator implements HealthIndicator {

    private final TaskEventConsumer consumer;
    private final long livenessThresholdSeconds;

    public ConsumerLivenessHealthIndicator(
            TaskEventConsumer consumer,
            @Value("${notifications.consumer.liveness-threshold-s:30}") long livenessThresholdSeconds) {
        this.consumer = consumer;
        this.livenessThresholdSeconds = livenessThresholdSeconds;
    }

    @Override
    public Health health() {
        Instant lastPollAt = consumer.getLastPollAt();
        long secondsSinceLastPoll = Duration.between(lastPollAt, Instant.now()).getSeconds();

        if (secondsSinceLastPoll > livenessThresholdSeconds) {
            return Health.down()
                    .withDetail("lastPollAt", lastPollAt.toString())
                    .withDetail("secondsSinceLastPoll", secondsSinceLastPoll)
                    .build();
        }
        return Health.up()
                .withDetail("lastPollAt", lastPollAt.toString())
                .build();
    }
}
