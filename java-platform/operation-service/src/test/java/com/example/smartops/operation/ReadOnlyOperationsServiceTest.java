package com.example.smartops.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class ReadOnlyOperationsServiceTest {
    private DockerClient dockerClient;
    private MockRestServiceServer server;
    private ReadOnlyOperationsService operations;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        operations = new ReadOnlyOperationsService(dockerClient, builder,
                "http://prometheus:9090", "http://loki:3100", "agent_codex");
    }

    @Test
    void statusOnlySelectsKnownComposeServiceInThisProject() {
        ListContainersCmd command = mock(ListContainersCmd.class);
        Container container = mock(Container.class);
        when(dockerClient.listContainersCmd()).thenReturn(command);
        when(command.withShowAll(true)).thenReturn(command);
        when(command.withLabelFilter(Map.of("com.docker.compose.service", "order-service",
                "com.docker.compose.project", "agent_codex"))).thenReturn(command);
        when(command.exec()).thenReturn(List.of(container));
        when(container.getId()).thenReturn("container-1");
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");

        var result = operations.status("order-service");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).state()).isEqualTo("running");
        verify(command).withLabelFilter(Map.of("com.docker.compose.service", "order-service",
                "com.docker.compose.project", "agent_codex"));
    }

    @Test
    void rejectsUnknownServiceBeforeQueryingBackends() {
        assertThatThrownBy(() -> operations.logs("other-service"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> operations.status("other-service"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void metricIsRestrictedToNamedQueries() {
        assertThatThrownBy(() -> operations.metrics("order-service", "arbitrary_query"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void queriesPrometheusWithFixedServiceSelector() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "service%3D%22order-service%22")))
                .andRespond(withSuccess("{\"status\":\"success\",\"data\":{\"result\":[]}}", MediaType.APPLICATION_JSON));

        var result = operations.metrics("order-service", "scrape_up");

        assertThat(result.source()).isEqualTo("prometheus");
        assertThat(result.data().path("status").asText()).isEqualTo("success");
        server.verify();
    }
}
