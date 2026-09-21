package com.dataviz.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/license")
@RequiredArgsConstructor
public class LicenseController {

    @GetMapping("/info")
    public Map<String, Object> getLicenseInfo() {
        Map<String, Object> licenseInfo = new HashMap<>();
        licenseInfo.put("type", "Enterprise");
        licenseInfo.put("issuedTo", "DataViz Platform");
        licenseInfo.put("issuedAt", "2024-01-01");
        licenseInfo.put("expiresAt", "2025-12-31");
        licenseInfo.put("maxUsers", 1000);
        licenseInfo.put("maxNodes", 10);
        licenseInfo.put("valid", true);
        return licenseInfo;
    }

    @PostMapping("/activate")
    public Map<String, Object> activateLicense(@RequestBody Map<String, String> request) {
        String licenseKey = request.get("licenseKey");
        log.info("Activating license: {}", licenseKey);
        // TODO: Implement license activation logic
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "License activated successfully");
        return result;
    }

    @GetMapping("/validate")
    public Map<String, Object> validateLicense() {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("message", "License is valid");
        return result;
    }
}
