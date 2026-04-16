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

import java.nio.charset.StandardCharsets;
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

        List<quincena_dto> datos = partes_trabajo_repo.getResumenQuincena(desde, hasta);

        StringBuilder csv = new StringBuilder();

        // 1. Añadimos el BOM (Byte Order Mark) para UTF-8.
        // Esto evita que Excel interprete mal caracteres especiales o finales de archivo.
        csv.append('\ufeff');

        // 2. Cabecera (Usamos punto y coma si el Excel de administración está en español)
        csv.append("Código;Nombre;Obra;Total Horas\n");

        for (int i = 0; i < datos.size(); i++) {
            quincena_dto linea = datos.get(i);

            // Usamos punto y coma para separar columnas y la coma para decimales
            csv.append(String.format("%s;%s;%s;%.2f",
                    linea.getCodigo() != null ? linea.getCodigo() : "",
                    linea.getNombre(),
                    linea.getObra(),
                    linea.getTotal_horas()));

            // 3. Solo añadimos salto de línea si NO es el último registro
            // Esto elimina la fila fantasma de ceros al final.
            if (i < datos.size() - 1) {
                csv.append("\n");
            }
        }

        // 4. Convertimos a bytes especificando explícitamente UTF-8
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        // Especificamos el charset en el Content-Type
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                "quincena_" + desde + "_" + hasta + ".csv");

        // 5. Definir el tamaño exacto ayuda a cerrar el stream correctamente
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}
