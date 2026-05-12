package com.example.gestion_partes.service;

import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class pdf_service {

    @Autowired
    private partes_trabajo_repo partes_trabajo_repo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Colores ───────────────────────────────────────────────────────────────
    private static final Color COLOR_HEADER_ELEC  = new Color(21,  101, 192); // azul — electricidad
    private static final Color COLOR_HEADER_FONT  = new Color(13,  71,  161); // azul oscuro — fontanería
    private static final Color COLOR_HEADER_OP    = new Color(232, 240, 255);
    private static final Color COLOR_FILA_PAR     = new Color(248, 249, 252);
    private static final Color COLOR_TOTAL        = new Color(240, 244, 255);
    private static final Color COLOR_TEXTO_BLANCO = Color.WHITE;
    private static final Color COLOR_TEXTO_DARK   = new Color(26,  26,  46);
    private static final Color COLOR_BORDER       = new Color(200, 210, 230);

    // ─────────────────────────────────────────────────────────────────────────
    // ZIP: un PDF por cada combinación obra + especialidad
    //
    // Ejemplos de nombres de fichero dentro del ZIP:
    //   castanyetes_23_electricidad.pdf
    //   castanyetes_23_fontaneria.pdf
    //   castanyetes_26_electricidad.pdf
    //   obra_sin_especialidad.pdf   ← partes sin especialidad asignada
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarZipPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        // Agrupar: obraId + especialidad → partes
        // Clave: "obraId|ESPECIALIDAD"  (e.g. "30|ELECTRICIDAD", "30|FONTANERIA", "30|SIN")
        Map<String, List<partes_trabajo>> porObraEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name()
                            : "SIN";
                    String clave = p.getObra().getId() + "|" + esp;
                    porObraEsp.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
                });

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
            for (Map.Entry<String, List<partes_trabajo>> entry : porObraEsp.entrySet()) {
                List<partes_trabajo> partesGrupo = entry.getValue();
                partes_trabajo primero = partesGrupo.get(0);

                String nombreObra = primero.getObra().getNombre();
                String esp = primero.getEspecialidad() != null
                        ? primero.getEspecialidad().name()
                        : "SIN";

                // Nombre del fichero: obra_electricidad.pdf / obra_fontaneria.pdf
                String sufijo = switch (esp) {
                    case "ELECTRICIDAD" -> "electricidad";
                    case "FONTANERIA"   -> "fontaneria";
                    default             -> "sin_especialidad";
                };
                String nombreFichero = sanitizarNombre(nombreObra) + "_" + sufijo + ".pdf";

                byte[] pdfBytes = generarPdfGrupo(nombreObra, esp, partesGrupo, desde, hasta);
                zos.putNextEntry(new ZipEntry(nombreFichero));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }

        return zipBaos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF único — todas las obras en un solo archivo, separadas por especialidad
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarPdfPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        // Agrupar por obra, dentro por especialidad
        Map<Long, Map<String, List<partes_trabajo>>> porObraEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name()
                            : "SIN";
                    porObraEsp
                            .computeIfAbsent(p.getObra().getId(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(esp, k -> new ArrayList<>())
                            .add(p);
                });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        agregarPiePagina(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        for (Map.Entry<Long, Map<String, List<partes_trabajo>>> obraEntry : porObraEsp.entrySet()) {
            Map<String, List<partes_trabajo>> porEsp = obraEntry.getValue();
            String nombreObra = porEsp.values().iterator().next().get(0).getObra().getNombre();

            // Orden: ELECTRICIDAD → FONTANERIA → SIN
            List<String> ordenEsp = new ArrayList<>();
            if (porEsp.containsKey("ELECTRICIDAD")) ordenEsp.add("ELECTRICIDAD");
            if (porEsp.containsKey("FONTANERIA"))   ordenEsp.add("FONTANERIA");
            porEsp.keySet().stream()
                    .filter(k -> !k.equals("ELECTRICIDAD") && !k.equals("FONTANERIA"))
                    .forEach(ordenEsp::add);

            for (String esp : ordenEsp) {
                agregarTablaGrupo(doc, nombreObra, esp, porEsp.get(esp));
            }
        }

        if (porObraEsp.isEmpty()) agregarVacio(doc);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Genera el PDF de un grupo obra + especialidad (usado por el ZIP)
    // ─────────────────────────────────────────────────────────────────────────
    private byte[] generarPdfGrupo(
            String nombreObra,
            String especialidad,
            List<partes_trabajo> partes,
            LocalDate desde,
            LocalDate hasta) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        agregarPiePagina(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);
        agregarTablaGrupo(doc, nombreObra, especialidad, partes);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Genera la tabla de un grupo obra + especialidad
    // ─────────────────────────────────────────────────────────────────────────
    private void agregarTablaGrupo(
            Document doc,
            String nombreObra,
            String especialidad,
            List<partes_trabajo> partes) throws Exception {

        // Título: "Castanyetes 23  ·  Electricidad"  o  "Castanyetes 23  ·  Fontanería"
        String titulo = nombreObra;
        if (!especialidad.equals("SIN")) {
            titulo += "  ·  " + labelEspecialidad(especialidad);
        }

        // Agrupar por operario, ordenado por apellidos, cada operario fecha desc
        Map<UUID, List<partes_trabajo>> porOperario = new LinkedHashMap<>();
        partes.stream()
                .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                .forEach(p -> porOperario
                        .computeIfAbsent(p.getPerfil().getId(), k -> new ArrayList<>())
                        .add(p));
        porOperario.values().forEach(lista ->
                lista.sort(Comparator.comparing(partes_trabajo::getFecha).reversed()));

        PdfPTable tabla = crearTablaBase();
        agregarCeldaObra(tabla, titulo, especialidad, 4);

        for (List<partes_trabajo> partesOp : porOperario.values()) {
            agregarFilasOperario(tabla, partesOp);
        }

        doc.add(tabla);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de tabla
    // ─────────────────────────────────────────────────────────────────────────

    private PdfPTable crearTablaBase() throws Exception {
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2f, 1f, 5f, 0.1f});
        tabla.setSpacingBefore(12);
        tabla.setSpacingAfter(6);
        tabla.setKeepTogether(false);
        return tabla;
    }

    private void agregarFilasOperario(PdfPTable tabla, List<partes_trabajo> partesOp) {
        partes_trabajo primero = partesOp.get(0);
        String nombreOp = primero.getPerfil().getName()
                + " " + primero.getPerfil().getApellidos();

        agregarCeldaOperario(tabla, nombreOp, 4);

        double totalHoras = 0;
        boolean par = false;
        for (partes_trabajo p : partesOp) {
            Color bgFila = par ? COLOR_FILA_PAR : Color.WHITE;
            par = !par;
            agregarCeldaDato(tabla, FMT.format(p.getFecha()), bgFila, Element.ALIGN_CENTER);
            agregarCeldaDato(tabla, formatHoras(p.getHoras_normales()), bgFila, Element.ALIGN_CENTER);
            String desc = (p.getDescripcion() != null && !p.getDescripcion().isBlank())
                    ? p.getDescripcion() : "Sin descripción";
            agregarCeldaDato(tabla, desc, bgFila, Element.ALIGN_LEFT);
            agregarCeldaDato(tabla, "", bgFila, Element.ALIGN_LEFT);
            totalHoras += p.getHoras_normales();
        }
        agregarCeldaTotal(tabla, totalHoras, 4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de documento
    // ─────────────────────────────────────────────────────────────────────────

    private Document construirDocumento(ByteArrayOutputStream baos) {
        return new Document(PageSize.A4, 36, 36, 50, 40);
    }

    private void agregarPiePagina(PdfWriter writer) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Phrase footer = new Phrase(
                            "Página " + w.getPageNumber(),
                            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, footer,
                            d.right(), d.bottom() - 10, 0);
                } catch (Exception ignored) {}
            }
        });
    }

    private void agregarCabeceraDocumento(Document doc, LocalDate desde, LocalDate hasta)
            throws Exception {
        Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_TEXTO_DARK);
        Font fSub    = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

        Paragraph titulo = new Paragraph("Informe de Partes de Trabajo", fTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        Paragraph rango = new Paragraph(
                "Período: " + FMT.format(desde) + "  →  " + FMT.format(hasta), fSub);
        rango.setAlignment(Element.ALIGN_CENTER);
        rango.setSpacingAfter(16);
        doc.add(rango);
    }

    private void agregarVacio(Document doc) throws Exception {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.GRAY);
        Paragraph p = new Paragraph("No hay partes para los filtros seleccionados.", f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(40);
        doc.add(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de celdas
    // ─────────────────────────────────────────────────────────────────────────

    private void agregarCeldaObra(PdfPTable tabla, String texto, String especialidad, int colspan) {
        Color bg = especialidad.equals("FONTANERIA") ? COLOR_HEADER_FONT : COLOR_HEADER_ELEC;
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_TEXTO_BLANCO);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setColspan(colspan);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderWidth(0);
        tabla.addCell(cell);
    }

    private void agregarCeldaOperario(PdfPTable tabla, String texto, int colspan) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_TEXTO_DARK);
        PdfPCell cell = new PdfPCell(new Phrase("  " + texto, f));
        cell.setColspan(colspan);
        cell.setBackgroundColor(COLOR_HEADER_OP);
        cell.setPadding(6);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidthTop(1);
        cell.setBorderWidthBottom(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        tabla.addCell(cell);
    }

    private void agregarCeldaDato(PdfPTable tabla, String texto, Color bg, int align) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXTO_DARK);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setBackgroundColor(bg);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(4);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(0.3f);
        tabla.addCell(cell);
    }

    private void agregarCeldaTotal(PdfPTable tabla, double totalHoras, int colspan) {
        String texto = "Total: " + formatHoras(totalHoras);
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_TEXTO_DARK);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setColspan(colspan);
        cell.setBackgroundColor(COLOR_TOTAL);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidthTop(0.5f);
        cell.setBorderWidthBottom(1f);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        tabla.addCell(cell);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private List<partes_trabajo> filtrar(
            List<Long> obraIds,
            List<String> perfilIds,
            LocalDate desde,
            LocalDate hasta) {
        return partes_trabajo_repo.findAll().stream()
                .filter(p -> !p.getFecha().isBefore(desde) && !p.getFecha().isAfter(hasta))
                .filter(p -> obraIds == null || obraIds.isEmpty()
                        || obraIds.contains(p.getObra().getId()))
                .filter(p -> perfilIds == null || perfilIds.isEmpty()
                        || perfilIds.contains(p.getPerfil().getId().toString()))
                .collect(Collectors.toList());
    }

    private String labelEspecialidad(String esp) {
        return switch (esp) {
            case "ELECTRICIDAD" -> "Electricidad";
            case "FONTANERIA"   -> "Fontaneria";
            default             -> esp;
        };
    }

    private String formatHoras(double h) {
        return h % 1 == 0 ? (int) h + "h" : h + "h";
    }

    private String sanitizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "_").trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
