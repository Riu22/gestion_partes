package com.example.gestion_partes.controller;

import com.example.gestion_partes.service.configuration_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/fecha-libre")
public class configuration_controller {

    @Autowired configuration_service service;

    // Añadir fechas sueltas a un usuario
    // Body: ["2025-05-21", "2025-04-18", "2025-06-30"]
    @PostMapping("/habilitar/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> habilitar(
            @PathVariable String id,
            @RequestBody List<LocalDate> fechas) {
        service.habilitarFechas(id, fechas);
        return ResponseEntity.ok("Fechas añadidas: " + fechas);
    }

    // Quitar una fecha concreta
    @DeleteMapping("/deshabilitar/{id}/{fecha}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> deshabilitarFecha(
            @PathVariable String id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        service.deshabilitarFecha(id, fecha);
        return ResponseEntity.ok("Fecha eliminada");
    }

    // Quitar todas las fechas de un usuario
    @DeleteMapping("/deshabilitar/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> deshabilitarTodas(@PathVariable String id) {
        service.deshabilitarTodas(id);
        return ResponseEntity.ok("Todas las fechas eliminadas");
    }

    // Listar todos los usuarios con fechas activas
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, List<LocalDate>>> listar() {
        return ResponseEntity.ok(service.getUsuariosActivos());
    }

    // El usuario consulta sus propias fechas permitidas
    @GetMapping("/mis-fechas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LocalDate>> misFechas(Authentication auth) {
        return ResponseEntity.ok(
                service.getFechasDeUsuario(auth.getName()).stream().sorted().toList());
    }
}