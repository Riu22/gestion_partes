/* Controlador REST para gestionar ausencias laborales (bajas, vacaciones, paternidad).
   Expone endpoints para crear, eliminar, consultar ausencias de un perfil y ver días sin parte.
   Solo accesible para roles de administración y gestión. */
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

    /* GET /dias-sin-parte: devuelve un mapa con los días que no hay partes registrados.
       Solo ADMINISTRACION y GESTION pueden verlo. */
    @GetMapping("/dias-sin-parte")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, Object>> diasSinParte() {
        return ResponseEntity.ok(ausenciasService.getDiasSinParte());
    }

    /* POST /laborales: crea una nueva ausencia laboral (baja, vacaciones, paternidad) para un perfil.
       Recibe los datos en el cuerpo (AusenciaRequestDto): perfilId, tipo, fechas, observaciones y obraId opcional. */
    @PostMapping("/laborales")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Ausencia> crear(@RequestBody AusenciaRequestDto req) {
        return ResponseEntity.ok(ausenciasService.crear(
                UUID.fromString(req.perfilId()),
                req.tipo(),
                req.fechaInicio(),
                req.fechaFin(),
                req.observaciones(),
                req.obraId()
        ));
    }

    /* DELETE /laborales/{id}: elimina una ausencia por su ID. */
    @DeleteMapping("/laborales/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ausenciasService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /* GET /laborales/perfil/{perfilId}: devuelve todas las ausencias de un perfil concreto. */
    @GetMapping("/laborales/perfil/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Ausencia>> getDeUsuario(@PathVariable String perfilId) {
        return ResponseEntity.ok(
                ausenciasService.getDeUsuario(UUID.fromString(perfilId))
        );
    }

    /* GET /laborales/perfil/{perfilId}/historial: devuelve un resumen/historial de ausencias de un perfil. */
    @GetMapping("/laborales/perfil/{perfilId}/historial")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, Object>> getHistorial(
            @PathVariable String perfilId) {
        return ResponseEntity.ok(
                ausenciasService.getHistorialPerfil(UUID.fromString(perfilId))
        );
    }
}
