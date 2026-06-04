/* Controlador REST para gestionar las fechas libres de los usuarios.
   Permite habilitar/deshabilitar fechas específicas en las que un usuario puede registrar partes
   aunque estén fuera del rango normal. Útil para días festivos habilitados o recuperaciones. */
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

    @Autowired
    configuration_service service;

    /* POST /habilitar/{id}: añade una o varias fechas libres para un perfil concreto.
       Solo ADMINISTRACION y GESTION pueden hacerlo. */
    @PostMapping("/habilitar/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> habilitar(
            @PathVariable String id,
            @RequestBody List<LocalDate> fechas) {
        service.habilitarFechas(id, fechas);
        return ResponseEntity.ok("Fechas añadidas: " + fechas);
    }

    /* DELETE /deshabilitar/{id}/{fecha}: elimina una fecha libre concreta de un perfil. */
    @DeleteMapping("/deshabilitar/{id}/{fecha}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> deshabilitarFecha(
            @PathVariable String id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        service.deshabilitarFecha(id, fecha);
        return ResponseEntity.ok("Fecha eliminada");
    }

    /* DELETE /deshabilitar/{id}: elimina todas las fechas libres de un perfil. */
    @DeleteMapping("/deshabilitar/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> deshabilitarTodas(@PathVariable String id) {
        service.deshabilitarTodas(id);
        return ResponseEntity.ok("Todas las fechas eliminadas");
    }

    /* GET /: devuelve un mapa con todos los usuarios activos y sus fechas libres asignadas. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<Map<String, List<LocalDate>>> listar() {
        return ResponseEntity.ok(service.getUsuariosActivos());
    }

    /* GET /mis-fechas: devuelve las fechas libres del usuario autenticado. */
    @GetMapping("/mis-fechas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LocalDate>> misFechas(Authentication auth) {
        return ResponseEntity.ok(service.getFechasDeUsuario(auth.getName()));
    }
}
