/* Controlador REST para la gestión de usuarios/perfiles.
   Expone endpoints para obtener el perfil propio, crear, actualizar, eliminar usuarios
   y listar todos los perfiles del sistema. */
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

    /* GET /me: devuelve el perfil del usuario autenticado (identificado por el sub del JWT). */
    @GetMapping("/me")
    public ResponseEntity<perfil> getMyProfile(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        return ResponseEntity.ok(perfil_repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Perfil no encontrado para el ID: " + userId)));
    }

    /* POST /create_user: crea un nuevo usuario en Supabase Auth y su perfil en la BD local.
       Recibe los datos en create_user_dto (email, password, nombre, apellidos, rol, etc.). */
    @PostMapping("/create_user")
    @PreAuthorize("hasAnyRole('ADMINISTRACION')")
    public ResponseEntity<String> create_user(@RequestBody create_user_dto new_user) {
        user_service.create_user(new_user);
        return new ResponseEntity<>("Usuario creado correctamente en Auth y Perfiles", HttpStatus.CREATED);
    }

    /* DELETE /delete_user/{id}: elimina un usuario de Supabase Auth y su perfil de la BD local.
       Solo ADMINISTRACION puede eliminar usuarios. */
    @DeleteMapping("/delete_user/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<String> delete_user(@PathVariable UUID id) {
        try {
            user_service.delete_user(id);
            return new ResponseEntity<>("Usuario eliminado correctamente", HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al eliminar el usuario: " + e.getMessage());        }

    }

    /* PUT /update_user/{id}: actualiza los datos del perfil de un usuario (nombre, apellidos, rol, etc.). */
    @PutMapping("/update_user/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION')")
    public ResponseEntity<perfil> update_profile(@PathVariable UUID id, @RequestBody update_user_dto perfil_datos) {
        return ResponseEntity.ok(user_service.update_profile(id, perfil_datos));
    }

    /* GET /all: devuelve todos los perfiles del sistema ordenados por activo (primero los activos) y luego alfabéticamente. */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMINISTRACION')")
    public ResponseEntity<List<perfil>> get_all_users() {
        return ResponseEntity.ok(perfil_repo.findAllByOrderByActivoDescApellidosAscNameAsc());
    }
}