// ZIP por operario: un PDF por operario con todas sus obras
// Nombre fichero: juan_garcia_lopez_electricidad.pdf
//                 juan_garcia_lopez_fontaneria.pdf
// ─────────────────────────────────────────────────────────────────────────
    public byte[] generarZipPartesPorOperario(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        // Agrupar: perfilId + especialidad → partes
        Map<String, List<partes_trabajo>> porPerfilEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name()
                            : "SIN";
                    String clave = p.getPerfil().getId() + "|" + esp;
                    porPerfilEsp.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
                });

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
            for (Map.Entry<String, List<partes_trabajo>> entry : porPerfilEsp.entrySet()) {
                List<partes_trabajo> partesGrupo = entry.getValue();
                partes_trabajo primero = partesGrupo.get(0);

                String nombreOperario = primero.getPerfil().getName()
                        + " " + primero.getPerfil().getApellidos();
                String esp = primero.getEspecialidad() != null
                        ? primero.getEspecialidad().name()
                        : "SIN";

                String sufijo = switch (esp) {
                    case "ELECTRICIDAD" -> "electricidad";
                    case "FONTANERIA"   -> "fontaneria";
                    default             -> "sin_especialidad";
                };
                String nombreFichero = sanitizarNombre(nombreOperario) + "_" + sufijo + ".pdf";

                byte[] pdfBytes = generarPdfGrupoPorOperario(nombreOperario, esp, partesGrupo, desde, hasta);
                zos.putNextEntry(new ZipEntry(nombreFichero));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }

        return zipBaos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
