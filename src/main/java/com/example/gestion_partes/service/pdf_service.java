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
    private static final Color COLOR_HEADER_ELEC  = new Color(255, 185, 0);
    private static final Color COLOR_HEADER_FONT  = new Color(13,  71,  161);
    private static final Color COLOR_HEADER_OP    = new Color(232, 240, 255);
    private static final Color COLOR_FILA_PAR     = new Color(248, 249, 252);
    private static final Color COLOR_TOTAL        = new Color(240, 244, 255);
    private static final Color COLOR_TEXTO_BLANCO = Color.WHITE;
    private static final Color COLOR_TEXTO_DARK   = new Color(26,  26,  46);
    private static final Color COLOR_BORDER       = new Color(200, 210, 230);

    // ─────────────────────────────────────────────────────────────────────────
    // Clase interna: cabecera dinámica + pie de página
    // ─────────────────────────────────────────────────────────────────────────
    private static class CabeceraPiePaginaEvent extends PdfPageEventHelper {

        String tituloActual = "";

        @Override
        public void onEndPage(PdfWriter w, Document d) {
            try {
                PdfContentByte cb = w.getDirectContent();

                Font fCab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,
                        new Color(26, 26, 46));
                Phrase header = new Phrase(tituloActual, fCab);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, header,
                        d.left(), d.top() + 14, 0);

                cb.setColorStroke(new Color(200, 210, 230));
                cb.setLineWidth(0.5f);
                cb.moveTo(d.left(), d.top() + 10);
                cb.lineTo(d.right(), d.top() + 10);
                cb.stroke();

                Phrase footer = new Phrase(
                        "Página " + w.getPageNumber(),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, footer,
                        d.right(), d.bottom() - 10, 0);

            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ZIP: un PDF por cada combinación obra + especialidad
    // Cabecera = nombre de la obra
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarZipPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        Map<String, List<partes_trabajo>> porObraEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name() : "SIN";
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
                        ? primero.getEspecialidad().name() : "SIN";

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
    // ZIP: un PDF por cada combinación operario + especialidad
    // Cabecera = nombre del operario
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarZipPartesPorOperario(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        Map<String, List<partes_trabajo>> porOperarioEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name() : "SIN";
                    String clave = p.getPerfil().getId() + "|" + esp;
                    porOperarioEsp.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
                });

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
            for (Map.Entry<String, List<partes_trabajo>> entry : porOperarioEsp.entrySet()) {
                List<partes_trabajo> partesGrupo = entry.getValue();
                partes_trabajo primero = partesGrupo.get(0);

                String nombreOp = primero.getPerfil().getName()
                        + " " + primero.getPerfil().getApellidos();
                String esp = primero.getEspecialidad() != null
                        ? primero.getEspecialidad().name() : "SIN";

                String sufijo = switch (esp) {
                    case "ELECTRICIDAD" -> "electricidad";
                    case "FONTANERIA"   -> "fontaneria";
                    default             -> "sin_especialidad";
                };
                String nombreFichero = sanitizarNombre(nombreOp) + "_" + sufijo + ".pdf";

                byte[] pdfBytes = generarPdfOperarioEsp(nombreOp, esp, partesGrupo, desde, hasta);
                zos.putNextEntry(new ZipEntry(nombreFichero));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }

        return zipBaos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF único — todas las obras en un solo archivo
    // Una página por cada combinación obra + especialidad
    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generarPdfPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        Map<Long, Map<String, List<partes_trabajo>>> porObraEsp = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name() : "SIN";
                    porObraEsp
                            .computeIfAbsent(p.getObra().getId(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(esp, k -> new ArrayList<>())
                            .add(p);
                });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        CabeceraPiePaginaEvent evento = agregarCabeceraYPie(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        boolean primerGrupo = true;
        for (Map.Entry<Long, Map<String, List<partes_trabajo>>> obraEntry : porObraEsp.entrySet()) {
            Map<String, List<partes_trabajo>> porEsp = obraEntry.getValue();

            List<String> ordenEsp = new ArrayList<>();
            if (porEsp.containsKey("ELECTRICIDAD")) ordenEsp.add("ELECTRICIDAD");
            if (porEsp.containsKey("FONTANERIA"))   ordenEsp.add("FONTANERIA");
            porEsp.keySet().stream()
                    .filter(k -> !k.equals("ELECTRICIDAD") && !k.equals("FONTANERIA"))
                    .forEach(ordenEsp::add);

            String nombreObra = porEsp.values().iterator().next().get(0).getObra().getNombre();

            for (String esp : ordenEsp) {
                if (!primerGrupo) {
                    doc.newPage();
                }
                primerGrupo = false;
                evento.tituloActual = nombreObra;
                agregarGrupoAlDocumento(doc, nombreObra, esp, porEsp.get(esp), evento);
            }
        }

        if (porObraEsp.isEmpty()) agregarVacio(doc);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF de un grupo obra + especialidad (usado por generarZipPartes)
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
        CabeceraPiePaginaEvent evento = agregarCabeceraYPie(writer);
        evento.tituloActual = nombreObra;
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);
        agregarGrupoAlDocumento(doc, nombreObra, especialidad, partes, evento);
        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF de un operario + especialidad (usado por generarZipPartesPorOperario)
    // ─────────────────────────────────────────────────────────────────────────
    private byte[] generarPdfOperarioEsp(
            String nombreOp,
            String especialidad,
            List<partes_trabajo> partes,
            LocalDate desde,
            LocalDate hasta) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        CabeceraPiePaginaEvent evento = agregarCabeceraYPie(writer);
        evento.tituloActual = nombreOp;
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        agregarGrupoAlDocumento(doc, nombreOp, especialidad, partes, evento);

        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Añade la cabecera de sección y las tablas por operario
    // ─────────────────────────────────────────────────────────────────────────
    private void agregarGrupoAlDocumento(
            Document doc,
            String nombreObra,
            String especialidad,
            List<partes_trabajo> partes,
            CabeceraPiePaginaEvent evento) throws Exception {

        String tituloSeccion = nombreObra;
        if (!especialidad.equals("SIN")) {
            tituloSeccion += "  ·  " + labelEspecialidad(especialidad);
        }

        PdfPTable tablaHeader = new PdfPTable(1);
        tablaHeader.setWidthPercentage(100);
        tablaHeader.setSpacingBefore(12);
        tablaHeader.setSpacingAfter(0);
        tablaHeader.setKeepTogether(true);

        Color bgHeader = especialidad.equals("FONTANERIA") ? COLOR_HEADER_FONT : COLOR_HEADER_ELEC;
        Font fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_TEXTO_BLANCO);
        PdfPCell celdaHeader = new PdfPCell(new Phrase(tituloSeccion, fHeader));
        celdaHeader.setBackgroundColor(bgHeader);
        celdaHeader.setPadding(8);
        celdaHeader.setBorderWidth(0);
        tablaHeader.addCell(celdaHeader);
        doc.add(tablaHeader);

        Map<UUID, List<partes_trabajo>> porOperario = new LinkedHashMap<>();
        partes.stream()
                .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                .forEach(p -> porOperario
                        .computeIfAbsent(p.getPerfil().getId(), k -> new ArrayList<>())
                        .add(p));
        porOperario.values().forEach(lista ->
                lista.sort(Comparator.comparing(partes_trabajo::getFecha).reversed()));

        for (List<partes_trabajo> partesOp : porOperario.values()) {
            doc.add(tablaOperario(partesOp));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tabla de un operario
    // ─────────────────────────────────────────────────────────────────────────
    private PdfPTable tablaOperario(List<partes_trabajo> partesOp) throws Exception {
        partes_trabajo primero = partesOp.get(0);
        String nombreOp = primero.getPerfil().getName()
                + " " + primero.getPerfil().getApellidos();

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2f, 1f, 5f, 0.1f});
        tabla.setSpacingBefore(0);
        tabla.setSpacingAfter(2);
        tabla.setKeepTogether(true);

        Font fOp = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_TEXTO_DARK);
        PdfPCell celdaOp = new PdfPCell(new Phrase("  " + nombreOp, fOp));
        celdaOp.setColspan(4);
        celdaOp.setBackgroundColor(COLOR_HEADER_OP);
        celdaOp.setPadding(6);
        celdaOp.setBorderColor(COLOR_BORDER);
        celdaOp.setBorderWidthTop(1);
        celdaOp.setBorderWidthBottom(0);
        celdaOp.setBorderWidthLeft(0);
        celdaOp.setBorderWidthRight(0);
        tabla.addCell(celdaOp);

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

        String textoTotal = "Total: " + formatHoras(totalHoras);
        Font fTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_TEXTO_DARK);
        PdfPCell celdaTotal = new PdfPCell(new Phrase(textoTotal, fTotal));
        celdaTotal.setColspan(4);
        celdaTotal.setBackgroundColor(COLOR_TOTAL);
        celdaTotal.setPadding(5);
        celdaTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaTotal.setBorderColor(COLOR_BORDER);
        celdaTotal.setBorderWidthTop(0.5f);
        celdaTotal.setBorderWidthBottom(1f);
        celdaTotal.setBorderWidthLeft(0);
        celdaTotal.setBorderWidthRight(0);
        tabla.addCell(celdaTotal);

        return tabla;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de documento
    // ─────────────────────────────────────────────────────────────────────────

    private Document construirDocumento(ByteArrayOutputStream baos) {
        return new Document(PageSize.A4, 36, 36, 65, 40);
    }

    private CabeceraPiePaginaEvent agregarCabeceraYPie(PdfWriter writer) {
        CabeceraPiePaginaEvent evento = new CabeceraPiePaginaEvent();
        writer.setPageEvent(evento);
        return evento;
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
            case "FONTANERIA"   -> "Fontanería";
            default             -> esp;
        };
    }

    private String formatHoras(double h) {
        return h % 1 == 0 ? (int) h + "h" : h + "h";
    }

    private String sanitizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "_").trim();
    }
}