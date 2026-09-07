package com.actiongate.api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
public class HealthController {
  @GetMapping("/api/v1/status")
  public Map<String, String> status() { return Map.of("service", "actiongate", "status", "ready", "version", "0.1.0"); }
}
