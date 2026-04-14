package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.repo.partes_trabajo_repo;
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
@RequestMapping("/api/v1/quincena")
public class contabilidad_controller {

    @Autowired
    partes_trabajo_repo partes_trabajo_repo;

    // Obtener resumen de quincena
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<quincena_dto>> get_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                partes_trabajo_repo.getResumenQuincena(desde, hasta));
    }

    // Exportar quincena como CSV
    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportar_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<quincena_dto> datos =
                partes_trabajo_repo.getResumenQuincena(desde, hasta);

        StringBuilder csv = new StringBuilder();
        csv.append("Código,Nombre,Obra,Total Horas\n");
        for (quincena_dto linea : datos) {
            csv.append(String.format("%s,%s,%s,%.2f\n",
                    linea.getCodigo() != null ? linea.getCodigo() : "",
                    linea.getNombre(),
                    linea.getObra(),
                    linea.getTotal_horas()));
        }

        byte[] bytes = csv.toString().getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment",
                "quincena_" + desde + "_" + hasta + ".csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}
