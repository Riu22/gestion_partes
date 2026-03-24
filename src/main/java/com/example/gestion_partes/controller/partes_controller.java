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

import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/v1/partes")
public class partes_controller {

    @Autowired
    partes_service partes_service;

    @PostMapping("/new_parte")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<partes_trabajo> create_parte(
            @RequestBody partes_dto new_partes,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(partes_service.create_parte(new_partes, userId));
    }

    // GET, no POST — no se envía cuerpo para listar
    @GetMapping("/get_partes")
    public ResponseEntity<?> get_partes(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<partes_trabajo> lista = partes_service.get_partes_jerarquico(userId);

        // ESTO TE DIRÁ LA VERDAD EN LA CONSOLA DE INTELLIJ
        System.out.println("DEBUG: Se han encontrado " + lista.size() + " partes en la DB");

        return ResponseEntity.ok(lista);
    }

    @PutMapping("/validar/{parteId}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA','ENCARGADO')")
    public ResponseEntity<?> validar_parte(
            @PathVariable Long parteId,
            Authentication auth) {
        UUID revisorId = UUID.fromString(auth.getName());
        partes_service.validar_parte(parteId, revisorId);
        return ResponseEntity.ok("Parte validado correctamente");
    }

    @DeleteMapping("/delete/{parteId}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<?> delete_parte(@PathVariable Long parteId) {
        partes_service.delete_parte(parteId);
        return ResponseEntity.ok().build();
    }
}
