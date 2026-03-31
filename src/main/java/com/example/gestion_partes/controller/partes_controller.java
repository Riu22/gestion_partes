package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.partes_jefe_dto;
import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.partes_jefe;
import com.example.gestion_partes.model.partes_jefe;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.service.parte_jefe_service;
import com.example.gestion_partes.service.partes_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/partes")
public class partes_controller {

    @Autowired partes_service partes_service;
    @Autowired parte_jefe_service parte_jefe_service;
    @Autowired
    partes_trabajo_repo partes_trabajo_repo;

    // ─── PARTES OPERARIO / ENCARGADO ───────────────────────────

    @PostMapping("/new_parte")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','OPERARIO','ENCARGADO')")
    public ResponseEntity<partes_trabajo> create_parte(
            @RequestBody partes_dto dto,
            Authentication auth) {
        return ResponseEntity.ok(partes_service.create_parte(dto, auth.getName()));
    }

    @GetMapping("/get_partes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<partes_trabajo>> get_partes(Authentication auth) {
        return ResponseEntity.ok(partes_service.get_partes_jerarquico(auth.getName()));
    }

    @PutMapping("/validar/{parteId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','ENCARGADO','JEFE_DE_OBRA')")
    public ResponseEntity<?> validar_parte(
            @PathVariable Long parteId,
            Authentication auth) {
        partes_service.validar_parte(parteId, auth.getName());
        return ResponseEntity.ok("Parte validado correctamente");
    }

    @DeleteMapping("/delete/{parteId}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<?> delete_parte(@PathVariable Long parteId) {
        partes_service.delete_parte(parteId);
        return ResponseEntity.ok().build();
    }

    // ─── PARTES JEFE DE OBRA ───────────────────────────────────

    @PostMapping("/new_parte_jefe")
    @PreAuthorize("hasRole('JEFE_DE_OBRA')")
    public ResponseEntity<partes_jefe> create_parte_jefe(
            @RequestBody partes_jefe_dto dto,
            Authentication auth) {
        return ResponseEntity.ok(
                parte_jefe_service.create_parte_jefe(dto, auth.getName()));
    }

    @GetMapping("/get_partes_jefe")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA')")
    public ResponseEntity<List<partes_jefe>> get_partes_jefe(Authentication auth) {
        return ResponseEntity.ok(
                parte_jefe_service.get_partes_jefe(auth.getName()));
    }

    @PutMapping("/validar_jefe/{parteId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> validar_parte_jefe(
            @PathVariable Long parteId,
            Authentication auth) {
        parte_jefe_service.validar_parte_jefe(parteId, auth.getName());
        return ResponseEntity.ok("Parte de jefe validado correctamente");
    }

    @DeleteMapping("/delete_jefe/{parteId}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<?> delete_parte_jefe(@PathVariable Long parteId) {
        parte_jefe_service.delete_parte_jefe(parteId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<List<partes_trabajo>> buscar(
            @RequestParam(required = false) String obra,
            @RequestParam(required = false) String operario,
            @RequestParam(required = false) String especialidad) {

        // Validamos si la especialidad existe en nuestro Enum solo por seguridad
        String especialidadParaQuery = null;
        if (especialidad != null && !especialidad.isBlank()) {
            try {
                // Esto comprueba que el texto enviado sea un valor válido del Enum
                especialidadParaQuery = com.example.gestion_partes.model.especialidad
                        .valueOf(especialidad.toUpperCase().trim()).name();
            } catch (IllegalArgumentException e) {
                // Si mandan algo que no existe, devolvemos lista vacía
                return ResponseEntity.ok(List.of());
            }
        }

        String obraFiltro = (obra != null && !obra.isBlank()) ? obra : null;
        String operarioFiltro = (operario != null && !operario.isBlank()) ? operario : null;

        // Ahora pasamos 'especialidadParaQuery' que es un String, no un Enum
        return ResponseEntity.ok(partes_trabajo_repo.buscarPartes(obraFiltro, operarioFiltro, especialidadParaQuery));
    }
}