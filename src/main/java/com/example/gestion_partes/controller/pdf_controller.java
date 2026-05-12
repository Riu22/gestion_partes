package com.example.gestion_partes.controller;

import com.example.gestion_partes.service.pdf_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pdf")
public class pdf_controller {

    @Autowired
    private pdf_service pdf_service;

    /**
     * PDF único con todas las obras seleccionadas en un solo archivo.
     * GET /api/v1/pdf/partes?desde=...&hasta=...&obraIds=1&obraIds=2&perfilIds=...
     */
    @GetMapping("/partes")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> generarPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<Long>   obraIds,
            @RequestParam(required = false) List<String> perfilIds) {
        try {
            byte[] pdf = pdf_service.generarPdfPartes(obraIds, perfilIds, desde, hasta);
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

    /**
     * ZIP con un PDF por cada obra física seleccionada.
     * GET /api/v1/pdf/partes-zip?desde=...&hasta=...&obraIds=1&obraIds=2&perfilIds=...
     */
    @GetMapping("/partes-zip")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> generarZip(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<Long>   obraIds,
            @RequestParam(required = false) List<String> perfilIds) {
        try {
            byte[] zip = pdf_service.generarZipPartes(obraIds, perfilIds, desde, hasta);
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

    @GetMapping("/zip-por-operario")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> zipPorOperario(
            @RequestParam(required = false) List<Long> obraIds,
            @RequestParam(required = false) List<String> perfilIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta)
            throws Exception {

        byte[] zip = pdf_service.generarZipPartesPorOperario(obraIds, perfilIds, desde, hasta);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=partes_por_operario.zip")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}