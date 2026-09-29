package com.example.smartops.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

class DemoOrderControllerTest {

    @Test
    void countsSuccessfulRequests() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("demo:order:requests")).thenReturn(1L);

        var result = new DemoOrderController(redisTemplate).demo();

        assertThat(result).containsEntry("service", "order-service");
        assertThat(result).containsEntry("requestCount", 1L);
    }

    @Test
    void reportsRedisConnectionFailureAsServerError() {
        var controller = new DemoOrderController(mock(StringRedisTemplate.class));

        var result = controller.redisUnavailable(new RedisConnectionFailureException("Redis unavailable"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).containsEntry("error", "REDIS_UNAVAILABLE");
    }

    @Test
    void reportsRedisTimeoutAsServerError() {
        var controller = new DemoOrderController(mock(StringRedisTemplate.class));

        var result = controller.redisUnavailable(new QueryTimeoutException("Redis command timed out"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).containsEntry("error", "REDIS_UNAVAILABLE");
    }
}
