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

    // ─── Obtener resumen de quincena ──────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<quincena_dto>> get_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                partes_trabajo_repo.getResumenQuincena(desde, hasta));
    }

    // ─── Exportar quincena como CSV ───────────────────────────────────────────
    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportar_quincena(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<quincena_dto> datos = partes_trabajo_repo.getResumenQuincena(desde, hasta);

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff'); // BOM UTF-8 para Excel
        csv.append("Código;Nombre;Obra;Total Horas\n");

        for (int i = 0; i < datos.size(); i++) {
            quincena_dto linea = datos.get(i);
            csv.append(String.format("%s;%s;%s;%.2f",
                    linea.getCodigo() != null ? linea.getCodigo() : "",
                    linea.getNombre(),
                    linea.getObra(),
                    linea.getTotal_horas()));
            if (i < datos.size() - 1) {
                csv.append("\n");
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                "quincena_" + desde + "_" + hasta + ".csv");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ─── Detalle contabilidad JSON ────────────────────────────────────────────
    @GetMapping("/contabilidad-detalle-json")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<List<Map<String, Object>>> getDetalleJson(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<Map<String, Object>> resultado = procesarLogicaDetalle(desde, hasta);
        return ResponseEntity.ok(resultado);
    }

    // ─── Exportar detalle como CSV ────────────────────────────────────────────
    @GetMapping("/exportar-detalle-csv")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<byte[]> exportarDetalleCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<Map<String, Object>> filas = procesarLogicaDetalle(desde, hasta);
        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff'); // BOM UTF-8 para Excel

        // Cabecera
        csv.append("Código;Operario;Grupo Profesional;Obra");
        for (LocalDate dia : diasRango) {
            csv.append(";").append(dia.getDayOfMonth())
                    .append("/").append(dia.getMonthValue());
        }
        csv.append(";TOTAL HORAS\n");

        // Cuerpo
        for (Map<String, Object> fila : filas) {
            csv.append(fila.get("codigo")).append(";")
                    .append(fila.get("operario")).append(";")
                    .append(fila.get("grupo_profesional")).append(";")
                    .append(fila.get("obra"));

            @SuppressWarnings("unchecked")
            Map<LocalDate, Double> horasDias =
                    (Map<LocalDate, Double>) fila.get("horas_por_dia");

            for (LocalDate dia : diasRango) {
                double h = horasDias.getOrDefault(dia, 0.0);
                // Cero → guion para que coincida visualmente con la interfaz web
                // Coma decimal para que Excel en español lo reconozca como número
                csv.append(";").append(h == 0.0 ? "" : String.format("%.2f", h).replace('.', ','));
            }
            double total = (Double) fila.get("total_horas");
            csv.append(";").append(String.format("%.2f", total).replace('.', ',')).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        String nombreArchivo = String.format(
                "contabilidad_detalle_%s_al_%s.csv", desde, hasta);
        headers.setContentDispositionFormData("attachment", nombreArchivo);
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ─── Lógica compartida ────────────────────────────────────────────────────
    /**
     * Agrupa los partes por (código operario + obra) acumulando horas por día.
     *
     * FIX: el mapa interno "horas_por_dia" usa LocalDate como clave (antes usaba
     * String), de modo que exportarDetalleCsv puede hacer getOrDefault(LocalDate)
     * y obtener el valor real en lugar de 0.0 siempre.
     */
    private List<Map<String, Object>> procesarLogicaDetalle(
            LocalDate desde, LocalDate hasta) {

        List<contabilidad_detalle_dto> datos =
                partes_trabajo_repo.getDetalleContabilidad(desde, hasta);

        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {

            String nombreObra  = (d.getObra_nombre() != null) ? d.getObra_nombre() : "Sin Obra";
            String codigoUser  = (d.getCodigo()      != null) ? d.getCodigo()      : "000";
            String clave       = codigoUser + "|" + nombreObra;

            if (!mapaAgrupado.containsKey(clave)) {
                Map<String, Object> fila = new LinkedHashMap<>();

                String aps = (d.getApellidos() != null) ? d.getApellidos().toUpperCase() : "";
                String nom = (d.getNombre()    != null) ? d.getNombre()                  : "S/N";
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                fila.put("codigo",           codigoUser);
                fila.put("operario",         operarioFull);
                fila.put("obra",             nombreObra);
                fila.put("grupo_profesional",
                        (d.getGrupo_profesional() != null) ? d.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",    new HashMap<LocalDate, Double>()); // ← LocalDate
                fila.put("total_horas",      0.0);
                mapaAgrupado.put(clave, fila);
            }

            @SuppressWarnings("unchecked")
            Map<LocalDate, Double> horasDia =
                    (Map<LocalDate, Double>) mapaAgrupado.get(clave).get("horas_por_dia");

            // ── FIX: usamos LocalDate directamente como clave (antes era String)
            LocalDate fechaKey = d.getFecha();
            double horas = (d.getHoras_totales() != null) ? d.getHoras_totales() : 0.0;

            if (fechaKey != null) {
                horasDia.put(fechaKey, horasDia.getOrDefault(fechaKey, 0.0) + horas);
            }

            double totalAnterior = (double) mapaAgrupado.get(clave).get("total_horas");
            mapaAgrupado.get(clave).put("total_horas", totalAnterior + horas);
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}