package com.example.smartops.operation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/services/{service}")
class OperationsController {
    private final ReadOnlyOperationsService operations;
    private final RestartOperationsService restarts;

    OperationsController(ReadOnlyOperationsService operations, RestartOperationsService restarts) {
        this.operations = operations;
        this.restarts = restarts;
    }

    @GetMapping("/status")
    List<ReadOnlyOperationsService.ContainerStatus> status(@PathVariable String service) {
        return operations.status(service);
    }

    @GetMapping("/logs")
    ReadOnlyOperationsService.QueryResult logs(@PathVariable String service) {
        return operations.logs(service);
    }

    @GetMapping("/metrics/{metric}")
    ReadOnlyOperationsService.QueryResult metrics(@PathVariable String service, @PathVariable String metric) {
        return operations.metrics(service, metric);
    }

    @PostMapping("/restart")
    RestartOperationsService.RestartResult restart(@PathVariable String service,
            @RequestHeader(value = "X-SmartOps-Restart-Token", required = false) String token) {
        return restarts.restart(service, token);
    }
}
