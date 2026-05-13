package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.create_user_dto;
import com.example.gestion_partes.dto.update_user_dto;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.repo.perfil_repo;
import com.example.gestion_partes.service.user_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/user")
public class user_controller {

    @Autowired
    perfil_repo perfil_repo;
    @Autowired
    user_service user_service;

    @GetMapping("/me")
    public ResponseEntity<perfil> getMyProfile(Authentication authentication) {
        // Convertimos el String del sub a UUID
        UUID userId = UUID.fromString(authentication.getName());

        System.out.println(">>> Buscando perfil por ID: " + userId);

        return ResponseEntity.ok(perfil_repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Perfil no encontrado para el ID: " + userId)));
    }

    @PostMapping("/create_user")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<String> create_user(@RequestBody create_user_dto new_user) {
        user_service.create_user(new_user);
        return new ResponseEntity<>("Usuario creado correctamente en Auth y Perfiles", HttpStatus.CREATED);
    }

    @DeleteMapping("/delete_user/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<String> delete_user(@PathVariable UUID id) {
        try {
            user_service.delete_user(id);
            return new ResponseEntity<>("Usuario eliminado correctamente", HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al eliminar el usuario: " + e.getMessage());        }

    }

    @PutMapping("/update_user/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION', 'GESTION')")
    public ResponseEntity<perfil> update_profile(@PathVariable UUID id, @RequestBody update_user_dto perfil_datos) {
        return ResponseEntity.ok(user_service.update_profile(id, perfil_datos));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<perfil>> get_all_users() {
        return ResponseEntity.ok(perfil_repo.findAllByOrderByActivoDescApellidosAscNameAsc());
    }
}