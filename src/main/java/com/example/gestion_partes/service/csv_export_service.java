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
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class csv_export_service {

    // ─────────────────────────────────────────────────────────────────────────
    //  COLORES
    // ─────────────────────────────────────────────────────────────────────────
    private static final byte[] C_WHITE        = hex("FFFFFF");
    private static final byte[] C_BLACK        = hex("000000");
    private static final byte[] C_SUBTOTAL_BG  = hex("DBEAFE");
    private static final byte[] C_SUBTOTAL_TXT = hex("1D4ED8");
    private static final byte[] C_WEEKEND      = hex("FF0000");
    private static final byte[] C_BAJA         = hex("84DCAE");
    private static final byte[] C_VAC          = hex("EF75DE");
    private static final byte[] C_PAT          = hex("A2D2E8");
    private static final byte[] C_TOTAL_TXT    = hex("1D4ED8");

    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1), MonthDay.of(1,  6), MonthDay.of(5,  1),
            MonthDay.of(8, 15), MonthDay.of(10,12), MonthDay.of(11, 1),
            MonthDay.of(12, 6), MonthDay.of(12, 8), MonthDay.of(12,25)
    );

    private static final Comparator<String> OBRA_ORDER = Comparator
            .comparingInt((String n) -> n.toLowerCase().startsWith("font") ? 1 : 0)
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    // ─────────────────────────────────────────────────────────────────────────
    //  Contenedor de todos los estilos del workbook.
    //  Se crea UNA sola vez por workbook y se pasa donde hace falta.
    //  POI permite máx ~4000 estilos; con este patrón usamos exactamente 15.
    // ─────────────────────────────────────────────────────────────────────────
    private static final class Estilos {
        final CellStyle header, headerWe, quincena;
        final CellStyle obra, normal, number;
        final CellStyle subtotal, subtotalNum;
        final CellStyle weekend;
        final CellStyle baja, vac, pat;
        final CellStyle totalNum;
        final CellStyle granTotal, granTotalNum;
        // Quincena (resumen)
        final CellStyle total;

        Estilos(XSSFWorkbook wb) {
            XSSFDataFormat fmt = wb.createDataFormat();
            short fmtDec = fmt.getFormat("0.0");

            header       = mkHeader(wb, C_WHITE,       C_BLACK,        false, false);
            headerWe     = mkHeader(wb, C_WEEKEND,     C_WHITE,        false, false);
            quincena     = mkHeader(wb, C_WHITE,       C_BLACK,        false, true);
            obra         = mkHeader(wb, C_WHITE,       C_BLACK,        false, false);
            normal       = mkNormal(wb, null,          C_BLACK,        false, false, (short)-1);
            number       = mkNormal(wb, null,          C_BLACK,        false, true,  fmtDec);
            subtotal     = mkNormal(wb, C_SUBTOTAL_BG, C_SUBTOTAL_TXT, true,  false, (short)-1);
            subtotalNum  = mkNormal(wb, C_SUBTOTAL_BG, C_SUBTOTAL_TXT, true,  true,  fmtDec);
            weekend      = mkNormal(wb, C_WEEKEND,     C_WHITE,        false, true,  (short)-1);
            baja         = mkAus(wb, C_BAJA);
            vac          = mkAus(wb, C_VAC);
            pat          = mkAus(wb, C_PAT);
            totalNum     = mkNormal(wb, null,          C_TOTAL_TXT,    true,  true,  fmtDec);
            granTotal    = mkNormal(wb, null,          C_BLACK,        true,  false, (short)-1);
            granTotalNum = mkNormal(wb, null,          C_BLACK,        true,  true,  fmtDec);
            total        = mkNormal(wb, null,          C_TOTAL_TXT,    true,  true,  fmtDec);
        }

        // ── constructores internos ────────────────────────────────────────────

        private static XSSFCellStyle mkHeader(XSSFWorkbook wb, byte[] bg, byte[] fg,
                                              boolean bold, boolean wrap) {
            XSSFCellStyle cs = wb.createCellStyle();
            if (bg != null) { cs.setFillForegroundColor(new XSSFColor(bg, null)); cs.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            XSSFFont font = wb.createFont();
            font.setBold(true);
            font.setColor(new XSSFColor(fg, null));
            font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
            cs.setFont(font);
            cs.setAlignment(HorizontalAlignment.CENTER);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            cs.setWrapText(wrap);
            applyBorder(cs);
            return cs;
        }

        private static XSSFCellStyle mkNormal(XSSFWorkbook wb, byte[] bg, byte[] fg,
                                              boolean bold, boolean centered, short fmt) {
            XSSFCellStyle cs = wb.createCellStyle();
            if (bg != null) { cs.setFillForegroundColor(new XSSFColor(bg, null)); cs.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            XSSFFont font = wb.createFont();
            font.setBold(bold);
            font.setColor(new XSSFColor(fg, null));
            font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
            cs.setFont(font);
            if (centered) cs.setAlignment(HorizontalAlignment.CENTER);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fmt >= 0) cs.setDataFormat(fmt);
            applyBorder(cs);
            return cs;
        }

        private static XSSFCellStyle mkAus(XSSFWorkbook wb, byte[] bg) {
            XSSFCellStyle cs = wb.createCellStyle();
            cs.setFillForegroundColor(new XSSFColor(bg, null));
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = wb.createFont();
            font.setBold(true); font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
            cs.setFont(font);
            cs.setAlignment(HorizontalAlignment.CENTER);
            applyBorder(cs);
            return cs;
        }

        private static void applyBorder(XSSFCellStyle cs) {
            cs.setBorderBottom(BorderStyle.THIN); cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);  cs.setBorderRight(BorderStyle.THIN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  QUINCENA (Resumen)
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildQuincenaXlsx(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Estimamos capacidad para evitar resizes del ByteArrayOutputStream.
            // ~4KB fijos + ~200B por operario es una estimación conservadora.
            out.reset(); // no-op pero deja clara la intención

            Estilos st = new Estilos(wb);
            XSSFSheet sheet = wb.createSheet("Quincena");
            sheet.setDefaultColumnWidth(18);

            String[] cols = {"Obra","Código","Apellidos","Nombre","Horas Operario","Total Obra"};
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(20);
            for (int c = 0; c < cols.length; c++) createCell(hRow, c, cols[c], st.header);

            Map<String, List<quincena_dto>> porObra = new TreeMap<>(OBRA_ORDER);
            for (quincena_dto d : datos) {
                porObra.computeIfAbsent(
                        d.getObra() != null ? d.getObra() : "Sin Obra",
                        k -> new ArrayList<>()).add(d);
            }

            int rowIdx = 1;
            for (Map.Entry<String, List<quincena_dto>> entry : porObra.entrySet()) {
                String             obra      = entry.getKey();
                List<quincena_dto> operarios = entry.getValue();
                double totalObra = operarios.stream()
                        .mapToDouble(o -> o.getTotal_horas() != null ? o.getTotal_horas() : 0.0)
                        .sum();

                for (int i = 0; i < operarios.size(); i++) {
                    quincena_dto linea = operarios.get(i);
                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);

                    Cell cObra = row.createCell(0);
                    if (i == 0) { cObra.setCellValue(obra); cObra.setCellStyle(st.obra); }
                    else          cObra.setCellStyle(st.normal);

                    createCell(row, 1, safe(linea.getCodigo()),    st.normal);
                    createCell(row, 2, safe(linea.getApellidos()), st.normal);
                    createCell(row, 3, safe(linea.getNombre()),    st.normal);

                    Cell cHoras = row.createCell(4);
                    cHoras.setCellValue(linea.getTotal_horas() != null ? linea.getTotal_horas() : 0.0);
                    cHoras.setCellStyle(st.number);

                    Cell cTotal = row.createCell(5);
                    if (i == 0) { cTotal.setCellValue(totalObra); cTotal.setCellStyle(st.total); }
                    else          cTotal.setCellStyle(st.normal);
                }
                rowIdx++;
            }

            // autoSizeColumn solo para las 4 columnas de texto; es lento con muchas filas
            // pero en quincena el número de filas es acotado y el ancho variable lo justifica.
            for (int c = 0; c < 4; c++) sheet.autoSizeColumn(c);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4000);

            wb.write(out);
            return buildResponse(out.toByteArray(), "quincena_" + desde + "_" + hasta + ".xlsx");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DETALLE (Día a día)
    // ─────────────────────────────────────────────────────────────────────────
    public ResponseEntity<byte[]> buildDetalleXlsx(
            List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws Exception {

        // Precalcular el rango de días una sola vez
        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());
        int nDias = diasRango.size();

        // Precalcular flags de día rojo para no recalcular por cada celda de cada operario
        // Con 15 días y 10 usuarios × 70 obras ahorramos ~10 500 llamadas a isDiaRojo().
        boolean[] esRojo = new boolean[nDias];
        String[]  letraDia = new String[nDias];
        String[]  labelDia = new String[nDias];
        for (int i = 0; i < nDias; i++) {
            LocalDate d = diasRango.get(i);
            esRojo[i]   = isDiaRojo(d);
            letraDia[i] = diaSemanaLetra(d);
            labelDia[i] = d.getDayOfMonth() + "/" + d.getMonthValue();
        }

        // Set de fechas del rango como strings para el filtro de filas vacías
        Set<String> fechasRangoStr = new HashSet<>(nDias * 2);
        for (LocalDate d : diasRango) fechasRangoStr.add(d.toString());

        String mesNombre = desde.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        mesNombre = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1);
        final String quincenaLabel =
                (desde.getDayOfMonth() <= 15 ? "1ª Quincena" : "2ª Quincena") + " - " + mesNombre;

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(512 * 1024)) {

            Estilos st = new Estilos(wb);

            XSSFSheet sheet = wb.createSheet("Detalle");
            sheet.setDefaultColumnWidth(6);
            sheet.createFreezePane(0, 1);

            final int FIXED_COLS   = 4;
            final int totalCol     = FIXED_COLS + nDias;
            final int obraTotalCol = totalCol + 1;

            // ── Agrupar y filtrar ─────────────────────────────────────────────
            Map<String, List<Map<String, Object>>> porObra = new TreeMap<>(OBRA_ORDER);
            for (Map<String, Object> fila : filas) {
                String obra = fila.get("obra") != null ? fila.get("obra").toString() : "Sin Obra";
                porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(fila);
            }

            for (Iterator<Map.Entry<String, List<Map<String, Object>>>> it =
                 porObra.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, List<Map<String, Object>>> entry = it.next();
                entry.getValue().removeIf(fila -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> horas    = (Map<String, Object>) fila.getOrDefault("horas_por_dia",    Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, String> ausencias = (Map<String, String>) fila.getOrDefault("ausencias_por_dia", Map.of());
                    return horas.keySet().stream().noneMatch(fechasRangoStr::contains)
                            && ausencias.keySet().stream().noneMatch(fechasRangoStr::contains);
                });
                if (entry.getValue().isEmpty()) it.remove();
            }

            int rowIdx = 0;
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
                String[] fixedH = {"Código","Operario","Categoría","Obra"};
                for (int c = 0; c < fixedH.length; c++) createCell(hRow, c, fixedH[c], st.header);
                for (int i = 0; i < nDias; i++) {
                    Cell cell = hRow.createCell(FIXED_COLS + i);
                    cell.setCellValue(letraDia[i]);
                    cell.setCellStyle(esRojo[i] ? st.headerWe : st.header);
                }
                createCell(hRow, totalCol,     "Total Horas", st.header);
                createCell(hRow, obraTotalCol, quincenaLabel, st.quincena);

                // ── Cabecera fila 2: dd/MM ────────────────────────────────────
                Row hRow2 = sheet.createRow(rowIdx++);
                hRow2.setHeightInPoints(14);
                Cell cObra0 = hRow2.createCell(0);
                cObra0.setCellValue(obra); cObra0.setCellStyle(st.obra);
                for (int c = 1; c < fixedH.length; c++) hRow2.createCell(c).setCellStyle(st.header);
                for (int i = 0; i < nDias; i++) {
                    Cell cell = hRow2.createCell(FIXED_COLS + i);
                    cell.setCellValue(labelDia[i]);
                    cell.setCellStyle(esRojo[i] ? st.headerWe : st.header);
                }
                hRow2.createCell(totalCol).setCellStyle(st.header);
                hRow2.createCell(obraTotalCol).setCellStyle(st.quincena);

                // ── Filas operarios ───────────────────────────────────────────
                double totalObra = 0;

                for (Map<String, Object> fila : operarios) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> horasDias = (Map<String, Object>) fila.getOrDefault("horas_por_dia",     Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, String> ausencias  = (Map<String, String>) fila.getOrDefault("ausencias_por_dia", Map.of());

                    double totalPersona = fila.get("total_horas") instanceof Number
                            ? ((Number) fila.get("total_horas")).doubleValue() : 0.0;
                    totalObra         += totalPersona;
                    granTotalPersonas += totalPersona;

                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);
                    createCell(row, 0, safe(fila.get("codigo")),                         st.normal);
                    createCell(row, 1, safe(fila.get("operario")),                       st.normal);
                    createCell(row, 2, safe(fila.getOrDefault("categoria_profesional","-")), st.normal);
                    createCell(row, 3, obra,                                             st.normal);

                    for (int i = 0; i < nDias; i++) {
                        String fechaStr = diasRango.get(i).toString();
                        String ausencia = ausencias.get(fechaStr);
                        Cell   cell     = row.createCell(FIXED_COLS + i);

                        if ("BAJA".equals(ausencia)) {
                            cell.setCellValue("B"); cell.setCellStyle(st.baja);
                        } else if ("VACACIONES".equals(ausencia)) {
                            cell.setCellValue("V"); cell.setCellStyle(st.vac);
                        } else if ("PATERNIDAD".equals(ausencia)) {
                            cell.setCellValue("P"); cell.setCellStyle(st.pat);
                        } else {
                            double h = 0.0;
                            Object entrada = horasDias.get(fechaStr);
                            if (entrada instanceof Map<?,?> m) {
                                Object ho = m.get("horas");
                                if (ho instanceof Number n) h = n.doubleValue();
                            } else if (entrada instanceof Number n) {
                                h = n.doubleValue();
                            }
                            if (h > 0) { cell.setCellValue(h); cell.setCellStyle(st.number); }
                            else         cell.setCellStyle(esRojo[i] ? st.weekend : st.normal);
                        }
                    }

                    Cell cTotalP = row.createCell(totalCol);
                    cTotalP.setCellValue(totalPersona);
                    cTotalP.setCellStyle(st.totalNum);
                    row.createCell(obraTotalCol).setCellStyle(st.normal);
                }

                granTotalObras += totalObra;

                // ── Subtotal obra ─────────────────────────────────────────────
                Row subRow = sheet.createRow(rowIdx++);
                subRow.setHeightInPoints(17);
                Cell subLabel = subRow.createCell(0);
                subLabel.setCellValue("Total " + obra);
                subLabel.setCellStyle(st.subtotal);
                for (int c = 1; c <= totalCol; c++) subRow.createCell(c).setCellStyle(st.subtotal);
                Cell subTotal = subRow.createCell(obraTotalCol);
                subTotal.setCellValue(totalObra);
                subTotal.setCellStyle(st.subtotalNum);

                rowIdx++; // fila vacía separadora
            }

            // ── Gran total ────────────────────────────────────────────────────
            Row gtRow = sheet.createRow(rowIdx);
            gtRow.setHeightInPoints(20);
            Cell gtLabel = gtRow.createCell(0);
            gtLabel.setCellValue("TOTAL HORAS"); gtLabel.setCellStyle(st.granTotal);
            for (int c = 1; c < totalCol; c++) gtRow.createCell(c).setCellStyle(st.granTotal);
            Cell gtP = gtRow.createCell(totalCol);
            gtP.setCellValue(granTotalPersonas); gtP.setCellStyle(st.granTotalNum);
            Cell gtO = gtRow.createCell(obraTotalCol);
            gtO.setCellValue(granTotalObras);    gtO.setCellStyle(st.granTotalNum);

            // ── Anchos de columna ─────────────────────────────────────────────
            // NO se usa autoSizeColumn: es O(filas) por columna, muy lento.
            // Anchos fijos calibrados para el contenido habitual.
            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 6000);
            for (int i = 0; i < nDias; i++) sheet.setColumnWidth(FIXED_COLS + i, 2200);
            sheet.setColumnWidth(totalCol,     5500);
            sheet.setColumnWidth(obraTotalCol, 4500);

            wb.write(out);
            return buildResponse(out.toByteArray(),
                    "contabilidad_detalle_" + desde + "_al_" + hasta + ".xlsx");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String safe(Object v) { return v != null ? v.toString() : ""; }

    private static String diaSemanaLetra(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY    -> "L";
            case TUESDAY   -> "M";
            case WEDNESDAY -> "X";
            case THURSDAY  -> "J";
            case FRIDAY    -> "V";
            case SATURDAY  -> "S";
            case SUNDAY    -> "D";
        };
    }

    private static boolean isDiaRojo(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> FESTIVOS_FIJOS.contains(MonthDay.from(d));
        };
    }

    private static byte[] hex(String h) {
        return new byte[]{
                (byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    private static ResponseEntity<byte[]> buildResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}