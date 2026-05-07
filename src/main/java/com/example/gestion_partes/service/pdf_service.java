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
    private static final Color COLOR_HEADER_OBRA  = new Color(21,  101, 192);
    private static final Color COLOR_HEADER_ESP   = new Color(13,  71,  161);
    private static final Color COLOR_HEADER_OP    = new Color(232, 240, 255);
    private static final Color COLOR_FILA_PAR     = new Color(248, 249, 252);
    private static final Color COLOR_TOTAL        = new Color(240, 244, 255);
    private static final Color COLOR_TEXTO_BLANCO = Color.WHITE;
    private static final Color COLOR_TEXTO_DARK   = new Color(26,  26,  46);
    private static final Color COLOR_BORDER       = new Color(200, 210, 230);

    // ─────────────────────────────────────────────────────────────────────────
    // ZIP: un PDF por obra física
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarZipPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        // 1. Obtener y filtrar partes
        List<partes_trabajo> todos = partes_trabajo_repo.findAll().stream()
                .filter(p -> !p.getFecha().isBefore(desde) && !p.getFecha().isAfter(hasta))
                .filter(p -> obraIds   == null || obraIds.isEmpty()   || obraIds.contains(p.getObra().getId()))
                .filter(p -> perfilIds == null || perfilIds.isEmpty() || perfilIds.contains(p.getPerfil().getId().toString()))
                .collect(Collectors.toList());

        // 2. Agrupar por obra física (id de obra, no por especialidad)
        Map<Long, List<partes_trabajo>> porObra = new LinkedHashMap<>();
        todos.forEach(p ->
                porObra.computeIfAbsent(p.getObra().getId(), k -> new ArrayList<>()).add(p));

        // 3. Construir el ZIP
        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
            for (Map.Entry<Long, List<partes_trabajo>> entry : porObra.entrySet()) {
                List<partes_trabajo> partesObra = entry.getValue();
                String nombreObra = partesObra.get(0).getObra().getNombre();

                byte[] pdfBytes = generarPdfUnaObra(nombreObra, partesObra, desde, hasta);

                // Nombre del fichero dentro del ZIP: sanitizamos caracteres especiales
                String nombreFichero = sanitizarNombre(nombreObra) + ".pdf";
                zos.putNextEntry(new ZipEntry(nombreFichero));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }

        return zipBaos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF único (todas las obras en un solo archivo) — se mantiene para compatibilidad
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarPdfPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = partes_trabajo_repo.findAll().stream()
                .filter(p -> !p.getFecha().isBefore(desde) && !p.getFecha().isAfter(hasta))
                .filter(p -> obraIds   == null || obraIds.isEmpty()   || obraIds.contains(p.getObra().getId()))
                .filter(p -> perfilIds == null || perfilIds.isEmpty() || perfilIds.contains(p.getPerfil().getId().toString()))
                .collect(Collectors.toList());

        // Agrupar: obra física → especialidad → operario → partes
        Map<Long, List<partes_trabajo>> porObra = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> porObra.computeIfAbsent(p.getObra().getId(), k -> new ArrayList<>()).add(p));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        agregarPiePagina(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        for (List<partes_trabajo> partesObra : porObra.values()) {
            String nombreObra = partesObra.get(0).getObra().getNombre();
            agregarSeccionObra(doc, nombreObra, partesObra);
        }

        if (porObra.isEmpty()) agregarVacio(doc);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Genera el PDF de una sola obra (usado por el ZIP)
    // Dentro de la obra separa por especialidad: primero ELECTRICIDAD, luego FONTANERIA
    // ─────────────────────────────────────────────────────────────────────────
    private byte[] generarPdfUnaObra(
            String nombreObra,
            List<partes_trabajo> partes,
            LocalDate desde,
            LocalDate hasta) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        agregarPiePagina(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);
        agregarSeccionObra(doc, nombreObra, partes);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sección de una obra: agrupa por especialidad → operario
    // ─────────────────────────────────────────────────────────────────────────
    private void agregarSeccionObra(Document doc, String nombreObra, List<partes_trabajo> partes) throws Exception {

        // Separar por especialidad: ELECTRICIDAD primero, FONTANERIA después, sin especialidad al final
        Map<String, List<partes_trabajo>> porEspecialidad = new LinkedHashMap<>();
        for (partes_trabajo p : partes) {
            String esp = p.getEspecialidad() != null ? p.getEspecialidad().name() : "SIN_ESPECIALIDAD";
            porEspecialidad.computeIfAbsent(esp, k -> new ArrayList<>()).add(p);
        }

        // Orden: ELECTRICIDAD → FONTANERIA → resto
        List<String> ordenEsp = new ArrayList<>();
        if (porEspecialidad.containsKey("ELECTRICIDAD"))    ordenEsp.add("ELECTRICIDAD");
        if (porEspecialidad.containsKey("FONTANERIA"))      ordenEsp.add("FONTANERIA");
        porEspecialidad.keySet().stream()
                .filter(k -> !k.equals("ELECTRICIDAD") && !k.equals("FONTANERIA"))
                .forEach(ordenEsp::add);

        for (String esp : ordenEsp) {
            List<partes_trabajo> partesEsp = porEspecialidad.get(esp);

            // Título de la sección: "OBRA · ELECTRICIDAD" o solo "OBRA"
            String tituloSeccion = nombreObra;
            if (!esp.equals("SIN_ESPECIALIDAD")) {
                tituloSeccion += "  ·  " + (esp.equals("FONTANERIA") ? "Fontanería" : "Electricidad");
            }

            // Agrupar por operario dentro de esta especialidad
            Map<UUID, List<partes_trabajo>> porOperario = new LinkedHashMap<>();
            partesEsp.stream()
                    .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                    .forEach(p -> porOperario
                            .computeIfAbsent(p.getPerfil().getId(), k -> new ArrayList<>())
                            .add(p));

            // Ordenar cada operario por fecha desc
            porOperario.values().forEach(lista ->
                    lista.sort(Comparator.comparing(partes_trabajo::getFecha).reversed()));

            // Construir tabla
            PdfPTable tabla = new PdfPTable(4);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{2f, 1f, 5f, 0.1f});
            tabla.setSpacingBefore(12);
            tabla.setSpacingAfter(6);
            tabla.setKeepTogether(false);

            // Cabecera de obra/especialidad
            agregarCeldaObra(tabla, tituloSeccion, esp, 4);

            for (List<partes_trabajo> partesOp : porOperario.values()) {
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
                    String hStr = formatHoras(p.getHoras_normales());
                    agregarCeldaDato(tabla, hStr, bgFila, Element.ALIGN_CENTER);
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

    private void agregarCabeceraDocumento(Document doc, LocalDate desde, LocalDate hasta) throws Exception {
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
        // Color diferente para fontanería vs electricidad
        Color bg = especialidad.equals("FONTANERIA") ? new Color(13, 71, 161) : COLOR_HEADER_OBRA;
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

    private String formatHoras(double h) {
        return h % 1 == 0 ? String.valueOf((int) h) + "h" : h + "h";
    }

    private String sanitizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "_").trim();
    }
}