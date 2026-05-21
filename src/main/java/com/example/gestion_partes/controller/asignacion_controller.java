package com.example.gestion_partes.controller;

import com.example.gestion_partes.model.asignacion_obra;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.service.asignacion_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/asignaciones")
public class asignacion_controller {

    @Autowired
    asignacion_service asignacion_service;

    // Asignar jefe o encargado a una obra
    @PostMapping("/asignar_a_obra/{perfilId}/{obraId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<asignacion_obra> asignar_a_obra(
            @PathVariable UUID perfilId,
            @PathVariable Long obraId) {
        return ResponseEntity.ok(asignacion_service.asignar_a_obra(perfilId, obraId));
    }

    // Asignar operario a su encargado
    @PutMapping("/asignar_subordinado/{subordinadoId}/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<perfil> asignar_subordinado(
            @PathVariable UUID subordinadoId,
            @PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.asignar_subordinado(subordinadoId, jefeId));
    }

    // Asignar encargado a su jefe de obra
    @PutMapping("/asignar_encargado/{encargadoId}/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<perfil> asignar_encargado(
            @PathVariable UUID encargadoId,
            @PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.asignar_encargado_a_jefe(encargadoId, jefeId));
    }

    // Ver quién está asignado a una obra
    @GetMapping("/obra/{obraId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<List<asignacion_obra>> get_asignaciones_obra(
            @PathVariable Long obraId) {
        return ResponseEntity.ok(asignacion_service.get_asignaciones_obra(obraId));
    }

    // Ver obras asignadas a un perfil
    @GetMapping("/perfil/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<List<asignacion_obra>> get_obras_de_perfil(
            @PathVariable UUID perfilId) {
        return ResponseEntity.ok(asignacion_service.get_obras_de_perfil(perfilId));
    }

    // Eliminar asignación de obra
    @DeleteMapping("/eliminar/{asignacionId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> eliminar_asignacion(
            @PathVariable Long asignacionId) {
        asignacion_service.eliminar_asignacion_obra(asignacionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{jefeId}/subordinados")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<perfil>> get_subordinados_de(@PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.get_mis_subordinados(jefeId));
    }

    // 2. Endpoint para quitar un jefe directo (el botón de eliminar en Flutter)
    @DeleteMapping("/quitar_subordinado/{usuarioId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> quitar_subordinado(@PathVariable UUID usuarioId) {
        // En tu service, este método debe poner jefe_directo = null
        asignacion_service.quitar_jefe_directo(usuarioId);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/mis_obras")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<asignacion_obra>> get_mis_obras(Authentication auth) {
        UUID id = UUID.fromString(auth.getName());
        return ResponseEntity.ok(asignacion_service.get_mis_obras(id));
    }

    @PostMapping("/asignar_obras_batch/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> asignar_obras_batch(
            @PathVariable UUID perfilId,
            @RequestBody List<Long> obraIds) {
        asignacion_service.asignar_obras_batch(perfilId, obraIds);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/asignar_subordinados_batch/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> asignar_subordinados_batch(
            @PathVariable UUID jefeId,
            @RequestBody List<UUID> subordinadoIds) {
        asignacion_service.asignar_subordinados_batch(jefeId, subordinadoIds);
        return ResponseEntity.ok().build();
    }
}