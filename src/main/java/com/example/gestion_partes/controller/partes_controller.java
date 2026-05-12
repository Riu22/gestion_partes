package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.partes_jefe_dto;
import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.partes_jefe;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.service.configuration_service;
import com.example.gestion_partes.service.parte_jefe_service;
import com.example.gestion_partes.service.partes_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("api/v1/partes")
public class partes_controller {

    @Autowired partes_service partes_service;
    @Autowired parte_jefe_service parte_jefe_service;
    @Autowired partes_trabajo_repo partes_trabajo_repo;
    @Autowired configuration_service configuration_service;

    // ─── PARTES OPERARIO / ENCARGADO ───────────────────────────

    @PostMapping("/new_parte")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','OPERARIO','ENCARGADO')")
    public ResponseEntity<?> create_parte(
            @RequestBody partes_dto dto,
            Authentication auth) {
        System.out.println("DEBUG: Recibiendo nombre_firmado: " + dto.nombre_firmado());
        try {
            return ResponseEntity.ok(partes_service.create_parte(dto, auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/get_partes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<partes_trabajo>> get_partes(Authentication auth) {
        return ResponseEntity.ok(partes_service.get_partes_jerarquico(auth.getName()));
    }

    @DeleteMapping("/delete/{parteId}")
    public ResponseEntity<?> delete_parte(
            @PathVariable Long parteId,
            @AuthenticationPrincipal Jwt jwt) {
        String sub = jwt.getSubject();
        partes_service.delete_parte(parteId, sub);
        return ResponseEntity.ok().build();
    }

    // ─── FECHAS CON PARTE (para bloquear en DatePicker) ───────

    /**
     * Devuelve la lista de fechas en las que el perfil con [id]
     * ya tiene un parte registrado. Solo accesible por admin/gestión.
     */
    @GetMapping("/fechas-con-parte/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<LocalDate>> fechasConParte(@PathVariable String id) {
        return ResponseEntity.ok(partes_service.getFechasConParte(id));
    }

    /**
     * Devuelve las fechas con parte del propio usuario autenticado.
     * Lo usan operarios/encargados para que el DatePicker bloquee
     * los días que ya tienen cubiertos.
     */
    @GetMapping("/mis-fechas-con-parte")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LocalDate>> misFechasConParte(Authentication auth) {
        System.out.println(">>> auth.getName(): " + auth.getName());
        System.out.println(">>> auth.class: " + auth.getClass().getName());
        return ResponseEntity.ok(partes_service.getFechasConPartePorUsername(auth.getName()));
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

        String especialidadParaQuery = null;
        if (especialidad != null && !especialidad.isBlank()) {
            try {
                especialidadParaQuery = com.example.gestion_partes.model.especialidad
                        .valueOf(especialidad.toUpperCase().trim()).name();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(List.of());
            }
        }

        String obraFiltro = (obra != null && !obra.isBlank()) ? obra : null;
        String operarioFiltro = (operario != null && !operario.isBlank()) ? operario : null;

        return ResponseEntity.ok(
                partes_trabajo_repo.buscarPartes(obraFiltro, operarioFiltro, especialidadParaQuery));
    }
    @GetMapping("/puede-fecha-libre")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> puedeFechaLibre(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        // Now we pass both the User ID and the specific Date to the service
        return ResponseEntity.ok(
                configuration_service.puedeUsarFechaLibre(auth.getName(), fecha));
    }
    @PutMapping("/update/{parteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<partes_trabajo> update_parte(
            @PathVariable Long parteId,
            @RequestBody partes_dto dto,
            Authentication auth) {
        return ResponseEntity.ok(
                partes_service.update_parte(parteId, dto, auth.getName()));
    }
}