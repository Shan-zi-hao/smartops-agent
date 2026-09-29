package com.example.smartops.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReadOnlyOperationsService {
    private static final Set<String> SERVICES = Set.of("order-service", "user-service", "payment-service");

    private final DockerClient dockerClient;
    private final RestClient restClient;
    private final String prometheusUrl;
    private final String lokiUrl;
    private final String composeProject;

    ReadOnlyOperationsService(DockerClient dockerClient, RestClient.Builder restClientBuilder,
                              @Value("${smartops.prometheus-url}") String prometheusUrl,
                              @Value("${smartops.loki-url}") String lokiUrl,
                              @Value("${smartops.compose-project}") String composeProject) {
        this.dockerClient = dockerClient;
        this.restClient = restClientBuilder.build();
        this.prometheusUrl = prometheusUrl;
        this.lokiUrl = lokiUrl;
        this.composeProject = composeProject;
    }

    List<ContainerStatus> status(String service) {
        requireAllowedService(service);
        try {
            return dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("com.docker.compose.service", service,
                            "com.docker.compose.project", composeProject))
                    .exec().stream()
                    .map(container -> new ContainerStatus(service, container.getId(),
                            container.getState(), container.getStatus()))
                    .toList();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Docker status unavailable", exception);
        }
    }

    QueryResult logs(String service) {
        requireAllowedService(service);
        String query = "{service=\"" + service + "\"}";
        long start = Instant.now().minusSeconds(15 * 60).toEpochMilli() * 1_000_000;
        String url = lokiUrl + "/loki/api/v1/query_range?query=" + encode(query)
                + "&start=" + start + "&limit=100&direction=backward";
        return new QueryResult(service, "loki", "last 15 minutes, up to 100 lines", fetch(url));
    }

    QueryResult metrics(String service, String metric) {
        requireAllowedService(service);
        String selector = "{job=\"spring-services\",service=\"" + service + "\"}";
        String requests = "http_server_requests_seconds_count{job=\"spring-services\",service=\""
                + service + "\",uri!~\"/actuator/.*\"}";
        String query = switch (metric) {
            case "scrape_up" -> "up" + selector;
            case "request_rate" -> "sum(rate(" + requests + "[1m]))";
            case "http_5xx_rate" -> "sum(rate(http_server_requests_seconds_count{job=\"spring-services\",service=\""
                    + service + "\",status=~\"5..\",uri!~\"/actuator/.*\"}[1m])) / sum(rate("
                    + requests + "[1m]))";
            case "avg_latency_seconds" -> "sum(rate(http_server_requests_seconds_sum{job=\"spring-services\",service=\""
                    + service + "\",uri!~\"/actuator/.*\"}[1m])) / sum(rate(" + requests + "[1m]))";
            case "process_cpu_usage" -> "process_cpu_usage" + selector;
            case "jvm_heap_used_bytes" -> "sum(jvm_memory_used_bytes{job=\"spring-services\",service=\""
                    + service + "\",area=\"heap\"})";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown metric: " + metric);
        };
        return new QueryResult(service, "prometheus", metric,
                fetch(prometheusUrl + "/api/v1/query?query=" + encode(query)));
    }

    private JsonNode fetch(String url) {
        try {
            return restClient.get().uri(URI.create(url)).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Monitoring backend unavailable", exception);
        }
    }

    private static void requireAllowedService(String service) {
        if (!SERVICES.contains(service)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown service: " + service);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record ContainerStatus(String service, String containerId, String state, String detail) {}

    record QueryResult(String service, String source, String description, JsonNode data) {}
}
