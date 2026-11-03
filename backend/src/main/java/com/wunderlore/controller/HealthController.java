
package com.wunderlore.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Liveness only: a 200 means the application started and is answering requests. It is not a
// readiness check, and it deliberately does not look at The One API, the lore data load or
// PostgreSQL. An outage of those must not make the process look dead, since the features that
// do not need them keep working and a restart would not fix an external failure.
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
