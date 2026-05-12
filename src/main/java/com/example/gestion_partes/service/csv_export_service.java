package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.quincena_dto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class csv_export_service {

    public ResponseEntity<byte[]> buildQuincenaCsv(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) {

        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff');
        csv.append("Código;Nombre;Obra;Total Horas\n");

        for (int i = 0; i < datos.size(); i++) {
            quincena_dto linea = datos.get(i);
            csv.append(String.format("%s;%s;%s;%.2f",
                    linea.getCodigo() != null ? linea.getCodigo() : "",
                    linea.getNombre(),
                    linea.getObra(),
                    linea.getTotal_horas()));
            if (i < datos.size() - 1) csv.append("\n");
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