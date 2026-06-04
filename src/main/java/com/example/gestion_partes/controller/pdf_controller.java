/* Controlador REST para la generación y descarga de PDFs de partes de trabajo.
   Permite generar:
   - Un único PDF con todas las obras
   - Un ZIP con un PDF por cada obra+especialidad
   - Un ZIP con un PDF por cada operario+especialidad
   Los jefes de obra solo ven sus obras asignadas. */
package com.example.gestion_partes.controller;

import com.example.gestion_partes.service.asignacion_service;
import com.example.gestion_partes.service.pdf_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pdf")
public class pdf_controller {

    @Autowired
    private pdf_service pdf_service;
    @Autowired
    private asignacion_service asignacion_service;

    /* GET /partes: genera y descarga un único PDF con todos los partes filtrados por obra, perfil y rango de fechas.
       Los jefes de obra solo ven sus obras asignadas (resuelto en resolverObras). */
    @GetMapping("/partes")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA')")
    public ResponseEntity<byte[]> generarPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<Long> obraIds,
            @RequestParam(required = false) List<String> perfilIds) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            List<Long> obrasFiltradas = resolverObras(obraIds, auth);
            byte[] pdf = pdf_service.generarPdfPartes(obrasFiltradas, perfilIds, desde, hasta);
            String filename = "partes_" + desde + "_" + hasta + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /* GET /partes-zip: genera y descarga un ZIP con un PDF por cada combinación obra+especialidad. */
    @GetMapping("/partes-zip")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA')")
    public ResponseEntity<byte[]> generarZip(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<Long> obraIds,
            @RequestParam(required = false) List<String> perfilIds) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            List<Long> obrasFiltradas = resolverObras(obraIds, auth);
            byte[] zip = pdf_service.generarZipPartes(obrasFiltradas, perfilIds, desde, hasta);
            String filename = "partes_" + desde + "_" + hasta + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(zip);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /* GET /zip-por-operario: genera y descarga un ZIP con un PDF por cada combinación operario+especialidad. */
    @GetMapping("/zip-por-operario")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION','JEFE_DE_OBRA')")
    public ResponseEntity<byte[]> zipPorOperario(
            @RequestParam(required = false) List<Long> obraIds,
            @RequestParam(required = false) List<String> perfilIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            List<Long> obrasFiltradas = resolverObras(obraIds, auth);
            byte[] zip = pdf_service.generarZipPartesPorOperario(obrasFiltradas, perfilIds, desde, hasta);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"partes_por_operario.zip\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(zip);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /* Resuelve las obras que puede ver un usuario según su rol.
       Si es JEFE_DE_OBRA, filtra solo las obras que tiene asignadas.
       Si es ADMINISTRACION/GESTION, usa las obras pasadas como parámetro (todas si es null). */
    private List<Long> resolverObras(List<Long> obraIds, Authentication auth) {
        boolean esJefe = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_JEFE_DE_OBRA"));

        if (!esJefe) return obraIds;

        Jwt jwt = (Jwt) auth.getPrincipal();
        String userId = jwt.getSubject();
        List<Long> obrasDelJefe = asignacion_service.getObrasDeJefe(UUID.fromString(userId));

        if (obraIds == null || obraIds.isEmpty()) return obrasDelJefe;

        return obraIds.stream()
                .filter(obrasDelJefe::contains)
                .collect(Collectors.toList());
    }
}
