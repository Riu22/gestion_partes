package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.create_user_dto;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.repo.perfil_repo;
import com.example.gestion_partes.service.user_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/v1/user")
public class user_controller {

    @Autowired
    perfil_repo perfil_repo;
    @Autowired
    user_service user_service;

    // Obtener los datos del usuario que está logueado actualmente
    @GetMapping("/me")
    public ResponseEntity<perfil> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(perfil_repo.findByEmail(email).orElseThrow());
    }

    @PostMapping("/create_user")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ResponseEntity<String> create_user(@RequestBody create_user_dto new_user) {
        user_service.create_user(new_user);
        return new ResponseEntity<>("Usuario creado correctamente en Auth y Perfiles", HttpStatus.CREATED);
    }
}