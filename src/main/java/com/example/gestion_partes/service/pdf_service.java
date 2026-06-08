/* Servicio que genera documentos PDF de partes de trabajo.
   Permite crear PDFs individuales por obra+especialidad, por operario+especialidad,
   o un único PDF con todas las obras. También soporta descarga en ZIP con múltiples PDFs.
   Usa la librería iText (com.lowagie.text) para construir los PDFs con formato profesional:
   cabeceras, tablas, colores, trabajos extra e imágenes de firma. */
package com.example.gestion_partes.service;

import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
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

    /* Colores corporativos para los distintos elementos del PDF. */
    private static final Color COLOR_HEADER_ELEC   = new Color(255, 185, 0);
    private static final Color COLOR_HEADER_FONT   = new Color(13,  71,  161);
    private static final Color COLOR_HEADER_OP     = new Color(232, 240, 255);
    private static final Color COLOR_FILA_PAR      = new Color(248, 249, 252);
    private static final Color COLOR_TOTAL         = new Color(240, 244, 255);
    private static final Color COLOR_TEXTO_BLANCO  = Color.WHITE;
    private static final Color COLOR_TEXTO_DARK    = new Color(26,  26,  46);
    private static final Color COLOR_BORDER        = new Color(200, 210, 230);
    private static final Color COLOR_EXTRA_BG      = new Color(255, 251, 235);
    private static final Color COLOR_EXTRA_BORDER  = new Color(251, 191, 36);
    private static final Color COLOR_FIRMA_BG      = new Color(240, 253, 244);
    private static final Color COLOR_FIRMA_BORDER  = new Color(134, 239, 172);

    /* Clase interna que maneja la cabecera dinámica (nombre de obra/operario) y el pie de página (número de página).
       Se usa como evento de página de iText para que aparezca en todas las páginas automáticamente. */
    private static class CabeceraPiePaginaEvent extends PdfPageEventHelper {

        String tituloActual = "";

        /* Se ejecuta al finalizar cada página: dibuja la cabecera (título) y el pie (número de página) usando el
           ContentByte directo del PdfWriter. */
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

    /* Genera un archivo ZIP que contiene un PDF por cada combinación de obra + especialidad.
       Recibe listas de IDs de obra y perfiles (opcionales), y un rango de fechas.
       Filtra los partes según esos criterios, los agrupa por obra+especialidad y genera un PDF para cada grupo. */
    public byte[] generarZipPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        /* Agrupa los partes por obra + especialidad usando un LinkedHashMap que preserva el orden de inserción. */
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

                /* Determina el sufijo del nombre del archivo según la especialidad. */
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

    /* Genera un ZIP con un PDF por cada combinación de operario + especialidad (agrupado por persona).
       Similar a generarZipPartes pero agrupando por operario en lugar de por obra. */
    public byte[] generarZipPartesPorOperario(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        /* Agrupa los partes por operario + especialidad. */
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

    /* Genera un único PDF con todos los partes. El orden es: primero toda la ELECTRICIDAD
       (obra a obra alfabéticamente), luego toda la FONTANERIA, luego el resto de especialidades.
       Dentro de cada especialidad las obras aparecen ordenadas alfabéticamente por nombre. */
    public byte[] generarPdfPartes(
            List<Long>   obraIds,
            List<String> perfilIds,
            LocalDate    desde,
            LocalDate    hasta) throws Exception {

        List<partes_trabajo> todos = filtrar(obraIds, perfilIds, desde, hasta);

        /* Agrupa primero por especialidad, luego por obra dentro de cada especialidad. */
        Map<String, Map<Long, List<partes_trabajo>>> porEspObra = new LinkedHashMap<>();
        todos.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> {
                    String esp = p.getEspecialidad() != null
                            ? p.getEspecialidad().name() : "SIN";
                    porEspObra
                            .computeIfAbsent(esp, k -> new LinkedHashMap<>())
                            .computeIfAbsent(p.getObra().getId(), k -> new ArrayList<>())
                            .add(p);
                });

        /* Orden de especialidades: ELECTRICIDAD primero, luego FONTANERIA, luego el resto alfabético. */
        List<String> ordenEsp = new ArrayList<>();
        if (porEspObra.containsKey("ELECTRICIDAD")) ordenEsp.add("ELECTRICIDAD");
        if (porEspObra.containsKey("FONTANERIA"))   ordenEsp.add("FONTANERIA");
        porEspObra.keySet().stream()
                .filter(k -> !k.equals("ELECTRICIDAD") && !k.equals("FONTANERIA"))
                .sorted()
                .forEach(ordenEsp::add);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = construirDocumento(baos);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        CabeceraPiePaginaEvent evento = agregarCabeceraYPie(writer);
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        boolean primerGrupo = true;
        for (String esp : ordenEsp) {
            Map<Long, List<partes_trabajo>> porObra = porEspObra.get(esp);
            for (List<partes_trabajo> partesGrupo : porObra.values()) {
                partes_trabajo primero = partesGrupo.get(0);
                String nombreObra = primero.getObra().getNombre();
                String codigoObra = primero.getObra().getCodigo();
                String prefijoCodigo = (codigoObra != null && !codigoObra.isBlank())
                        ? "[" + codigoObra + "]  " : "";
                evento.tituloActual = prefijoCodigo + nombreObra;

                if (!primerGrupo) doc.newPage();
                primerGrupo = false;
                agregarGrupoAlDocumento(doc, nombreObra, esp, partesGrupo, evento, false);
            }
        }

        if (porEspObra.isEmpty()) agregarVacio(doc);
        doc.close();
        return baos.toByteArray();
    }

    /* Genera un PDF para un grupo específico de obra + especialidad.
       Crea un documento nuevo, añade cabecera y la tabla con los partes del grupo. */
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

        String codigoObra = partes.get(0).getObra().getCodigo();
        String prefijoCodigo = (codigoObra != null && !codigoObra.isBlank())
                ? "[" + codigoObra + "]  " : "";
        evento.tituloActual = prefijoCodigo + nombreObra;

        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);
        // FIX 1: esPorOperario = false → muestra código de obra
        agregarGrupoAlDocumento(doc, nombreObra, especialidad, partes, evento, false);
        doc.close();
        return baos.toByteArray();
    }

    /* Genera un PDF para un operario concreto y una especialidad.
       Los partes se agrupan por obra para que cada obra tenga su propia sección con título
       "[código] NombreObra · Especialidad", igual que en el PDF por obra pero añadiendo
       el nombre del operario en la cabecera de página. */
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
        // La cabecera de página muestra el nombre del operario
        evento.tituloActual = nombreOp;
        doc.open();
        agregarCabeceraDocumento(doc, desde, hasta);

        // Agrupa los partes por obra (orden alfabético por nombre de obra)
        Map<Long, List<partes_trabajo>> porObra = new LinkedHashMap<>();
        partes.stream()
                .sorted(Comparator.comparing(p -> p.getObra().getNombre()))
                .forEach(p -> porObra
                        .computeIfAbsent(p.getObra().getId(), k -> new ArrayList<>())
                        .add(p));

        // Genera una sección por cada obra con el nombre real de la obra en el título
        for (List<partes_trabajo> partesObra : porObra.values()) {
            String nombreObra = partesObra.get(0).getObra().getNombre();
            // esPorOperario = false → muestra código y nombre de obra en el título de sección
            agregarGrupoAlDocumento(doc, nombreObra, especialidad, partesObra, evento, false);
        }

        doc.close();
        return baos.toByteArray();
    }

    /* Añade la cabecera de sección (nombre de obra/operario + especialidad) y las tablas de cada operario
       al documento. Agrupa los partes por operario y ordena cada grupo por fecha descendente.
       FIX 1: el parámetro esPorOperario controla si se muestra el código de obra en el título de sección.
       FIX 2: la celda de cabecera de sección se pasa a tablaOperario para que formen un único elemento
              iText, evitando que la cabecera quede huérfana en una página y la tabla salte a la siguiente. */
    private void agregarGrupoAlDocumento(
            Document doc,
            String nombreObra,
            String especialidad,
            List<partes_trabajo> partes,
            CabeceraPiePaginaEvent evento,
            boolean esPorOperario)   // FIX 1: true cuando el PDF es por operario
            throws Exception {

        // FIX 1: Solo añadir prefijo de código de obra cuando NO es un PDF por operario
        String codigoObra = partes.get(0).getObra().getCodigo();
        String prefijoCodigo = (!esPorOperario && codigoObra != null && !codigoObra.isBlank())
                ? "[" + codigoObra + "]  " : "";

        String tituloSeccion = prefijoCodigo + nombreObra;
        if (!especialidad.equals("SIN")) {
            tituloSeccion += "  ·  " + labelEspecialidad(especialidad);
        }

        /* Color de fondo diferente según especialidad: amarillo para electricidad, azul para fontanería. */
        Color bgHeader = especialidad.equals("FONTANERIA") ? COLOR_HEADER_FONT : COLOR_HEADER_ELEC;
        Font fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_TEXTO_BLANCO);

        // FIX 2: Se construye la celda de sección pero ya NO se añade al doc como tabla independiente.
        //        Se pasará directamente a cada tablaOperario como primera fila, así cabecera y datos
        //        forman un único bloque y iText no puede separarlos entre páginas.
        PdfPCell celdaSeccion = new PdfPCell(new Phrase(tituloSeccion, fHeader));
        celdaSeccion.setColspan(4);
        celdaSeccion.setBackgroundColor(bgHeader);
        celdaSeccion.setPadding(8);
        celdaSeccion.setBorderWidth(0);

        /* Agrupa los partes por operario (UUID) y ordena cada grupo por fecha descendente (más reciente primero). */
        Map<UUID, List<partes_trabajo>> porOperario = new LinkedHashMap<>();
        partes.stream()
                .sorted(Comparator.comparing(p -> p.getPerfil().getApellidos()))
                .forEach(p -> porOperario
                        .computeIfAbsent(p.getPerfil().getId(), k -> new ArrayList<>())
                        .add(p));
        porOperario.values().forEach(lista ->
                lista.sort(Comparator.comparing(partes_trabajo::getFecha).reversed()));

        // FIX 2: El primer operario recibe la celda de sección; el resto recibe null (sin duplicar cabecera).
        boolean esPrimerOperario = true;
        for (List<partes_trabajo> partesOp : porOperario.values()) {
            PdfPCell cabecera = esPrimerOperario ? celdaSeccion : null;
            doc.add(tablaOperario(partesOp, cabecera));
            esPrimerOperario = false;
        }
    }

    /* Crea la tabla de un operario con sus partes de trabajo. La tabla tiene 4 columnas:
       fecha, horas, descripción y un separador invisible.
       FIX 2: acepta una celdaSeccion opcional que se inserta como primera fila si no es null,
              uniendo visualmente la cabecera azul/amarilla con los datos en un único elemento iText.
       Incluye: cabecera de sección (opcional), cabecera del operario, filas de cada parte,
                total de horas, trabajos extra (si los hay) y firma. */
    private PdfPTable tablaOperario(
            List<partes_trabajo> partesOp,
            PdfPCell celdaSeccion)   // FIX 2: celda de sección a insertar como primera fila (puede ser null)
            throws Exception {

        partes_trabajo primero = partesOp.get(0);
        String nombreOp = primero.getPerfil().getName()
                + " " + primero.getPerfil().getApellidos();

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2f, 1f, 5f, 0.1f});
        tabla.setSpacingBefore(12);
        tabla.setSpacingAfter(2);
        tabla.setKeepTogether(false);
        // Permite que la tabla se parta entre páginas fila a fila
        tabla.setSplitRows(true);
        tabla.setSplitLate(false);

        // Fila 1 (opcional): celda de sección azul/amarilla
        if (celdaSeccion != null) {
            tabla.addCell(celdaSeccion);
        }

        /* Fila de cabecera con el nombre del operario (ocupa las 4 columnas). */
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

        // setHeaderRows: las N primeras filas se repiten al inicio de cada página si la tabla
        // se parte. Con celdaSeccion son 2 filas (sección + operario); sin ella, 1 (solo operario).
        // Esto garantiza que nunca quede la cabecera sola en una página sin sus datos.
        tabla.setHeaderRows(celdaSeccion != null ? 2 : 1);

        /* Filas de cada parte de trabajo: fecha, horas, descripción. */
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

        /* Fila de total de horas del operario. */
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

        /* Acumula todos los trabajos extra de todos los partes del operario (con su fecha). */
        String trabajosExtraAcumulados = partesOp.stream()
                .filter(p -> p.getTrabajos_extra() != null && !p.getTrabajos_extra().isBlank())
                .map(p -> "• [" + FMT.format(p.getFecha()) + "] " + p.getTrabajos_extra().trim())
                .collect(Collectors.joining("\n"));

        if (!trabajosExtraAcumulados.isBlank()) {
            /* Etiqueta "Trabajos extra" con fondo amarillo claro. */
            Font fExtraLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8,
                    new Color(146, 64, 14));
            PdfPCell celdaExtraLabel = new PdfPCell(new Phrase("Trabajos extra", fExtraLabel));
            celdaExtraLabel.setColspan(4);
            celdaExtraLabel.setBackgroundColor(COLOR_EXTRA_BG);
            celdaExtraLabel.setPaddingTop(5);
            celdaExtraLabel.setPaddingBottom(2);
            celdaExtraLabel.setPaddingLeft(8);
            celdaExtraLabel.setPaddingRight(8);
            celdaExtraLabel.setBorderColor(COLOR_EXTRA_BORDER);
            celdaExtraLabel.setBorderWidthTop(1f);
            celdaExtraLabel.setBorderWidthBottom(0);
            celdaExtraLabel.setBorderWidthLeft(2f);
            celdaExtraLabel.setBorderWidthRight(0);
            tabla.addCell(celdaExtraLabel);

            /* Contenido de los trabajos extra. */
            Font fExtra = FontFactory.getFont(FontFactory.HELVETICA, 8,
                    new Color(92, 45, 14));
            PdfPCell celdaExtra = new PdfPCell(new Phrase(trabajosExtraAcumulados, fExtra));
            celdaExtra.setColspan(4);
            celdaExtra.setBackgroundColor(COLOR_EXTRA_BG);
            celdaExtra.setPaddingTop(2);
            celdaExtra.setPaddingBottom(6);
            celdaExtra.setPaddingLeft(8);
            celdaExtra.setPaddingRight(8);
            celdaExtra.setBorderColor(COLOR_EXTRA_BORDER);
            celdaExtra.setBorderWidthTop(0);
            celdaExtra.setBorderWidthBottom(1f);
            celdaExtra.setBorderWidthLeft(2f);
            celdaExtra.setBorderWidthRight(0);
            tabla.addCell(celdaExtra);
        }

        /* Busca el parte más reciente que tenga una firma (imagen) y la añade si existe. */
        partes_trabajo parteConFirma = partesOp.stream()
                .filter(p -> p.getFirma_url() != null && !p.getFirma_url().isBlank())
                .findFirst()
                .orElse(null);

        if (parteConFirma != null) {
            agregarFilaFirma(tabla, parteConFirma);
        }

        return tabla;
    }

    /* Añade una fila con la imagen de firma dentro de la tabla del operario.
       Descarga la imagen desde la URL almacenada en el parte de trabajo y la escala para mostrarla.
       Si no se puede descargar la imagen, muestra un texto indicando que no está disponible. */
    private void agregarFilaFirma(PdfPTable tabla, partes_trabajo parte) {
        try {
            byte[] imagenBytes = descargarImagen(parte.getFirma_url());
            if (imagenBytes == null || imagenBytes.length == 0) return;

            Image firmaImg = Image.getInstance(imagenBytes);
            firmaImg.scaleToFit(120, 50);

            /* Etiqueta con el nombre de la persona que firmó. */
            String nombreFirmador = parte.getNombre_firmado() != null
                    ? parte.getNombre_firmado() : "Firmado";
            Font fFirmaLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8,
                    new Color(20, 83, 45));
            PdfPCell celdaFirmaLabel = new PdfPCell(
                    new Phrase("Conformidad - " + nombreFirmador, fFirmaLabel));
            celdaFirmaLabel.setColspan(4);
            celdaFirmaLabel.setBackgroundColor(COLOR_FIRMA_BG);
            celdaFirmaLabel.setPaddingTop(5);
            celdaFirmaLabel.setPaddingBottom(2);
            celdaFirmaLabel.setPaddingLeft(8);
            celdaFirmaLabel.setPaddingRight(8);
            celdaFirmaLabel.setBorderColor(COLOR_FIRMA_BORDER);
            celdaFirmaLabel.setBorderWidthTop(1f);
            celdaFirmaLabel.setBorderWidthBottom(0);
            celdaFirmaLabel.setBorderWidthLeft(2f);
            celdaFirmaLabel.setBorderWidthRight(0);
            tabla.addCell(celdaFirmaLabel);

            /* Imagen de la firma escalada. */
            PdfPCell celdaFirmaImg = new PdfPCell(firmaImg, false);
            celdaFirmaImg.setColspan(4);
            celdaFirmaImg.setBackgroundColor(COLOR_FIRMA_BG);
            celdaFirmaImg.setPaddingTop(4);
            celdaFirmaImg.setPaddingBottom(8);
            celdaFirmaImg.setPaddingLeft(8);
            celdaFirmaImg.setPaddingRight(8);
            celdaFirmaImg.setHorizontalAlignment(Element.ALIGN_LEFT);
            celdaFirmaImg.setBorderColor(COLOR_FIRMA_BORDER);
            celdaFirmaImg.setBorderWidthTop(0);
            celdaFirmaImg.setBorderWidthBottom(1f);
            celdaFirmaImg.setBorderWidthLeft(2f);
            celdaFirmaImg.setBorderWidthRight(0);
            tabla.addCell(celdaFirmaImg);

        } catch (Exception e) {
            /* Si falla la descarga o carga de la imagen, muestra un texto alternativo en cursiva. */
            try {
                Font fFirmaError = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
                String nombreFirmador = parte.getNombre_firmado() != null
                        ? parte.getNombre_firmado() : "firmante desconocido";
                PdfPCell celdaError = new PdfPCell(
                        new Phrase("Firma de " + nombreFirmador + " (imagen no disponible)", fFirmaError));
                celdaError.setColspan(4);
                celdaError.setBackgroundColor(COLOR_FIRMA_BG);
                celdaError.setPadding(6);
                celdaError.setBorderColor(COLOR_FIRMA_BORDER);
                celdaError.setBorderWidthTop(1f);
                celdaError.setBorderWidthBottom(1f);
                celdaError.setBorderWidthLeft(2f);
                celdaError.setBorderWidthRight(0);
                tabla.addCell(celdaError);
            } catch (Exception ignored) {}
        }
    }

    /* Descarga una imagen desde una URL y devuelve su contenido como array de bytes.
       Usa un buffer de 8KB para la lectura. Si falla, devuelve null. */
    private byte[] descargarImagen(String urlStr) {
        try {
            URL url = new URL(urlStr);
            try (InputStream is = url.openStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /* Crea un documento PDF en formato A4 con márgenes personalizados (36px izquierda/derecha, 65 arriba, 40 abajo). */
    private Document construirDocumento(ByteArrayOutputStream baos) {
        return new Document(PageSize.A4, 36, 36, 65, 40);
    }

    /* Añade el evento de cabecera y pie de página al PdfWriter y devuelve el objeto evento para poder cambiar el título. */
    private CabeceraPiePaginaEvent agregarCabeceraYPie(PdfWriter writer) {
        CabeceraPiePaginaEvent evento = new CabeceraPiePaginaEvent();
        writer.setPageEvent(evento);
        return evento;
    }

    /* Añade la cabecera general del documento: título "Informe de Partes de Trabajo" y el rango de fechas. */
    private void agregarCabeceraDocumento(Document doc, LocalDate desde, LocalDate hasta)
            throws Exception {
        Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_TEXTO_DARK);
        Font fSub    = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

        Paragraph titulo = new Paragraph("Informe de Partes de Trabajo", fTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        Paragraph rango = new Paragraph(
                "Período: " + FMT.format(desde) + "  ->  " + FMT.format(hasta), fSub);
        rango.setAlignment(Element.ALIGN_CENTER);
        rango.setSpacingAfter(16);
        doc.add(rango);
    }

    /* Añade un mensaje de "sin datos" cuando no hay partes que coincidan con los filtros. */
    private void agregarVacio(Document doc) throws Exception {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.GRAY);
        Paragraph p = new Paragraph("No hay partes para los filtros seleccionados.", f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(40);
        doc.add(p);
    }

    /* Crea una celda de datos estándar en una tabla PDF: recibe texto, color de fondo y alineación. */
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

    /* Filtra los partes de trabajo por obra, perfil y rango de fechas.
       Si obraIds o perfilIds están vacíos, no filtra por ese criterio. */
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

    /* Devuelve la etiqueta legible para una especialidad (ELECTRICIDAD -> "Electricidad", FONTANERIA -> "Fontanería"). */
    private String labelEspecialidad(String esp) {
        return switch (esp) {
            case "ELECTRICIDAD" -> "Electricidad";
            case "FONTANERIA"   -> "Fontanería";
            default             -> esp;
        };
    }

    /* Formatea un número de horas: si es entero muestra "8h", si tiene decimales muestra "7.5h". */
    private String formatHoras(double h) {
        return h % 1 == 0 ? (int) h + "h" : h + "h";
    }

    /* Limpia un nombre para usarlo como nombre de archivo: elimina caracteres no permitidos
       (solo permite letras, números, espacios, guiones y guiones bajos). */
    private String sanitizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "_").trim();
    }
}