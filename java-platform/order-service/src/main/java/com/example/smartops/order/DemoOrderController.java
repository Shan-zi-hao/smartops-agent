package com.example.smartops.order;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class DemoOrderController {

    private static final Logger log = LoggerFactory.getLogger(DemoOrderController.class);
    private static final String REQUEST_COUNT_KEY = "demo:order:requests";

    private final StringRedisTemplate redisTemplate;

    public DemoOrderController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/demo")
    public Map<String, Object> demo() {
        Long requestCount = redisTemplate.opsForValue().increment(REQUEST_COUNT_KEY);
        return Map.of("service", "order-service", "requestCount", requestCount);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> redisUnavailable(DataAccessException exception) {
        log.error("Redis operation failed while handling order request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "REDIS_UNAVAILABLE", "message", "Order request failed because Redis is unavailable"));
    }
}