// PDF de un operario: tabla por cada obra donde ha trabajado
// ─────────────────────────────────────────────────────────────────────────
    private byte[] generarPdfGrupoPorOperario(
            String nombreOperario,
            String especialidad,
            List<partes_trabajo> partes,
            LocalDate desde,
            LocalDate hasta) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        agregarPiePagina(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);
        agregarTablaGrupoPorOperario(doc, nombreOperario, especialidad, partes);
        doc.close();
        return baos.toByteArray();
    }

// Tabla del operario: agrupa por obra, dentro filas por fecha
    private void agregarTablaGrupoPorOperario(
            Document doc,
            String nombreOperario,
            String especialidad,
            List<partes_trabajo> partes) throws Exception {

        // Cabecera del operario
        String titulo = nombreOperario;
        if (!especialidad.equals("SIN")) {
            titulo += "  ·  " + labelEspecialidad(especialidad);
        }

        // Agrupar por obra, ordenado por nombre de obra
        Map<Long, List<partes_trabajo>> porObra = new LinkedHashMap<>();
        partes.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> porObra
                        .computeIfAbsent(p.getObra().getId(), k -> new ArrayList<>())
                        .add(p));
        // Cada obra ordenada por fecha desc
        porObra.values().forEach(lista ->
                lista.sort(Comparator.comparing(partes_trabajo::getFecha).reversed()));

        PdfPTable tabla = crearTablaBase();
        agregarCeldaObra(tabla, titulo, especialidad, 4);

        for (Map.Entry<Long, List<partes_trabajo>> obraEntry : porObra.entrySet()) {
            List<partes_trabajo> partesObra = obraEntry.getValue();
            String nombreObra = partesObra.get(0).getObra().getNombre();

            // Subcabecera de obra (reutiliza estilo de operario)
            agregarCeldaOperario(tabla, nombreObra, 4);

            double totalHoras = 0;
            boolean par = false;
            for (partes_trabajo p : partesObra) {
                Color bgFila = par ? COLOR_FILA_PAR : Color.WHITE;
                par = !par;
                agregarCeldaDato(tabla, FMT.format(p.getFecha()), bgFila, Element.ALIGN_CENTER);
                agregarCeldaDato(tabla, formatHoras(p.getHoras_normales()), bgFila, Element.ALIGN_CENTER);
                String desc = (p.getDescripcion() != null && !p.getDescripcion().isBlank())
                        ? p.getDescripcion() : "Sin descripción";
                agregarCeldaDato(tabla, desc, bgFila, Element.ALIGN_LEFT);
                agregarCeldaDato(tabla, "", bgFila, Element.ALIGN_LEFT);
                totalHoras += p.getHoras_normales();
            }
            agregarCeldaTotal(tabla, totalHoras, 4);
        }

        doc.add(tabla);
    }
}