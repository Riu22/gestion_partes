package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.AusenciaRequestDto;
import com.example.gestion_partes.model.Ausencia;
import com.example.gestion_partes.service.ausencias_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ausencias")
public class ausencias_controller {

    @Autowired
    ausencias_service ausenciasService;



    // ── Incidencias ──────────────────────────────────────

    @GetMapping("/dias-sin-parte")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, Object>> diasSinParte() {
        return ResponseEntity.ok(ausenciasService.getDiasSinParte());
    }

    // ── Ausencias laborales (baja / vacaciones) ───────────

    @PostMapping("/laborales")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Ausencia> crear(@RequestBody AusenciaRequestDto req) {
        return ResponseEntity.ok(ausenciasService.crear(
                UUID.fromString(req.perfilId()),
                req.tipo(),
                req.fechaInicio(),
                req.fechaFin(),
                req.observaciones()
        ));
    }

    @DeleteMapping("/laborales/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ausenciasService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/laborales/perfil/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Ausencia>> getDeUsuario(@PathVariable String perfilId) {
        return ResponseEntity.ok(
                ausenciasService.getDeUsuario(UUID.fromString(perfilId))
        );
    }
}