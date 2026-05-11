package com.example.gestion_partes.controller;

import com.example.gestion_partes.service.ausencias_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ausencias")
public class ausencias_controller {

    @Autowired
    ausencias_service ausencias_service;

    /**
     * Devuelve por operario/encargado los días laborables (L-V)
     * sin parte en la quincena actual.
     *
     * Respuesta: Map<nombreCompleto, List<fecha>>
     * Solo incluye usuarios que tienen al menos 1 día sin parte.
     */
    @GetMapping("/dias-sin-parte")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, Object>> diasSinParte() {
        return ResponseEntity.ok(ausencias_service.getDiasSinParte());
    }
}
