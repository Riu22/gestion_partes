package com.example.gestion_partes.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/version")
public class update_controller {

    @Value("${app.version.actual}")
    private String versionActual;

    @Value("${app.apk.url}")
    private String apkUrl;

    @GetMapping
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of(
                "version", versionActual,
                "url", apkUrl
        ));
    }
}
