package com.example.smartops.payment;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class DemoPaymentController {

    @GetMapping("/demo")
    public Map<String, Object> demo(@RequestParam(defaultValue = "0") long delayMs) throws InterruptedException {
        if (delayMs < 0 || delayMs > 3000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delayMs must be between 0 and 3000");
        }
        Thread.sleep(delayMs);
        return Map.of("service", "payment-service", "status", "ok", "delayMs", delayMs);
    }
}
