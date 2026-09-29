package com.example.smartops.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
class RestartOperationsService {
    private static final Set<String> SERVICES = Set.of("order-service", "user-service", "payment-service");
    private final DockerClient docker;
    private final RestClient healthClient;
    private final String project;
    private final String restartToken;

    RestartOperationsService(DockerClient docker, @Qualifier("healthClient") RestClient healthClient,
            @Value("${smartops.compose-project}") String project,
            @Value("${smartops.restart-token:}") String restartToken) {
        this.docker = docker;
        this.healthClient = healthClient;
        this.project = project;
        this.restartToken = restartToken;
    }

    RestartResult restart(String service, String suppliedToken) {
        if (restartToken.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Restart is disabled");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                restartToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restart is not authorized");
        }
        if (!SERVICES.contains(service)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown service");
        }
        try {
            var containers = docker.listContainersCmd().withShowAll(true)
                    .withLabelFilter(Map.of("com.docker.compose.service", service,
                            "com.docker.compose.project", project)).exec();
            if (containers.size() != 1 || !"running".equalsIgnoreCase(containers.get(0).getState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Expected exactly one running container for this service");
            }
            String containerId = containers.get(0).getId();
            docker.restartContainerCmd(containerId).withTimeout(10).exec();
            for (int attempt = 0; attempt < 15; attempt++) {
                try {
                    JsonNode health = healthClient.get().uri("http://" + service + ":8080/actuator/health")
                            .retrieve().body(JsonNode.class);
                    if (health != null && "UP".equals(health.path("status").asText())) {
                        return new RestartResult(service, containerId, true, "Health endpoint is UP");
                    }
                } catch (RestClientException ignored) {
                    // The service may briefly refuse connections while restarting.
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return new RestartResult(service, containerId, false, "Health check interrupted");
                }
            }
            return new RestartResult(service, containerId, false, "Health endpoint did not become UP");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Docker restart unavailable", exception);
        }
    }

    record RestartResult(String service, String containerId, boolean healthy, String detail) {}
}
