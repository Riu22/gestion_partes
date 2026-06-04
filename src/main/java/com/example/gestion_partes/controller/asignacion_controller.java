/* Controlador REST para la gestión de asignaciones entre perfiles y obras.
   Permite asignar jefes/encargados a obras, subordinados a jefes, consultar asignaciones,
   y asignaciones en lote (batch). Roles permitidos: ADMINISTRACION y GESTION principalmente. */
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

    /* POST /asignar_a_obra/{perfilId}/{obraId}: asigna un perfil (jefe/encargado) a una obra. */
    @PostMapping("/asignar_a_obra/{perfilId}/{obraId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<asignacion_obra> asignar_a_obra(
            @PathVariable UUID perfilId,
            @PathVariable Long obraId) {
        return ResponseEntity.ok(asignacion_service.asignar_a_obra(perfilId, obraId));
    }

    /* PUT /asignar_subordinado/{subordinadoId}/{jefeId}: asigna un operario como subordinado de un encargado/jefe. */
    @PutMapping("/asignar_subordinado/{subordinadoId}/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<perfil> asignar_subordinado(
            @PathVariable UUID subordinadoId,
            @PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.asignar_subordinado(subordinadoId, jefeId));
    }

    /* PUT /asignar_encargado/{encargadoId}/{jefeId}: asigna un encargado como subordinado de un jefe de obra. */
    @PutMapping("/asignar_encargado/{encargadoId}/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<perfil> asignar_encargado(
            @PathVariable UUID encargadoId,
            @PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.asignar_encargado_a_jefe(encargadoId, jefeId));
    }

    /* GET /obra/{obraId}: devuelve todas las asignaciones (perfiles) de una obra concreta. */
    @GetMapping("/obra/{obraId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<List<asignacion_obra>> get_asignaciones_obra(
            @PathVariable Long obraId) {
        return ResponseEntity.ok(asignacion_service.get_asignaciones_obra(obraId));
    }

    /* GET /perfil/{perfilId}: devuelve todas las obras asignadas a un perfil concreto. */
    @GetMapping("/perfil/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<List<asignacion_obra>> get_obras_de_perfil(
            @PathVariable UUID perfilId) {
        return ResponseEntity.ok(asignacion_service.get_obras_de_perfil(perfilId));
    }

    /* DELETE /eliminar/{asignacionId}: elimina la asignación de un perfil a una obra. */
    @DeleteMapping("/eliminar/{asignacionId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> eliminar_asignacion(
            @PathVariable Long asignacionId) {
        asignacion_service.eliminar_asignacion_obra(asignacionId);
        return ResponseEntity.ok().build();
    }

    /* GET /{jefeId}/subordinados: devuelve la lista de subordinados de un jefe/encargado. */
    @GetMapping("/{jefeId}/subordinados")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<perfil>> get_subordinados_de(@PathVariable UUID jefeId) {
        return ResponseEntity.ok(asignacion_service.get_mis_subordinados(jefeId));
    }

    /* DELETE /quitar_subordinado/{usuarioId}: elimina la relación jefe directo de un usuario
       (pone jefe_directo a null). */
    @DeleteMapping("/quitar_subordinado/{usuarioId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> quitar_subordinado(@PathVariable UUID usuarioId) {
        asignacion_service.quitar_jefe_directo(usuarioId);
        return ResponseEntity.ok().build();
    }

    /* GET /mis_obras: devuelve las obras asignadas al usuario autenticado. */
    @GetMapping("/mis_obras")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<asignacion_obra>> get_mis_obras(Authentication auth) {
        UUID id = UUID.fromString(auth.getName());
        return ResponseEntity.ok(asignacion_service.get_mis_obras(id));
    }

    /* POST /asignar_obras_batch/{perfilId}: asigna múltiples obras a un perfil de una sola vez. */
    @PostMapping("/asignar_obras_batch/{perfilId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> asignar_obras_batch(
            @PathVariable UUID perfilId,
            @RequestBody List<Long> obraIds) {
        asignacion_service.asignar_obras_batch(perfilId, obraIds);
        return ResponseEntity.ok().build();
    }

    /* PUT /asignar_subordinados_batch/{jefeId}: asigna múltiples subordinados a un jefe de una sola vez. */
    @PutMapping("/asignar_subordinados_batch/{jefeId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Void> asignar_subordinados_batch(
            @PathVariable UUID jefeId,
            @RequestBody List<UUID> subordinadoIds) {
        asignacion_service.asignar_subordinados_batch(jefeId, subordinadoIds);
        return ResponseEntity.ok().build();
    }
}
