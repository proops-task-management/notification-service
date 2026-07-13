package com.proops2026.notificationservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Dedicated consumer thread for the {@code task-events} Redis list (IRD-004 amended, ADR-004).
 *
 * <p>Replaces the old {@code @Scheduled} + {@code rightPop} poll with a single thread doing a
 * blocking <b>{@code BRPOP}</b> ({@code rightPop} with a timeout). The producer {@code LPUSH}es the
 * list head, so popping the <b>tail</b> ({@code BRPOP}) preserves <b>FIFO</b> ordering — a
 * {@code BLPOP} (head-pop) would deliver events LIFO. Each payload is handed to
 * {@link EventConsumerServiceImpl#process} (separate bean → transactional).
 *
 * <p>{@code lastPollAt} is refreshed every loop and read by the consumer-liveness health
 * indicator; a wedged consumer stops updating it and is restarted by the orchestrator.
 */
@Slf4j
@Component
public class TaskEventConsumer implements SmartLifecycle {

    private static final String TASK_EVENTS_QUEUE = "task-events";

    private final StringRedisTemplate redisTemplate;
    private final EventConsumerServiceImpl eventProcessor;
    private final long brpopTimeoutSeconds;

    private volatile boolean running = false;
    private volatile Instant lastPollAt = Instant.now();
    private Thread worker;

    public TaskEventConsumer(StringRedisTemplate redisTemplate,
                             EventConsumerServiceImpl eventProcessor,
                             @Value("${notifications.consumer.brpop-timeout-s:5}") long brpopTimeoutSeconds) {
        this.redisTemplate = redisTemplate;
        this.eventProcessor = eventProcessor;
        this.brpopTimeoutSeconds = brpopTimeoutSeconds;
    }

    @Override
    public void start() {
        running = true;
        lastPollAt = Instant.now();
        worker = new Thread(this::consumeLoop, "task-events-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("task-events BRPOP consumer started (timeout={}s)", brpopTimeoutSeconds);
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        log.info("task-events BRPOP consumer stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Last time the consumer loop woke up — the consumer-liveness signal. */
    public Instant getLastPollAt() {
        return lastPollAt;
    }

    private void consumeLoop() {
        while (running) {
            lastPollAt = Instant.now();
            try {
                String payload = redisTemplate.opsForList()
                        .rightPop(TASK_EVENTS_QUEUE, Duration.ofSeconds(brpopTimeoutSeconds));
                if (payload != null) {
                    eventProcessor.process(payload);
                }
            } catch (RuntimeException ex) {
                // Redis blip or a poison payload: log, back off briefly, keep the loop alive.
                log.warn("Error consuming task-events; backing off 1s", ex);
                sleepQuietly(Duration.ofSeconds(1));
            }
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
