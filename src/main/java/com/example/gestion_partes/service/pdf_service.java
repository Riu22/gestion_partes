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

@Service
public class pdf_service {

    @Autowired
    private partes_trabajo_repo partes_trabajo_repo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Colores
    private static final Color COLOR_HEADER_OBRA  = new Color(21,  101, 192); // azul oscuro
    private static final Color COLOR_HEADER_OP    = new Color(232, 240, 255); // azul claro
    private static final Color COLOR_FILA_PAR     = new Color(248, 249, 252); // gris muy claro
    private static final Color COLOR_TOTAL        = new Color(240, 244, 255); // azul pálido
    private static final Color COLOR_TEXTO_BLANCO = Color.WHITE;
    private static final Color COLOR_TEXTO_DARK   = new Color(26,  26,  46);
    private static final Color COLOR_BORDER       = new Color(200, 210, 230);

    /**
     * Genera el PDF filtrando por obras, operarios y rango de fechas.
     *
     * @param obraIds      IDs de obras a incluir (null o vacío = todas)
     * @param perfilIds    IDs de perfiles a incluir (null o vacío = todos)
     * @param desde        fecha inicio (inclusive)
     * @param hasta        fecha fin (inclusive)
     */
    public byte[] generarPdfPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        // 1. Obtener partes del repositorio
        List<partes_trabajo> todos = partes_trabajo_repo.findAll().stream()
                .filter(p -> !p.getFecha().isBefore(desde) && !p.getFecha().isAfter(hasta))
                .filter(p -> obraIds   == null || obraIds.isEmpty()   || obraIds.contains(p.getObra().getId()))
                .filter(p -> perfilIds == null || perfilIds.isEmpty() || perfilIds.contains(p.getPerfil().getId().toString()))
                .collect(Collectors.toList());

        // 2. Agrupar: obra+especialidad → operario → partes ordenados desc por fecha
        //    La clave de agrupación de obra es "nombre [FONT.]" o "nombre" según la query existente
        Map<String, Map<String, List<partes_trabajo>>> porObraYOperario = new LinkedHashMap<>();

        // Ordenar partes por fecha desc
        todos.sort(Comparator.comparing(partes_trabajo::getFecha).reversed());

        for (partes_trabajo p : todos) {
            String claveObra = construirClaveObra(p);
            String claveOp   = p.getPerfil().getId().toString(); // usamos id para agrupar
            porObraYOperario
                    .computeIfAbsent(claveObra, k -> new LinkedHashMap<>())
                    .computeIfAbsent(claveOp,   k -> new ArrayList<>())
                    .add(p);
        }

        // Ordenar obras alfabéticamente
        Map<String, Map<String, List<partes_trabajo>>> obrasSorted = new LinkedHashMap<>();
        porObraYOperario.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> obrasSorted.put(e.getKey(), e.getValue()));

        // 3. Generar PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 50, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Número de página en el pie
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

        doc.open();

        // ── Cabecera del documento ──────────────────────────────────────────
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

        // ── Una sección por obra ────────────────────────────────────────────
        for (Map.Entry<String, Map<String, List<partes_trabajo>>> obraEntry : obrasSorted.entrySet()) {
            String obraNombre = obraEntry.getKey();
            Map<String, List<partes_trabajo>> operarios = obraEntry.getValue();

            // Tabla completa de la obra
            PdfPTable tabla = new PdfPTable(4); // fecha | horas | descripcion | espaciado
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{2f, 1f, 5f, 0.1f});
            tabla.setSpacingBefore(12);
            tabla.setSpacingAfter(8);
            tabla.setKeepTogether(false);

            // ── Cabecera de obra ──
            agregarCeldaObra(tabla, obraNombre, 4);

            // ── Por cada operario ──
            for (Map.Entry<String, List<partes_trabajo>> opEntry : operarios.entrySet()) {
                List<partes_trabajo> partes = opEntry.getValue();
                partes_trabajo primero = partes.get(0);
                String nombreOp = primero.getPerfil().getName()
                        + " " + primero.getPerfil().getApellidos();

                // Fila cabecera operario
                agregarCeldaOperario(tabla, nombreOp, 4);

                // Filas de partes (fecha desc)
                double totalHoras = 0;
                boolean par = false;
                for (partes_trabajo p : partes) {
                    Color bgFila = par ? COLOR_FILA_PAR : Color.WHITE;
                    par = !par;

                    agregarCeldaDato(tabla, FMT.format(p.getFecha()), bgFila, Element.ALIGN_CENTER);
                    String hStr = p.getHoras_normales() % 1 == 0
                            ? String.valueOf(p.getHoras_normales().intValue()) + "h"
                            : p.getHoras_normales() + "h";
                    agregarCeldaDato(tabla, hStr, bgFila, Element.ALIGN_CENTER);
                    String desc = (p.getDescripcion() != null && !p.getDescripcion().isBlank())
                            ? p.getDescripcion()
                            : "Sin descripción";
                    agregarCeldaDato(tabla, desc, bgFila, Element.ALIGN_LEFT);
                    agregarCeldaDato(tabla, "", bgFila, Element.ALIGN_LEFT); // espaciador

                    totalHoras += p.getHoras_normales();
                }

                // Fila total del operario
                agregarCeldaTotal(tabla, totalHoras, 4);
            }

            doc.add(tabla);
        }

        if (obrasSorted.isEmpty()) {
            Font fVacio = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.GRAY);
            Paragraph vacio = new Paragraph("No hay partes para los filtros seleccionados.", fVacio);
            vacio.setAlignment(Element.ALIGN_CENTER);
            vacio.setSpacingBefore(40);
            doc.add(vacio);
        }

        doc.close();
        return baos.toByteArray();
    }

    // ─── Helpers de celdas ─────────────────────────────────────────────────────

    private String construirClaveObra(partes_trabajo p) {
        String nombre = p.getObra().getNombre();
        if (p.getEspecialidad() != null &&
                p.getEspecialidad().name().equals("FONTANERIA")) {
            return "Font. " + nombre;
        }
        return nombre;
    }

    private void agregarCeldaObra(PdfPTable tabla, String texto, int colspan) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_TEXTO_BLANCO);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setColspan(colspan);
        cell.setBackgroundColor(COLOR_HEADER_OBRA);
        cell.setPadding(8);
        cell.setBorderColor(COLOR_HEADER_OBRA);
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
        String texto = "Total: " + (totalHoras % 1 == 0
                ? String.valueOf((int) totalHoras)
                : String.valueOf(totalHoras)) + "h";
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
}
