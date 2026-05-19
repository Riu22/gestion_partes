package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.quincena_dto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.time.MonthDay;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class csv_export_service {

    // ─────────────────────────────────────────────────────────────────────────
    //  COLORES
    // ─────────────────────────────────────────────────────────────────────────
    private static final String COLOR_OBRA_BG        = "1E3A8A";
    private static final String COLOR_SUBTOTAL_BG    = "DBEAFE";
    private static final String COLOR_WEEKEND_BG     = "FF0000";
    private static final String COLOR_HEADER_BG      = "1E3A8A";
    private static final String COLOR_BAJA_BG        = "84DCAE";
    private static final String COLOR_VAC_BG         = "EF75DE";
    private static final String COLOR_PAT_BG         = "A2D2E8";
    private static final String COLOR_SUBTOTAL_TEXT  = "1D4ED8";
    private static final String COLOR_WHITE          = "FFFFFF";
    private static final String COLOR_TOTAL_TEXT     = "1D4ED8";
    private static final String COLOR_GRAN_TOTAL_BG  = "1E3A8A";

    // ─────────────────────────────────────────────────────────────────────────
    //  FESTIVOS NACIONALES FIJOS
    // ─────────────────────────────────────────────────────────────────────────
    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1),  MonthDay.of(1,  6),  MonthDay.of(5,  1),
            MonthDay.of(8,  15), MonthDay.of(10, 12), MonthDay.of(11, 1),
            MonthDay.of(12, 6),  MonthDay.of(12, 8),  MonthDay.of(12, 25)
    );

    // ─────────────────────────────────────────────────────────────────────────
    //  QUINCENA
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildQuincenaXlsx(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Quincena");
            sheet.setDefaultColumnWidth(18);

            CellStyle csHeader = headerStyle(wb);
            CellStyle csObra   = obraStyle(wb);
            CellStyle csNormal = normalStyle(wb);
            CellStyle csNumber = numberStyle(wb);
            CellStyle csTotal  = totalStyle(wb);

            String[] cols = {"Obra","Código","Apellidos","Nombre","Horas Operario","Total Obra"};
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(20);
            for (int c = 0; c < cols.length; c++) {
                Cell cell = hRow.createCell(c);
                cell.setCellValue(cols[c]);
                cell.setCellStyle(csHeader);
            }

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
                    if (i == 0) { cObra.setCellValue(obra); cObra.setCellStyle(csObra); }
                    else        { cObra.setCellStyle(csNormal); }

                    setCell(row, 1, safe(linea.getCodigo()),    csNormal);
                    setCell(row, 2, safe(linea.getApellidos()), csNormal);
                    setCell(row, 3, safe(linea.getNombre()),    csNormal);

                    Cell cHoras = row.createCell(4);
                    cHoras.setCellValue(linea.getTotal_horas() != null ? linea.getTotal_horas() : 0.0);
                    cHoras.setCellStyle(csNumber);

                    Cell cTotal = row.createCell(5);
                    if (i == 0) { cTotal.setCellValue(totalObra); cTotal.setCellStyle(csTotal); }
                    else        { cTotal.setCellStyle(csNormal); }
                }
                sheet.createRow(rowIdx++);
            }

            for (int c = 0; c < 4; c++) sheet.autoSizeColumn(c);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4000);

            wb.write(out);
            return buildResponse(out.toByteArray(), "quincena_" + desde + "_" + hasta + ".xlsx");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DETALLE (con días)
    //
    //  Estructura por obra:
    //    fila A  → cabecera letra día  (L/M/X…)        repetida para cada obra
    //    fila B  → cabecera dd/MM                       repetida para cada obra
    //    fila C  → nombre de obra (fondo azul oscuro)
    //    filas   → operarios: cols fijas + días + total persona (totalCol)
    //    fila S  → "Total [obra]" + número en obraTotalCol (totalCol + 1)
    //
    //  Al final:
    //    fila G → TOTAL HORAS: suma operarios en totalCol, suma obras en obraTotalCol
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildDetalleXlsx(
            List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws Exception {

        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());

        // Calcular quincenaLabel una sola vez (igual para todas las obras)
        String mesNombre = desde.getMonth()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        mesNombre = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1);
        final String quincenaLabel = (desde.getDayOfMonth() <= 15 ? "1ª Quincena" : "2ª Quincena")
                + " - " + mesNombre;

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Detalle");
            sheet.setDefaultColumnWidth(6);

            CellStyle csHeader       = headerStyle(wb);
            CellStyle csHeaderWe     = weekendHeaderStyle(wb);
            CellStyle csObra         = obraStyle(wb);
            CellStyle csNormal       = normalStyle(wb);
            CellStyle csNumber       = numberStyle(wb);
            CellStyle csWeekend      = weekendStyle(wb);
            CellStyle csSubtotal     = subtotalStyle(wb);
            CellStyle csSubNum       = subtotalNumberStyle(wb);
            CellStyle csBaja         = bajaStyle(wb);
            CellStyle csVac          = vacStyle(wb);
            CellStyle csPat          = paternidadStyle(wb);
            CellStyle csTotalNum     = totalNumStyle(wb);
            CellStyle csGranTotal    = granTotalStyle(wb);
            CellStyle csGranTotalNum = granTotalNumStyle(wb);
            CellStyle csQuincena     = quincenaHeaderStyle(wb);

            final int FIXED_COLS   = 4;
            final int totalCol     = FIXED_COLS + diasRango.size();  // total persona / quincena
            final int obraTotalCol = totalCol + 1;                   // total obra

            Map<String, List<Map<String, Object>>> porObra = new LinkedHashMap<>();
            for (Map<String, Object> fila : filas) {
                String obra = fila.get("obra") != null ? fila.get("obra").toString() : "Sin Obra";
                porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(fila);
            }

            int    rowIdx            = 0;
            double granTotalPersonas = 0;
            double granTotalObras    = 0;

            for (Map.Entry<String, List<Map<String, Object>>> entry : porObra.entrySet()) {
                String obra = entry.getKey();
                List<Map<String, Object>> operarios = new ArrayList<>(entry.getValue());
                operarios.sort(Comparator.comparing(
                        f -> f.getOrDefault("operario", "").toString().toLowerCase()));

                // ── Cabecera fila 1: letra día ────────────────────────────────
                Row hRow = sheet.createRow(rowIdx++);
                hRow.setHeightInPoints(18);
                String[] fixedHeaders = {"Código", "Operario", "Categoría", "Obra"};
                for (int c = 0; c < fixedHeaders.length; c++) {
                    Cell cell = hRow.createCell(c);
                    cell.setCellValue(fixedHeaders[c]);
                    cell.setCellStyle(csHeader);
                }
                for (int i = 0; i < diasRango.size(); i++) {
                    LocalDate dia = diasRango.get(i);
                    Cell cell = hRow.createCell(FIXED_COLS + i);
                    cell.setCellValue(diaSemanaLetra(dia));
                    cell.setCellStyle(isDiaRojo(dia) ? csHeaderWe : csHeader);
                }
                // Columna totalCol: mostrar quincenaLabel en lugar de "TOTAL"
                Cell hQuincena = hRow.createCell(totalCol);
                hQuincena.setCellValue(quincenaLabel);
                hQuincena.setCellStyle(csQuincena);
                hRow.createCell(obraTotalCol).setCellStyle(csHeader);

                // ── Cabecera fila 2: dd/MM ────────────────────────────────────
                Row hRow2 = sheet.createRow(rowIdx++);
                hRow2.setHeightInPoints(14);
                for (int c = 0; c < fixedHeaders.length; c++) {
                    hRow2.createCell(c).setCellStyle(csHeader);
                }
                for (int i = 0; i < diasRango.size(); i++) {
                    LocalDate dia = diasRango.get(i);
                    Cell cell = hRow2.createCell(FIXED_COLS + i);
                    cell.setCellValue(dia.getDayOfMonth() + "/" + dia.getMonthValue());
                    cell.setCellStyle(isDiaRojo(dia) ? csHeaderWe : csHeader);
                }
                // Segunda fila de cabecera: repetir quincenaLabel (o dejar vacía)
                hRow2.createCell(totalCol).setCellStyle(csQuincena);
                hRow2.createCell(obraTotalCol).setCellStyle(csHeader);

                // ── Filas operarios (el nombre de obra va en col 0 del primero) ──
                double totalObra = 0;

                for (int opIdx = 0; opIdx < operarios.size(); opIdx++) {
                    Map<String, Object> fila = operarios.get(opIdx);
                    @SuppressWarnings("unchecked")
                    Map<LocalDate, Double> horasDias =
                            (Map<LocalDate, Double>) fila.getOrDefault("horas_por_dia", Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, String> ausencias =
                            (Map<String, String>) fila.getOrDefault("ausencias_por_dia", Map.of());

                    double totalPersona = fila.get("total_horas") instanceof Number
                            ? ((Number) fila.get("total_horas")).doubleValue() : 0.0;
                    totalObra         += totalPersona;
                    granTotalPersonas += totalPersona;

                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);

                    // Primera fila del grupo: nombre de obra en col 0 con estilo csObra
                    if (opIdx == 0) {
                        Cell cObra = row.createCell(0);
                        cObra.setCellValue(obra);
                        cObra.setCellStyle(csObra);
                    } else {
                        setCell(row, 0, safe(fila.get("codigo")), csNormal);
                    }
                    setCell(row, 1, safe(fila.get("operario")),                            csNormal);
                    setCell(row, 2, safe(fila.getOrDefault("categoria_profesional", "-")), csNormal);
                    setCell(row, 3, obra,                                                   csNormal);

                    for (int i = 0; i < diasRango.size(); i++) {
                        LocalDate dia   = diasRango.get(i);
                        String iso      = dia.toString();
                        String ausencia = ausencias.get(iso);
                        Cell cell       = row.createCell(FIXED_COLS + i);
                        CellStyle base  = isDiaRojo(dia) ? csWeekend : csNormal;

                        if ("BAJA".equals(ausencia)) {
                            cell.setCellValue("B"); cell.setCellStyle(csBaja);
                        } else if ("VACACIONES".equals(ausencia)) {
                            cell.setCellValue("V"); cell.setCellStyle(csVac);
                        } else if ("PATERNIDAD".equals(ausencia)) {
                            cell.setCellValue("P"); cell.setCellStyle(csPat);
                        } else {
                            double h = horasDias.getOrDefault(dia, 0.0);
                            if (h > 0) { cell.setCellValue(h); cell.setCellStyle(csNumber); }
                            else       { cell.setCellStyle(base); }
                        }
                    }

                    Cell cTotalP = row.createCell(totalCol);
                    cTotalP.setCellValue(totalPersona);
                    cTotalP.setCellStyle(csTotalNum);
                    row.createCell(obraTotalCol).setCellStyle(csNormal); // vacía
                }

                granTotalObras += totalObra;

                // ── Fila subtotal obra ────────────────────────────────────────
                Row subRow = sheet.createRow(rowIdx++);
                subRow.setHeightInPoints(17);
                Cell subLabel = subRow.createCell(0);
                subLabel.setCellValue("Total " + obra);
                subLabel.setCellStyle(csSubtotal);
                for (int c = 1; c <= totalCol; c++) {
                    subRow.createCell(c).setCellStyle(csSubtotal);
                }
                Cell subTotal = subRow.createCell(obraTotalCol);
                subTotal.setCellValue(totalObra);
                subTotal.setCellStyle(csSubNum);

                // Sin fila vacía entre bloques de obra
            }

            // ── Gran total ────────────────────────────────────────────────────
            Row gtRow = sheet.createRow(rowIdx++);
            gtRow.setHeightInPoints(20);
            Cell gtLabel = gtRow.createCell(0);
            gtLabel.setCellValue("TOTAL HORAS");
            gtLabel.setCellStyle(csGranTotal);
            for (int c = 1; c < totalCol; c++) {
                gtRow.createCell(c).setCellStyle(csGranTotal);
            }
            Cell gtPNum = gtRow.createCell(totalCol);
            gtPNum.setCellValue(granTotalPersonas);
            gtPNum.setCellStyle(csGranTotalNum);
            Cell gtONum = gtRow.createCell(obraTotalCol);
            gtONum.setCellValue(granTotalObras);
            gtONum.setCellStyle(csGranTotalNum);

            // ── Anchos de columna ─────────────────────────────────────────────
            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 6000);
            for (int i = 0; i < diasRango.size(); i++) {
                sheet.setColumnWidth(FIXED_COLS + i, 2200);
            }
            sheet.setColumnWidth(totalCol,     5500); // más ancho para el texto de quincena
            sheet.setColumnWidth(obraTotalCol, 4500);

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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle weekendHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes("FF0000"), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    /** Estilo para la columna de quincena/mes en las cabeceras de días */
    private CellStyle quincenaHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_OBRA_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        cs.setWrapText(true);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        return cs;
    }

    private CellStyle normalStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
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
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        cs.setFont(font);
        cs.setAlignment(HorizontalAlignment.CENTER);
        cs.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        applyBorder(cs);
        return cs;
    }

    private CellStyle granTotalStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_GRAN_TOTAL_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 11);
        cs.setFont(font);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(cs);
        return cs;
    }

    private CellStyle granTotalNumStyle(XSSFWorkbook wb) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_GRAN_TOTAL_BG), null));
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(hexToBytes(COLOR_WHITE), null));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 11);
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