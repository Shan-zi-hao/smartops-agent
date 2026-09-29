package com.example.smartops.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.model.Container;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class RestartOperationsServiceTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private DockerClient docker;
    private MockRestServiceServer server;
    private RestartOperationsService operations;

    @BeforeEach
    void setUp() {
        docker = mock(DockerClient.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        operations = new RestartOperationsService(docker, builder.build(), "agent_codex", TOKEN);
    }

    @Test
    void refusesMissingOrWrongTokenBeforeDockerAccess() {
        assertThatThrownBy(() -> operations.restart("order-service", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> operations.restart("order-service", "wrong"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> new RestartOperationsService(docker, RestClient.create(),
                "agent_codex", "").restart("order-service", TOKEN))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503 SERVICE_UNAVAILABLE");
        verifyNoInteractions(docker);
    }

    @Test
    void rejectsUnknownOrAmbiguousTarget() {
        assertThatThrownBy(() -> operations.restart("redis-app", TOKEN))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        ListContainersCmd command = mock(ListContainersCmd.class);
        when(docker.listContainersCmd()).thenReturn(command);
        when(command.withShowAll(true)).thenReturn(command);
        when(command.withLabelFilter(Map.of("com.docker.compose.service", "order-service",
                "com.docker.compose.project", "agent_codex"))).thenReturn(command);
        when(command.exec()).thenReturn(List.of());
        assertThatThrownBy(() -> operations.restart("order-service", TOKEN))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
    }

    @Test
    void restartsOnlyMatchingRunningContainerAndChecksHealth() {
        ListContainersCmd list = mock(ListContainersCmd.class);
        RestartContainerCmd restart = mock(RestartContainerCmd.class);
        Container container = mock(Container.class);
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        when(list.withLabelFilter(Map.of("com.docker.compose.service", "order-service",
                "com.docker.compose.project", "agent_codex"))).thenReturn(list);
        when(list.exec()).thenReturn(List.of(container));
        when(container.getId()).thenReturn("container-1");
        when(container.getState()).thenReturn("running");
        when(docker.restartContainerCmd("container-1")).thenReturn(restart);
        when(restart.withTimeout(10)).thenReturn(restart);
        server.expect(requestTo("http://order-service:8080/actuator/health"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        var result = operations.restart("order-service", TOKEN);

        assertThat(result.healthy()).isTrue();
        verify(restart).exec();
        server.verify();
    }
}
