package com.proops2026.notificationservice.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the dedicated BRPOP consumer thread (IRD-004 amended, ADR-004). Redis and the
 * transactional processor are mocked, so this exercises the {@link org.springframework.context.SmartLifecycle}
 * contract and the loop's branches (deliver / back-off) without Testcontainers — the full
 * MySQL+Redis delivery path is covered by {@code NotificationConsumerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class TaskEventConsumerTest {

    private static final String TASK_EVENTS_QUEUE = "task-events";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private EventConsumerServiceImpl eventProcessor;

    private TaskEventConsumer consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null && consumer.isRunning()) {
            consumer.stop();
        }
    }

    @Test
    void lifecycle_startThenStop_togglesRunning() {
        // No payloads: the loop just spins on an empty queue.
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(listOperations.rightPop(anyString(), any(Duration.class))).thenReturn(null);
        consumer = new TaskEventConsumer(redisTemplate, eventProcessor, 1);

        assertThat(consumer.isRunning()).isFalse();

        consumer.start();
        assertThat(consumer.isRunning()).isTrue();
        assertThat(consumer.getLastPollAt()).isAfterOrEqualTo(Instant.now().minusSeconds(5));

        consumer.stop();
        assertThat(consumer.isRunning()).isFalse();
    }

    @Test
    void consumeLoop_whenPayloadPresent_handsItToProcessor() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        // First poll returns an event, subsequent polls are empty.
        when(listOperations.rightPop(eq(TASK_EVENTS_QUEUE), any(Duration.class)))
                .thenReturn("{\"eventId\":\"evt-1\"}", (String) null);
        consumer = new TaskEventConsumer(redisTemplate, eventProcessor, 1);

        consumer.start();

        verify(eventProcessor, timeout(2000)).process("{\"eventId\":\"evt-1\"}");
    }

    @Test
    void consumeLoop_whenRedisThrows_backsOffAndStaysRunning() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        // A Redis blip on the first poll, then an empty queue: the loop must swallow it and survive.
        when(listOperations.rightPop(eq(TASK_EVENTS_QUEUE), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"))
                .thenReturn(null);
        consumer = new TaskEventConsumer(redisTemplate, eventProcessor, 1);

        consumer.start();

        // The loop must poll again after swallowing the exception (proves it survived the blip).
        verify(listOperations, timeout(2500).atLeast(2))
                .rightPop(eq(TASK_EVENTS_QUEUE), any(Duration.class));
        assertThat(consumer.isRunning()).isTrue();
        verify(eventProcessor, never()).process(anyString());
    }
}
