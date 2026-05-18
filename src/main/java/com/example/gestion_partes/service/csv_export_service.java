package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.quincena_dto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class csv_export_service {

    public ResponseEntity<byte[]> buildQuincenaCsv(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) {

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff');
        csv.append("Obra;Código;Apellidos;Nombre;Horas Operario;Total Obra\n");

        // Agrupar por obra manteniendo orden
        Map<String, List<quincena_dto>> porObra = new LinkedHashMap<>();
        for (quincena_dto d : datos) {
            String obra = d.getObra() != null ? d.getObra() : "Sin Obra";
            porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(d);
        }

        for (Map.Entry<String, List<quincena_dto>> entry : porObra.entrySet()) {
            String obra = entry.getKey();
            List<quincena_dto> operarios = entry.getValue();
            double totalObra = operarios.stream()
                    .mapToDouble(o -> o.getTotal_horas() != null ? o.getTotal_horas() : 0.0)
                    .sum();

            for (int i = 0; i < operarios.size(); i++) {
                quincena_dto linea = operarios.get(i);
                csv.append(String.format("%s;%s;%s;%s;%.2f;%s",
                        i == 0 ? obra : "",
                        linea.getCodigo() != null ? linea.getCodigo() : "",
                        linea.getApellidos() != null ? linea.getApellidos() : "",
                        linea.getNombre() != null ? linea.getNombre() : "",
                        linea.getTotal_horas() != null ? linea.getTotal_horas() : 0.0,
                        i == 0 ? String.format("%.2f", totalObra) : ""
                ));
                csv.append("\n");
            }
            csv.append("\n");
        }

        return buildResponse(csv.toString(),
                "quincena_" + desde + "_" + hasta + ".csv");
    }

    public ResponseEntity<byte[]> buildDetalleCsv(
            List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) {

        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff');

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
                csv.append(";").append(h == 0.0 ? ""
                        : String.format("%.2f", h).replace('.', ','));
            }
            double total = (Double) fila.get("total_horas");
            csv.append(";")
                    .append(String.format("%.2f", total).replace('.', ','))
                    .append("\n");
        }

        return buildResponse(csv.toString(),
                String.format("contabilidad_detalle_%s_al_%s.csv", desde, hasta));
    }

    private ResponseEntity<byte[]> buildResponse(String csv, String nombreArchivo) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", nombreArchivo);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}