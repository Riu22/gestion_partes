/* Servicio que genera archivos Excel (.xlsx) para exportar datos de partes de trabajo y contabilidad.
   Crea dos tipos de informes: quincena (resumen por obra y operario) y detalle (día a día por operario).
   Usa la librería Apache POI para construir los archivos Excel con estilos, colores y formato profesional. */
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

    /* Festivos fijos del año (días que no se trabaja aunque no caigan en fin de semana).
       Se usan para marcar celdas en rojo. */
    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1), MonthDay.of(1,  6), MonthDay.of(5,  1),
            MonthDay.of(8, 15), MonthDay.of(10,12), MonthDay.of(11, 1),
            MonthDay.of(12, 6), MonthDay.of(12, 8), MonthDay.of(12,25)
    );

    /* Orden de las obras en el Excel: las que empiezan con "font" van al final, el resto por orden alfabético. */
    private static final Comparator<String> OBRA_ORDER = Comparator
            .comparingInt((String n) -> n.toLowerCase().startsWith("font") ? 1 : 0)
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    /* Contenedor de todos los estilos del workbook.
       Se crea UNA sola vez por workbook y se pasa donde hace falta.
       POI permite máx ~4000 estilos; con este patrón usamos exactamente 15. */
    private static final class Estilos {
        final CellStyle header, headerWe, quincena;
        final CellStyle obra, normal, number;
        final CellStyle subtotal, subtotalNum;
        final CellStyle weekend;
        final CellStyle baja, vac, pat;
        final CellStyle totalNum;
        final CellStyle granTotal, granTotalNum;
        final CellStyle total;

        /* Constructor: recibe el workbook y crea todos los estilos (15 en total) llamando a los métodos internos. */
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

        /* Crea un estilo de cabecera: fondo de color, texto en negrita, centrado, con borde. */
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

        /* Crea un estilo normal: recibe fondo, color de texto, negrita, centrado y formato numérico opcional. */
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

        /* Crea un estilo para ausencias (baja/vacaciones/paternidad): solo fondo de color, texto centrado y negrita. */
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

        /* Aplica un borde fino a los cuatro lados de una celda. */
        private static void applyBorder(XSSFCellStyle cs) {
            cs.setBorderBottom(BorderStyle.THIN); cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);  cs.setBorderRight(BorderStyle.THIN);
        }
    }

    /* Genera un Excel de resumen quincenal: recibe una lista de datos quincena_dto y el rango de fechas.
       Devuelve el archivo .xlsx como respuesta HTTP para descargar.
       Agrupa los datos por obra, muestra código, apellidos, nombre, horas del operario y total de la obra. */
    public ResponseEntity<byte[]> buildQuincenaXlsx(
            List<quincena_dto> datos, LocalDate desde, LocalDate hasta) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Estilos st = new Estilos(wb);
            XSSFSheet sheet = wb.createSheet("Quincena");
            sheet.setDefaultColumnWidth(18);

            String[] cols = {"Obra","Código","Apellidos","Nombre","Horas Operario","Total Obra"};
            Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(20);
            for (int c = 0; c < cols.length; c++) createCell(hRow, c, cols[c], st.header);

            /* Agrupa los datos por nombre de obra usando un TreeMap que ordena alfabéticamente. */
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
                /* Calcula la suma total de horas de todos los operarios de esta obra. */
                double totalObra = operarios.stream()
                        .mapToDouble(o -> o.getTotal_horas() != null ? o.getTotal_horas() : 0.0)
                        .sum();

                for (int i = 0; i < operarios.size(); i++) {
                    quincena_dto linea = operarios.get(i);
                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(16);

                    Cell cObra = row.createCell(0);
                    /* Solo la primera fila del grupo muestra el nombre de la obra (merged visual). */
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
                rowIdx++; /* fila vacía separadora entre obras */
            }

            for (int c = 0; c < 4; c++) sheet.autoSizeColumn(c);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4000);

            wb.write(out);
            return buildResponse(out.toByteArray(), "quincena_" + desde + "_" + hasta + ".xlsx");
        }
    }

    /* Genera un Excel de detalle día a día: recibe una lista de mapas con los datos de cada operario y el rango de fechas.
       Muestra una columna por cada día del rango con las horas trabajadas o las ausencias (B/V/P).
       Agrupa por obra, calcula subtotales y gran total. */
    public ResponseEntity<byte[]> buildDetalleXlsx(
            List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws Exception {

        /* Precalcula el rango de días una sola vez para evitar recalcularlo en cada fila. */
        List<LocalDate> diasRango = desde.datesUntil(hasta.plusDays(1))
                .collect(Collectors.toList());
        int nDias = diasRango.size();

        /* Precalcula si cada día es festivo/fin de semana y las etiquetas (letra del día y dd/MM). */
        boolean[] esRojo = new boolean[nDias];
        String[]  letraDia = new String[nDias];
        String[]  labelDia = new String[nDias];
        for (int i = 0; i < nDias; i++) {
            LocalDate d = diasRango.get(i);
            esRojo[i]   = isDiaRojo(d);
            letraDia[i] = diaSemanaLetra(d);
            labelDia[i] = d.getDayOfMonth() + "/" + d.getMonthValue();
        }

        /* Conjunto de fechas del rango para filtrar filas vacías (operarios sin horas ni ausencias en el rango). */
        Set<String> fechasRangoStr = new HashSet<>(nDias * 2);
        for (LocalDate d : diasRango) fechasRangoStr.add(d.toString());

        String mesNombre = desde.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        mesNombre = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1);
        /* Etiqueta de la quincena: "1ª Quincena - Marzo" o "2ª Quincena - Marzo". */
        final String quincenaLabel =
                (desde.getDayOfMonth() <= 15 ? "1ª Quincena" : "2ª Quincena") + " - " + mesNombre;

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(512 * 1024)) {

            Estilos st = new Estilos(wb);

            XSSFSheet sheet = wb.createSheet("Detalle");
            sheet.setDefaultColumnWidth(6);
            sheet.createFreezePane(0, 1); /* congela la fila de cabecera */

            final int FIXED_COLS   = 4;
            final int totalCol     = FIXED_COLS + nDias;
            final int obraTotalCol = totalCol + 1;

            /* Agrupa las filas por obra usando un TreeMap que ordena alfabéticamente. */
            Map<String, List<Map<String, Object>>> porObra = new TreeMap<>(OBRA_ORDER);
            for (Map<String, Object> fila : filas) {
                String obra = fila.get("obra") != null ? fila.get("obra").toString() : "Sin Obra";
                porObra.computeIfAbsent(obra, k -> new ArrayList<>()).add(fila);
            }

            /* Elimina las filas que no tienen horas ni ausencias en el rango de fechas. */
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
                /* Ordena los operarios alfabéticamente dentro de cada obra. */
                operarios.sort(Comparator.comparing(
                        f -> f.getOrDefault("operario", "").toString().toLowerCase()));

                /* Fila 1 de cabecera: letra del día (L M X J V S D). */
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

                /* Fila 2 de cabecera: dd/MM y nombre de la obra. */
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

                double totalObra = 0;

                /* Itera sobre cada operario de la obra para escribir sus horas día a día. */
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

                    /* Para cada día del rango, escribe la hora trabajada, la ausencia (B/V/P) o deja en blanco. */
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

                /* Fila de subtotal para la obra: suma de todas las horas de todos los operarios. */
                Row subRow = sheet.createRow(rowIdx++);
                subRow.setHeightInPoints(17);
                Cell subLabel = subRow.createCell(0);
                subLabel.setCellValue("Total " + obra);
                subLabel.setCellStyle(st.subtotal);
                for (int c = 1; c <= totalCol; c++) subRow.createCell(c).setCellStyle(st.subtotal);
                Cell subTotal = subRow.createCell(obraTotalCol);
                subTotal.setCellValue(totalObra);
                subTotal.setCellStyle(st.subtotalNum);

                rowIdx++; /* fila vacía separadora entre obras */
            }

            /* Fila de gran total: suma de todas las horas de todas las obras y personas. */
            Row gtRow = sheet.createRow(rowIdx);
            gtRow.setHeightInPoints(20);
            Cell gtLabel = gtRow.createCell(0);
            gtLabel.setCellValue("TOTAL HORAS"); gtLabel.setCellStyle(st.granTotal);
            for (int c = 1; c < totalCol; c++) gtRow.createCell(c).setCellStyle(st.granTotal);
            Cell gtP = gtRow.createCell(totalCol);
            gtP.setCellValue(granTotalPersonas); gtP.setCellStyle(st.granTotalNum);
            Cell gtO = gtRow.createCell(obraTotalCol);
            gtO.setCellValue(granTotalObras);    gtO.setCellStyle(st.granTotalNum);

            /* Anchos de columna fijos (no se usa autoSizeColumn para evitar problemas de rendimiento). */
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

    /* Crea una celda con valor texto y estilo en una fila dada. */
    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /* Convierte un objeto a String de forma segura (si es null devuelve cadena vacía). */
    private static String safe(Object v) { return v != null ? v.toString() : ""; }

    /* Devuelve la letra del día de la semana (L/M/X/J/V/S/D). */
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

    /* Comprueba si una fecha es "día rojo" (fin de semana o festivo fijo). */
    private static boolean isDiaRojo(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> FESTIVOS_FIJOS.contains(MonthDay.from(d));
        };
    }

    /* Convierte un color hexadecimal (6 dígitos) a un array de 3 bytes (RGB). */
    private static byte[] hex(String h) {
        return new byte[]{
                (byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    /* Construye la respuesta HTTP con el archivo Excel: establece el tipo de contenido, nombre de archivo y tamaño. */
    private static ResponseEntity<byte[]> buildResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
