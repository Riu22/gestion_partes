package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.quincena_dto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class csv_export_service {

    // ─────────────────────────────────────────────────────────────────────────
    //  COLORES (coinciden con la UI)
    // ─────────────────────────────────────────────────────────────────────────
    private static final String COLOR_OBRA_BG      = "1E3A8A"; // azul oscuro cabecera obra
    private static final String COLOR_SUBTOTAL_BG  = "DBEAFE"; // azul muy claro fila subtotal
    private static final String COLOR_WEEKEND_BG   = "FEE2E2"; // rosa claro fin de semana / festivo
    private static final String COLOR_HEADER_BG    = "1E3A8A"; // igual que obra para la cabecera
    private static final String COLOR_BAJA_BG      = "008000"; // verde celda B
    private static final String COLOR_VAC_BG       = "FFB5C0"; // rosa celda V
    private static final String COLOR_PAT_BG       = "BFDBFE"; // azul claro celda P
    private static final String COLOR_SUBTOTAL_TEXT = "1D4ED8"; // azul texto subtotal
    private static final String COLOR_WHITE         = "FFFFFF";
    private static final String COLOR_TOTAL_TEXT    = "1D4ED8";

    // ─────────────────────────────────────────────────────────────────────────
    //  FESTIVOS NACIONALES FIJOS (mismos que contabilidad_service y Flutter)
    // ─────────────────────────────────────────────────────────────────────────
    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1),  // Año Nuevo
            MonthDay.of(1,  6),  // Reyes
            MonthDay.of(5,  1),  // Día del Trabajo
            MonthDay.of(8,  15), // Asunción
            MonthDay.of(10, 12), // Fiesta Nacional
            MonthDay.of(11, 1),  // Todos los Santos
            MonthDay.of(12, 6),  // Constitución
            MonthDay.of(12, 8),  // Inmaculada
            MonthDay.of(12, 25)  // Navidad
    );

    // ─────────────────────────────────────────────────────────────────────────
    //  QUINCENA (resumen)
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildQuincenaXlsx(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Quincena");
            sheet.setDefaultColumnWidth(18);

            // ── Estilos ───────────────────────────────────────────────────────
            CellStyle csHeader = headerStyle(wb);
            CellStyle csObra   = obraStyle(wb);
            CellStyle csNormal = normalStyle(wb);
            CellStyle csNumber = numberStyle(wb);
            CellStyle csTotal  = totalStyle(wb);

            // ── Cabecera ──────────────────────────────────────────────────────
            String[] cols = {"Obra", "Código", "Apellidos", "Nombre",
                    "Horas Operario", "Total Obra"};
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(20);
            for (int c = 0; c < cols.length; c++) {
                Cell cell = hRow.createCell(c);
                cell.setCellValue(cols[c]);
                cell.setCellStyle(csHeader);
            }

            // ── Datos agrupados por obra ───────────────────────────────────
            Map<String, List<quincena_dto>> porObra = new LinkedHashMap<>();
            for (quincena_dto d : datos) {
                String obra = d.getObra() != null ? d.getObra() : "Sin Obra";
                porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(d);
            }

            int rowIdx = 1;
            for (Map.Entry<String, List<quincena_dto>> entry : porObra.entrySet()) {
                String obra = entry.getKey();
                List<quincena_dto> operarios = entry.getValue();
                double totalObra = operarios.stream()
                        .mapToDouble(o -> o.getTotal_horas() != null ? o.getTotal_horas() : 0.0)
                        .sum();

                for (int i = 0; i < operarios.size(); i++) {
                    quincena_dto linea = operarios.get(i);
                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);

                    Cell cObra = row.createCell(0);
                    if (i == 0) {
                        cObra.setCellValue(obra);
                        cObra.setCellStyle(csObra);
                    } else {
                        cObra.setCellStyle(csNormal);
                    }

                    setCell(row, 1, safe(linea.getCodigo()),    csNormal);
                    setCell(row, 2, safe(linea.getApellidos()), csNormal);
                    setCell(row, 3, safe(linea.getNombre()),    csNormal);

                    Cell cHoras = row.createCell(4);
                    cHoras.setCellValue(linea.getTotal_horas() != null ? linea.getTotal_horas() : 0.0);
                    cHoras.setCellStyle(csNumber);

                    Cell cTotal = row.createCell(5);
                    if (i == 0) {
                        cTotal.setCellValue(totalObra);
                        cTotal.setCellStyle(csTotal);
                    } else {
                        cTotal.setCellStyle(csNormal);
                    }
                }
                sheet.createRow(rowIdx++); // fila vacía entre obras
            }

            for (int c = 0; c < 4; c++) sheet.autoSizeColumn(c);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4000);

            wb.write(out);
            return buildResponse(out.toByteArray(),
                    "quincena_" + desde + "_" + hasta + ".xlsx");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DETALLE (con días)
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildDetalleXlsx(
            List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws Exception {

        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Detalle");
            sheet.setDefaultColumnWidth(6);

            // ── Estilos ───────────────────────────────────────────────────────
            CellStyle csHeader   = headerStyle(wb);
            CellStyle csObra     = obraStyle(wb);
            CellStyle csNormal   = normalStyle(wb);
            CellStyle csNumber   = numberStyle(wb);
            CellStyle csWeekend  = weekendStyle(wb);
            CellStyle csSubtotal = subtotalStyle(wb);
            CellStyle csSubNum   = subtotalNumberStyle(wb);
            CellStyle csBaja     = bajaStyle(wb);
            CellStyle csVac      = vacStyle(wb);
            CellStyle csPat      = paternidadStyle(wb);
            CellStyle csTotalNum = totalNumStyle(wb);

            int FIXED_COLS = 4; // Código, Operario, Categoría, Obra

            // ── Cabecera fila 1: letras del día ───────────────────────────────
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(18);
            String[] fixedHeaders = {"Código", "Operario", "Categoría", "Obra"};
            for (int c = 0; c < fixedHeaders.length; c++) {
                Cell cell = hRow.createCell(c);
                cell.setCellValue(fixedHeaders[c]);
                cell.setCellStyle(csHeader);
                sheet.addMergedRegion(new CellRangeAddress(0, 1, c, c));
            }
            for (int i = 0; i < diasRango.size(); i++) {
                LocalDate dia = diasRango.get(i);
                Cell cell = hRow.createCell(FIXED_COLS + i);
                cell.setCellValue(diaSemanaLetra(dia));
                // Rojo si fin de semana O festivo
                cell.setCellStyle(isDiaRojo(dia) ? weekendHeaderStyle(wb) : csHeader);
            }
            Cell cTotalH = hRow.createCell(FIXED_COLS + diasRango.size());
            cTotalH.setCellValue("TOTAL");
            cTotalH.setCellStyle(csHeader);
            sheet.addMergedRegion(new CellRangeAddress(0, 1,
                    FIXED_COLS + diasRango.size(), FIXED_COLS + diasRango.size()));

            // ── Cabecera fila 2: número/mes ───────────────────────────────────
            Row hRow2 = sheet.createRow(1);
            hRow2.setHeightInPoints(14);
            for (int c = 0; c < fixedHeaders.length; c++) {
                hRow2.createCell(c).setCellStyle(csHeader);
            }
            for (int i = 0; i < diasRango.size(); i++) {
                LocalDate dia = diasRango.get(i);
                Cell cell = hRow2.createCell(FIXED_COLS + i);
                cell.setCellValue(dia.getDayOfMonth() + "/" + dia.getMonthValue());
                cell.setCellStyle(isDiaRojo(dia) ? weekendHeaderStyle(wb) : csHeader);
            }
            hRow2.createCell(FIXED_COLS + diasRango.size()).setCellStyle(csHeader);

            // ── Agrupar por obra ──────────────────────────────────────────────
            Map<String, List<Map<String, Object>>> porObra = new LinkedHashMap<>();
            for (Map<String, Object> fila : filas) {
                String obra = fila.get("obra") != null ? fila.get("obra").toString() : "Sin Obra";
                porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(fila);
            }

            int rowIdx = 2;

            for (Map.Entry<String, List<Map<String, Object>>> entry : porObra.entrySet()) {
                String obra = entry.getKey();
                List<Map<String, Object>> operarios = new ArrayList<>(entry.getValue());
                operarios.sort(Comparator.comparing(
                        f -> f.getOrDefault("operario", "").toString().toLowerCase()));

                // ── Fila cabecera de obra ─────────────────────────────────────
                Row obraRow = sheet.createRow(rowIdx++);
                obraRow.setHeightInPoints(18);
                Cell obraCell = obraRow.createCell(0);
                obraCell.setCellValue(obra);
                obraCell.setCellStyle(csObra);
                int lastCol = FIXED_COLS + diasRango.size();
                sheet.addMergedRegion(new CellRangeAddress(
                        obraRow.getRowNum(), obraRow.getRowNum(), 0, lastCol));
                for (int c = 1; c <= lastCol; c++) {
                    obraRow.createCell(c).setCellStyle(csObra);
                }

                // ── Filas operarios ───────────────────────────────────────────
                double totalObra = 0;

                for (Map<String, Object> fila : operarios) {
                    @SuppressWarnings("unchecked")
                    Map<LocalDate, Double> horasDias =
                            (Map<LocalDate, Double>) fila.getOrDefault("horas_por_dia", Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, String> ausencias =
                            (Map<String, String>) fila.getOrDefault("ausencias_por_dia", Map.of());

                    double totalPersona = fila.get("total_horas") instanceof Number
                            ? ((Number) fila.get("total_horas")).doubleValue() : 0.0;
                    totalObra += totalPersona;

                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);

                    setCell(row, 0, safe(fila.get("codigo")),                          csNormal);
                    setCell(row, 1, safe(fila.get("operario")),                        csNormal);
                    setCell(row, 2, safe(fila.getOrDefault("categoria_profesional", "-")), csNormal);
                    setCell(row, 3, obra,                                               csNormal);

                    for (int i = 0; i < diasRango.size(); i++) {
                        LocalDate dia = diasRango.get(i);
                        String iso = dia.toString();
                        String ausencia = ausencias.get(iso);
                        Cell cell = row.createCell(FIXED_COLS + i);

                        // Estilo base del día: rojo si fin de semana O festivo
                        CellStyle baseStyle = isDiaRojo(dia) ? csWeekend : csNormal;

                        if ("BAJA".equals(ausencia)) {
                            cell.setCellValue("B");
                            cell.setCellStyle(csBaja);
                        } else if ("VACACIONES".equals(ausencia)) {
                            cell.setCellValue("V");
                            cell.setCellStyle(csVac);
                        } else if ("PATERNIDAD".equals(ausencia)) {
                            cell.setCellValue("P");
                            cell.setCellStyle(csPat);
                        } else {
                            double h = horasDias.getOrDefault(dia, 0.0);
                            if (h > 0) {
                                cell.setCellValue(h);
                                cell.setCellStyle(csNumber);
                            } else {
                                cell.setCellStyle(baseStyle);
                            }
                        }
                    }

                    Cell cTotal = row.createCell(FIXED_COLS + diasRango.size());
                    cTotal.setCellValue(totalPersona);
                    cTotal.setCellStyle(csTotalNum);
                }

                // ── Fila subtotal obra ────────────────────────────────────────
                Row subRow = sheet.createRow(rowIdx++);
                subRow.setHeightInPoints(17);

                int totalCol = FIXED_COLS + diasRango.size();
                Cell subLabel = subRow.createCell(0);
                subLabel.setCellValue("Total " + obra);
                subLabel.setCellStyle(csSubtotal);
                for (int c = 1; c <= totalCol; c++) {
                    subRow.createCell(c).setCellStyle(csSubtotal);
                }
                sheet.addMergedRegion(new CellRangeAddress(
                        subRow.getRowNum(), subRow.getRowNum(), 0, totalCol - 1));

                Cell subTotal = subRow.createCell(totalCol);
                subTotal.setCellValue(totalObra);
                subTotal.setCellStyle(csSubNum);

                sheet.createRow(rowIdx++); // fila vacía entre obras
            }

            // ── Anchos de columna ─────────────────────────────────────────────
            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 6000);
            for (int i = 0; i < diasRango.size(); i++) {
                sheet.setColumnWidth(FIXED_COLS + i, 2200);
            }
            sheet.setColumnWidth(FIXED_COLS + diasRango.size(), 3500);

            sheet.createFreezePane(FIXED_COLS, 2);

            wb.write(out);
            return buildResponse(out.toByteArray(),
                    String.format("contabilidad_detalle_%s_al_%s.xlsx", desde, hasta));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ESTILOS
    // ─────────────────────────────────────────────────────────────────────────

    private CellStyle headerStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_HEADER_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle weekendHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes("7F1D1D"), null)); // rojo oscuro
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle obraStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_OBRA_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        return cs;
    }

    private CellStyle normalStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle numberStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = (XSSFCellStyle) normalStyle(wb);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        return cs;
    }

    private CellStyle totalStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_TOTAL_TEXT), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        applyBorder(cs);
        return cs;
    }

    private CellStyle subtotalStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_SUBTOTAL_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_SUBTOTAL_TEXT), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle subtotalNumberStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_SUBTOTAL_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_SUBTOTAL_TEXT), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        applyBorder(cs);
        return cs;
    }

    private CellStyle weekendStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_WEEKEND_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle bajaStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_BAJA_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle vacStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_VAC_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle paternidadStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_PAT_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle totalNumStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_TOTAL_TEXT), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        applyBorder(cs);
        return cs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void applyBorder(XSSFCellStyle cs) {
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String safe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String diaSemanaLetra(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY    -> "L";
            case TUESDAY   -> "M";
            case WEDNESDAY -> "X";
            case THURSDAY  -> "J";
            case FRIDAY    -> "V";
            case SATURDAY  -> "S";
            case SUNDAY    -> "D";
        };
    }

    /** Días que se marcan en rojo: fines de semana Y festivos nacionales fijos */
    private boolean isDiaRojo(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> FESTIVOS_FIJOS.contains(MonthDay.from(date));
        };
    }

    private byte[] hexToBytes(String hex) {
        return new byte[]{
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private ResponseEntity<byte[]> buildResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}