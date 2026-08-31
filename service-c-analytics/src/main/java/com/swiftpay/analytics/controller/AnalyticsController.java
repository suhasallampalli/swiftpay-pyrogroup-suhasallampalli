package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.model.AnalyticsRecord;
import com.swiftpay.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Analytics", description = "Payment analytics and reporting")
@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Get payment volume summary")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @Operation(summary = "Get recent payment events (last 100)")
    @GetMapping("/events")
    public ResponseEntity<List<AnalyticsRecord>> getRecentEvents() {
        return ResponseEntity.ok(analyticsService.getRecentEvents());
    }
}
