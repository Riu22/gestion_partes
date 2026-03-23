package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.service.partes_service;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/v1/partes")
public class partes_controller {

    @Autowired
    partes_service partes_service;

    // Cualquier rol autenticado puede crear un parte (el servicio valida que sea el suyo)
    @PostMapping("/new_parte")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<partes_trabajo> create_parte(
            @RequestBody partes_dto new_partes,
            Authentication auth) {
        return ResponseEntity.ok(partes_service.create_parte(new_partes, auth.getName()));
    }

    // GET, no POST — no se envía cuerpo para listar
    @GetMapping("/get_partes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> get_partes(Authentication auth) {
        return ResponseEntity.ok(partes_service.get_partes_jerarquico(auth.getName()));
    }

    @PutMapping("/validar/{parteId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
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
}
