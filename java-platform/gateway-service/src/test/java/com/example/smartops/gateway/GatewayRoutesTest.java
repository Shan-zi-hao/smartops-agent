package com.example.smartops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class GatewayRoutesTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void loadsStandardRoutesFromDevConfiguration() {
        List<Route> routes = routeLocator.getRoutes()
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(routes).isNotNull();
        assertThat(routes).extracting(Route::getId)
                .contains("ai-service", "operation-service");
        assertThat(routes.stream()
                .filter(route -> route.getId().equals("ai-service"))
                .findFirst()
                .orElseThrow()
                .getUri()
                .toString()).isEqualTo("http://127.0.0.1:8000");
        assertThat(routes.stream()
                .filter(route -> route.getId().equals("operation-service"))
                .findFirst()
                .orElseThrow()
                .getUri()
                .toString()).isEqualTo("http://127.0.0.1:8083");
    }
}
