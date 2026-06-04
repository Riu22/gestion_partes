/* Controlador REST para la sección de contabilidad/quincenas.
   Expone endpoints para obtener resúmenes quincenales, detalle día a día (JSON y Excel),
   tanto para administración/gestión como para jefes de obra (que solo ven sus obras asignadas). */
package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.repo.perfil_repo;
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
    private csv_export_service xlsx_export_service;

    @Autowired
    private obra_service obra_service;

    @Autowired
    private perfil_repo perfilRepo;

    /* GET /: devuelve el resumen quincenal en JSON (lista de quincena_dto) para un rango de fechas.
       Cada DTO contiene obra, código de operario, apellidos, nombre y total de horas. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<quincena_dto>> get_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(contabilidad_service.getResumenQuincena(desde, hasta));
    }

    /* GET /exportar: genera y descarga un archivo Excel (.xlsx) con el resumen quincenal. */
    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportar_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws Exception {
        return xlsx_export_service.buildQuincenaXlsx(
                contabilidad_service.getResumenQuincena(desde, hasta), desde, hasta);
    }

    /* GET /contabilidad-detalle-json: devuelve el detalle día a día en JSON (horas por día y ausencias). */
    @GetMapping("/contabilidad-detalle-json")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJson(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(contabilidad_service.getDetalleContabilidad(desde, hasta));
    }

    /* GET /exportar-detalle-csv: genera y descarga un Excel con el detalle día a día. */
    @GetMapping("/exportar-detalle-csv")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportarDetalleXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws Exception {
        return xlsx_export_service.buildDetalleXlsx(
                contabilidad_service.getDetalleContabilidad(desde, hasta), desde, hasta);
    }

    /* GET /jefe/contabilidad-detalle-json: igual que el detalle JSON pero filtrando solo las obras
       asignadas al jefe de obra autenticado. Si no tiene obras ni personal a cargo, devuelve lista vacía. */
    @GetMapping("/jefe/contabilidad-detalle-json")
    @PreAuthorize("hasRole('JEFE_DE_OBRA')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJsonJefe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        UUID jefeId    = UUID.fromString(authentication.getName());
        List<Long> obraIds = obra_service.getObrasAsignadasAUsuario(jefeId);
        boolean tienePersonal = !perfilRepo.findByJefeDirecto_Id(jefeId).isEmpty();

        if (obraIds.isEmpty() && !tienePersonal) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(
                contabilidad_service.getDetalleContabilidadPorObras(desde, hasta, obraIds, jefeId));
    }

    /* GET /jefe/exportar-detalle-csv: igual que el Excel de detalle pero filtrando por las obras del jefe autenticado. */
    @GetMapping("/jefe/exportar-detalle-csv")
    @PreAuthorize("hasRole('JEFE_DE_OBRA')")
    public ResponseEntity<byte[]> exportarDetalleXlsxJefe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) throws Exception {

        UUID jefeId    = UUID.fromString(authentication.getName());
        List<Long> obraIds = obra_service.getObrasAsignadasAUsuario(jefeId);
        boolean tienePersonal = !perfilRepo.findByJefeDirecto_Id(jefeId).isEmpty();

        if (obraIds.isEmpty() && !tienePersonal) return ResponseEntity.ok(new byte[0]);

        return xlsx_export_service.buildDetalleXlsx(
                contabilidad_service.getDetalleContabilidadPorObras(desde, hasta, obraIds, jefeId),
                desde, hasta);
    }
}
