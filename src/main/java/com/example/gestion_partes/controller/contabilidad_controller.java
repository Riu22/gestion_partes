package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.service.contabilidad_service;
import com.example.gestion_partes.service.csv_export_service;
import com.example.gestion_partes.service.obra_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quincena")
public class contabilidad_controller {

    @Autowired
    private contabilidad_service contabilidad_service;

    @Autowired
    private csv_export_service csv_export_service;

    @Autowired
    obra_service obra_service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<quincena_dto>> get_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(contabilidad_service.getResumenQuincena(desde, hasta));
    }

    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportar_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return csv_export_service.buildQuincenaCsv(
                contabilidad_service.getResumenQuincena(desde, hasta), desde, hasta);
    }

    @GetMapping("/contabilidad-detalle-json")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJson(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(contabilidad_service.getDetalleContabilidad(desde, hasta));
    }

    @GetMapping("/exportar-detalle-csv")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportarDetalleCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return csv_export_service.buildDetalleCsv(
                contabilidad_service.getDetalleContabilidad(desde, hasta), desde, hasta);
    }

    @GetMapping("/jefe/contabilidad-detalle-json")
    @PreAuthorize("hasRole('JEFE_DE_OBRA')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJsonJefe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        UUID perfilId = UUID.fromString(authentication.getName());
        List<Long> obraIds = obra_service.getObrasAsignadasAUsuario(perfilId);

        if (obraIds.isEmpty()) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(
                contabilidad_service.getDetalleContabilidadPorObras(desde, hasta, obraIds));
    }

    @GetMapping("/jefe/exportar-detalle-csv")
    @PreAuthorize("hasRole('JEFE_DE_OBRA')")
    public ResponseEntity<byte[]> exportarDetalleCsvJefe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        UUID perfilId = UUID.fromString(authentication.getName());
        List<Long> obraIds = obra_service.getObrasAsignadasAUsuario(perfilId);

        if (obraIds.isEmpty()) return ResponseEntity.ok(new byte[0]);

        return csv_export_service.buildDetalleCsv(
                contabilidad_service.getDetalleContabilidadPorObras(desde, hasta, obraIds),
                desde, hasta);
    }
}