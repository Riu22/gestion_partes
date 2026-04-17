package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
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
import java.util.*;
import java.util.stream.Collectors;

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
    @GetMapping("/contabilidad-detalle-json")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJson(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        // Llamamos a la lógica común
        List<Map<String, Object>> resultado = procesarLogicaDetalle(desde, hasta);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/exportar-detalle-csv")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportarDetalleCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        // 1. Obtener la lógica procesada
        List<Map<String, Object>> filas = procesarLogicaDetalle(desde, hasta);
        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1)).collect(Collectors.toList());

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff'); // BOM para Excel

        // 2. Cabecera
        csv.append("Código;Operario;Grupo Profesional;Obra");
        for (LocalDate dia : diasRango) {
            csv.append(";").append(dia.getDayOfMonth()).append("/").append(dia.getMonthValue());
        }
        csv.append(";TOTAL HORAS\n");

        // 3. Cuerpo del CSV
        for (Map<String, Object> fila : filas) {
            csv.append(fila.get("codigo")).append(";")
                    .append(fila.get("operario")).append(";")
                    .append(fila.get("grupo_profesional")).append(";")
                    .append(fila.get("obra"));

            Map<LocalDate, Double> horasDias = (Map<LocalDate, Double>) fila.get("horas_por_dia");
            for (LocalDate dia : diasRango) {
                csv.append(String.format(";%.2f", horasDias.getOrDefault(dia, 0.0)));
            }
            csv.append(String.format(";%.2f\n", (Double) fila.get("total_horas")));
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        String nombreArchivo = String.format("contabilidad_detalle_%s_al_%s.csv", desde, hasta);
        headers.setContentDispositionFormData("attachment", nombreArchivo);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * Lógica compartida para agrupar y procesar los datos
     */
    private List<Map<String, Object>> procesarLogicaDetalle(LocalDate desde, LocalDate hasta) {
        List<contabilidad_detalle_dto> datos = partes_trabajo_repo.getDetalleContabilidad(desde, hasta);
        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            // 1. Manejo seguro de la Obra y Código para la clave
            String nombreObra = (d.getObra_nombre() != null) ? d.getObra_nombre() : "Sin Obra";
            String codigoUser = (d.getCodigo() != null) ? d.getCodigo() : "000";

            String clave = codigoUser + "|" + nombreObra;

            if (!mapaAgrupado.containsKey(clave)) {
                Map<String, Object> fila = new LinkedHashMap<>();

                // 2. Formateo seguro del Operario (Evita el NullPointerException)
                String aps = (d.getApellidos() != null) ? d.getApellidos().toUpperCase() : "";
                String nom = (d.getNombre() != null) ? d.getNombre() : "S/N";

                // Si hay apellidos, "APELLIDO, Nombre", si no, solo "Nombre"
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                fila.put("codigo", codigoUser);
                fila.put("operario", operarioFull);
                fila.put("obra", nombreObra);

                // 3. Manejo seguro del grupo profesional
                fila.put("grupo_profesional", (d.getGrupo_profesional() != null) ? d.getGrupo_profesional() : "No asignado");

                fila.put("horas_por_dia", new HashMap<String, Double>());
                fila.put("total_horas", 0.0);
                mapaAgrupado.put(clave, fila);
            }

            // 4. Suma de horas con seguridad
            Map<String, Double> horasDia = (Map<String, Double>) mapaAgrupado.get(clave).get("horas_por_dia");
            String fecha = (d.getFecha() != null) ? d.getFecha().toString() : "Sin Fecha";
            double horas = (d.getHoras_totales() != null) ? d.getHoras_totales() : 0.0;

            horasDia.put(fecha, horasDia.getOrDefault(fecha, 0.0) + horas);

            double totalAnterior = (double) mapaAgrupado.get(clave).get("total_horas");
            mapaAgrupado.get(clave).put("total_horas", totalAnterior + horas);
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}
