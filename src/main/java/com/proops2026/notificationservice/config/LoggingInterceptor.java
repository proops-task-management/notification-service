package com.proops2026.notificationservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";
    private static final String START_DT_ATTR = "startDateTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(START_DT_ATTR, LocalDateTime.now());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long duration = System.currentTimeMillis() - (long) request.getAttribute(START_TIME_ATTR);
        LocalDateTime arrived = (LocalDateTime) request.getAttribute(START_DT_ATTR);
        log.info("[{}] {} {} → {} ({}ms)",
                arrived,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
    }
}

