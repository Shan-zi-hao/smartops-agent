package com.example.smartops.user;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class DemoUserController {

    @GetMapping("/demo")
    public Map<String, String> demo() {
        return Map.of("service", "user-service", "status", "ok");
    }
}
