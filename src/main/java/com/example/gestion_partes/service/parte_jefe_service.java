/*
 * SERVICIO: parte_jefe_service (Gestion de partes de jefe/encargado)
 *
 * Proporciona la logica de negocio para los reportes que realizan
 * los jefes de obra y encargados. Estos reportes cubren un periodo
 * de tiempo (semana o quincena) y detallan las horas dedicadas a
 * cada obra, desglosadas por especialidad (electricidad/fontaneria).
 *
 * Metodos principales:
 *
 * CRUD:
 * - create_parte_jefe:    Crea un nuevo parte de jefe con sus lineas de detalle
 * - update_parte_jefe:    Modifica un parte existente (borra y recrea las lineas)
 * - delete_parte_jefe:    Elimina un parte de jefe
 * - get_partes_jefe:      Obtiene los partes segun el rol del usuario
 * - validar_parte_jefe:   Marca un parte como validado (solo ADMIN/GESTION)
 *
 * INFORMES:
 * - generar_informe:      Genera el informe detallado de un parte concreto
 * - generar_informe_rango: Genera informe agrupado de un rango de fechas
 * - get_resumen_mensual:  Resumen de un mes completo para un usuario
 * - get_resumen_mensual_por_usuario: Resumen mensual agrupado por cada jefe
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.*;
import com.example.gestion_partes.helper.calendario_laboral_helper;
import com.example.gestion_partes.model.*;
import com.example.gestion_partes.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
import java.time.LocalDate;

@Service
public class parte_jefe_service {

    @Autowired parte_jefe_repo parte_jefe_repo;
    @Autowired partes_jefe_obra_repo parte_jefe_obra_repo;
    @Autowired perfil_repo perfil_repo;
    @Autowired obra_repo obra_repo;
    @Autowired calendario_laboral_helper calendarioHelper;

    // ─── CREAR ────────────────────────────────────────────────────────────────

    /*
     * Crea un nuevo parte de jefe de obra para un periodo concreto.
     *
     * Recibe:
     * - dto: objeto con descripcion, fechas y lista de obras con horas
     * - sub: el UUID del usuario autenticado (debe ser JEFE_DE_OBRA)
     *
     * Devuelve: el partes_jefe creado con sus lineas de detalle
     *
     * Proceso:
     * 1. Verifica que el usuario sea JEFE_DE_OBRA
     * 2. Calcula las horas laborables del periodo (excluyendo festivos)
     * 3. Valida que las horas declaradas no superen las horas laborables
     * 4. Guarda el parte principal
     * 5. Por cada obra, crea una linea con horas electricas/mecanicas
     *    y calcula los porcentajes
     */
    @Transactional
    public partes_jefe create_parte_jefe(partes_jefe_dto dto, String sub) {

        perfil jefe = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        if (jefe.getRol() != user_rol.JEFE_DE_OBRA)
            throw new ResponseStatusException(FORBIDDEN, "Solo jefes de obra pueden crear este parte");

        double horasTotales = calendarioHelper.calcularHorasLaborales(
                dto.fecha_inicio(), dto.fecha_fin(), 8.0);

        if (horasTotales <= 0)
            throw new ResponseStatusException(BAD_REQUEST, "El periodo no contiene dias laborables");

        double sumaHoras = dto.obras().stream()
                .mapToDouble(o -> o.horas_electricas() + o.horas_mecanicas())
                .sum();
        if (sumaHoras > horasTotales + 0.01)
            throw new ResponseStatusException(BAD_REQUEST,
                    "Las horas totales (" + sumaHoras + "h) superan las horas laborables del periodo (" + horasTotales + "h)");

        partes_jefe nuevo = new partes_jefe();
        nuevo.setPerfil(jefe);
        nuevo.setDescripcion(dto.descripcion());
        nuevo.setFechaInicio(dto.fecha_inicio());
        nuevo.setFechaFin(dto.fecha_fin());
        nuevo.setTotalHorasLaborables(horasTotales);
        partes_jefe saved = parte_jefe_repo.save(nuevo);

        for (obra_horas_dto lineaDto : dto.obras()) {
            obra obra = obra_repo.findById(lineaDto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                            "Obra no encontrada: " + lineaDto.id_obra()));

            double pctElectrico = (lineaDto.horas_electricas() / horasTotales) * 100.0;
            double pctMecanico  = (lineaDto.horas_mecanicas()  / horasTotales) * 100.0;

            partes_jefe_obra linea = new partes_jefe_obra(
                    saved, obra,
                    lineaDto.horas_electricas(), lineaDto.horas_mecanicas(),
                    pctElectrico, pctMecanico);
            parte_jefe_obra_repo.save(linea);
        }

        return parte_jefe_repo.findById(saved.getId()).orElseThrow();
    }

    // ─── ACTUALIZAR ───────────────────────────────────────────────────────────

    /*
     * Modifica un parte de jefe existente.
     *
     * Recibe:
     * - parteId: ID del parte a modificar
     * - dto: nuevos datos del parte
     * - sub: UUID del usuario autenticado
     *
     * Devuelve: el parte actualizado
     *
     * Proceso:
     * 1. Verifica que el usuario sea el propietario del parte (o ADMIN/GESTION)
     * 2. Recalcula las horas laborables
     * 3. Actualiza los campos del parte principal
     * 4. Borra todas las lineas de detalle antiguas
     * 5. Crea las nuevas lineas con los datos actualizados
     */
    @Transactional
    public partes_jefe update_parte_jefe(Long parteId, partes_jefe_dto dto, String sub) {

        partes_jefe parte = parte_jefe_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Parte no encontrado"));

        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.JEFE_DE_OBRA
                && !parte.getPerfil().getId().equals(usuario.getId()))
            throw new ResponseStatusException(FORBIDDEN, "No puedes editar este parte");

        double horasTotales = calendarioHelper.calcularHorasLaborales(
                dto.fecha_inicio(), dto.fecha_fin(), 8.0);

        if (horasTotales <= 0)
            throw new ResponseStatusException(BAD_REQUEST, "El periodo no contiene dias laborables");

        double sumaHoras = dto.obras().stream()
                .mapToDouble(o -> o.horas_electricas() + o.horas_mecanicas())
                .sum();
        if (sumaHoras > horasTotales + 0.01)
            throw new ResponseStatusException(BAD_REQUEST,
                    "Las horas totales (" + sumaHoras + "h) superan las horas laborables del periodo (" + horasTotales + "h)");

        parte.setDescripcion(dto.descripcion());
        parte.setFechaInicio(dto.fecha_inicio());
        parte.setFechaFin(dto.fecha_fin());
        parte.setTotalHorasLaborables(horasTotales);
        parte_jefe_repo.save(parte);

        // Borrar lineas antiguas y recrearlas con los nuevos datos
        parte_jefe_obra_repo.deleteByParteJefeId(parteId);

        for (obra_horas_dto lineaDto : dto.obras()) {
            obra obra = obra_repo.findById(lineaDto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                            "Obra no encontrada: " + lineaDto.id_obra()));

            double pctElectrico = (lineaDto.horas_electricas() / horasTotales) * 100.0;
            double pctMecanico  = (lineaDto.horas_mecanicas()  / horasTotales) * 100.0;

            partes_jefe_obra linea = new partes_jefe_obra(
                    parte, obra,
                    lineaDto.horas_electricas(), lineaDto.horas_mecanicas(),
                    pctElectrico, pctMecanico);
            parte_jefe_obra_repo.save(linea);
        }

        return parte_jefe_repo.findById(parteId).orElseThrow();
    }

    // ─── CONSULTAS ────────────────────────────────────────────────────────────

    /*
     * Obtiene los partes de jefe segun el rol del usuario:
     * - ADMINISTRACION/GESTION: todos los partes de todos los jefes
     * - JEFE_DE_OBRA: solo sus propios partes
     * - Otros roles: lista vacia
     *
     * Recibe: sub (UUID del usuario autenticado)
     * Devuelve: lista de partes_jefe
     */
    public List<partes_jefe> get_partes_jefe(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.ADMINISTRACION
                || usuario.getRol() == user_rol.GESTION) {
            return parte_jefe_repo.findAll();
        }

        if (usuario.getRol() == user_rol.JEFE_DE_OBRA) {
            return parte_jefe_repo.findByPerfilId(usuario.getId());
        }

        return List.of();
    }

    /*
     * Valida (da por bueno) un parte de jefe.
     * Solo ADMINISTRACION y GESTION pueden validar.
     * Recibe: ID del parte y UUID del revisor
     */
    public void validar_parte_jefe(Long parteId, String sub) {
        partes_jefe parte = parte_jefe_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "Parte no encontrado"));
        perfil revisor = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "Revisor no encontrado"));

        if (revisor.getRol() == user_rol.ADMINISTRACION
                || revisor.getRol() == user_rol.GESTION) {
            parte_jefe_repo.save(parte);
            return;
        }

        throw new ResponseStatusException(FORBIDDEN,
                "Solo GESTION o ADMINISTRACION pueden validar partes de jefes de obra");
    }

    /*
     * Elimina un parte de jefe por su ID.
     */
    public void delete_parte_jefe(Long parteId) {
        if (!parte_jefe_repo.existsById(parteId)) {
            throw new ResponseStatusException(NOT_FOUND, "Parte no encontrado");
        }
        parte_jefe_repo.deleteById(parteId);
    }

    // ─── INFORMES ─────────────────────────────────────────────────────────────

    /*
     * Genera el informe detallado de un parte de jefe concreto.
     * Incluye todas las lineas de detalle con sus horas y porcentajes.
     *
     * Recibe: ID del parte y UUID del usuario autenticado
     * Devuelve: un informe_jefe_dto con los datos del parte y sus lineas
     */
    public informe_jefe_dto generar_informe(Long parteId, String sub) {
        partes_jefe parte = parte_jefe_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Parte no encontrado"));

        perfil usuario = perfil_repo.findById(UUID.fromString(sub)).orElseThrow();
        if (usuario.getRol() == user_rol.JEFE_DE_OBRA
                && !parte.getPerfil().getId().equals(usuario.getId()))
            throw new ResponseStatusException(FORBIDDEN, "No puedes ver este parte");

        List<informe_linea_dto> lineas = parte.getObras().stream()
                .map(l -> new informe_linea_dto(
                        l.getObra().getNombre(),
                        l.getHoras_electricas(),
                        l.getHoras_mecanicas(),
                        Math.round(l.getPorcentaje_electrico() * 100.0) / 100.0,
                        Math.round(l.getPorcentaje_mecanico()  * 100.0) / 100.0))
                .toList();

        return new informe_jefe_dto(
                parte.getId(),
                parte.getDescripcion(),
                parte.getFechaInicio(),
                parte.getFechaFin(),
                parte.getTotalHorasLaborables(),
                lineas);
    }

    /*
     * Genera el resumen mensual de los partes de jefe.
     *
     * Recibe: UUID del usuario, anio y mes
     * Devuelve: un resumen_mensual_jefe_dto con:
     *   - Total de horas laborables del mes
     *   - Resumen de obras con horas y porcentajes
     *   - Lista de partes individuales del mes
     */
    public resumen_mensual_jefe_dto get_resumen_mensual(String sub, int anio, int mes) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        List<partes_jefe> partes;
        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            partes = parte_jefe_repo.findAllByMes(anio, mes);
        } else {
            partes = parte_jefe_repo.findByPerfilIdAndMes(usuario.getId(), anio, mes);
        }

        // ✅ Paso 1: acumular horas por obra sin calcular porcentajes aún
        Map<Long, resumen_obra_dto> obraMap = new LinkedHashMap<>();
        for (partes_jefe parte : partes) {
            for (partes_jefe_obra linea : parte.getObras()) {
                if (linea.getObra() == null) continue;
                Long obraId = linea.getObra().getId();
                resumen_obra_dto existente = obraMap.get(obraId);
                double hE = existente != null ? existente.horas_electricas() : 0.0;
                double hM = existente != null ? existente.horas_mecanicas() : 0.0;
                hE += linea.getHoras_electricas() != null ? linea.getHoras_electricas() : 0.0;
                hM += linea.getHoras_mecanicas() != null ? linea.getHoras_mecanicas() : 0.0;
                obraMap.put(obraId, new resumen_obra_dto(
                        linea.getObra().getNombre(), linea.getObra().getCodigo(),
                        hE, hM, 0.0, 0.0));
            }
        }

        // Paso 2: totalHoras = suma real de horas trabajadas ese mes
        double totalHoras = obraMap.values().stream()
                .mapToDouble(o -> o.horas_electricas() + o.horas_mecanicas())
                .sum();

        // Paso 3: recalcular porcentajes sobre el total real
        obraMap.replaceAll((id, o) -> {
            double pctE = totalHoras > 0 ? (o.horas_electricas() / totalHoras) * 100.0 : 0.0;
            double pctM = totalHoras > 0 ? (o.horas_mecanicas()  / totalHoras) * 100.0 : 0.0;
            return new resumen_obra_dto(
                    o.nombre_obra(), o.codigo_obra(),
                    o.horas_electricas(), o.horas_mecanicas(),
                    pctE,
                    pctM);
        });

        List<resumen_parte_dto> partesDto = partes.stream()
                .map(p -> new resumen_parte_dto(
                        p.getId(), p.getFechaInicio(), p.getFechaFin(),
                        p.getTotalHorasLaborables(), p.getDescripcion()))
                .toList();

        return new resumen_mensual_jefe_dto(
                anio, mes, totalHoras,
                new ArrayList<>(obraMap.values()), partesDto);
    }

    /*
     * Genera un informe combinado de todos los partes de jefe en un rango
     * de fechas, sumando las horas de todas las obras.
     *
     * Recibe: UUID del usuario, fecha inicio y fecha fin
     * Devuelve: informe_jefe_dto con los datos agregados del rango
     */
    public informe_jefe_dto generar_informe_rango(String sub, LocalDate desde, LocalDate hasta) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        List<partes_jefe> partes;
        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            partes = parte_jefe_repo.findByFechaInicioBetween(desde, hasta);
        } else {
            partes = parte_jefe_repo.findByPerfilIdAndFechaInicioBetween(usuario.getId(), desde, hasta);
        }

        double totalHoras = partes.stream()
                .mapToDouble(p -> p.getTotalHorasLaborables() != null ? p.getTotalHorasLaborables() : 0.0)
                .sum();

        Map<Long, informe_linea_dto> obraMap = new LinkedHashMap<>();
        for (partes_jefe parte : partes) {
            for (partes_jefe_obra linea : parte.getObras()) {
                if (linea.getObra() == null) continue;
                Long obraId = linea.getObra().getId();
                informe_linea_dto existente = obraMap.get(obraId);

                double hE = existente != null ? existente.horas_electricas() : 0.0;
                double hM = existente != null ? existente.horas_mecanicas() : 0.0;
                hE += linea.getHoras_electricas() != null ? linea.getHoras_electricas() : 0.0;
                hM += linea.getHoras_mecanicas() != null ? linea.getHoras_mecanicas() : 0.0;

                double pctE = totalHoras > 0 ? (hE / totalHoras) * 100.0 : 0.0;
                double pctM = totalHoras > 0 ? (hM / totalHoras) * 100.0 : 0.0;

                obraMap.put(obraId, new informe_linea_dto(
                        linea.getObra().getNombre(), hE, hM,
                        Math.round(pctE * 100.0) / 100.0,
                        Math.round(pctM * 100.0) / 100.0));
            }
        }

        return new informe_jefe_dto(
                null, null, desde, hasta, totalHoras,
                new ArrayList<>(obraMap.values()));
    }

    /*
     * Obtiene el resumen mensual agrupado por cada jefe de obra.
     * Devuelve una lista donde cada elemento es un jefe con sus obras.
     *
     * Recibe: anio y mes
     * Devuelve: lista de mapas con nombre del jefe, total horas y obras
     */
    public List<Map<String, Object>> get_resumen_mensual_por_usuario(int anio, int mes) {
        List<partes_jefe> partes = parte_jefe_repo.findAllByMes(anio, mes);

        Map<UUID, List<partes_jefe>> porUsuario = new LinkedHashMap<>();
        for (partes_jefe parte : partes) {
            UUID id = parte.getPerfil().getId();
            porUsuario.computeIfAbsent(id, k -> new ArrayList<>()).add(parte);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map.Entry<UUID, List<partes_jefe>> entry : porUsuario.entrySet()) {
            List<partes_jefe> partesUsuario = entry.getValue();
            perfil p = partesUsuario.get(0).getPerfil();

            //  Paso 1: acumular horas por obra sin calcular porcentajes aún
            Map<Long, resumen_obra_dto> obraMap = new LinkedHashMap<>();
            for (partes_jefe parte : partesUsuario) {
                for (partes_jefe_obra linea : parte.getObras()) {
                    if (linea.getObra() == null) continue;
                    Long obraId = linea.getObra().getId();
                    resumen_obra_dto existente = obraMap.get(obraId);
                    double hE = existente != null ? existente.horas_electricas() : 0.0;
                    double hM = existente != null ? existente.horas_mecanicas() : 0.0;
                    hE += linea.getHoras_electricas() != null ? linea.getHoras_electricas() : 0.0;
                    hM += linea.getHoras_mecanicas() != null ? linea.getHoras_mecanicas() : 0.0;
                    obraMap.put(obraId, new resumen_obra_dto(
                            linea.getObra().getNombre(), linea.getObra().getCodigo(),
                            hE, hM, 0.0, 0.0));
                }
            }

            //  Paso 2: totalHoras = suma real de horas trabajadas ese mes
            double totalHoras = obraMap.values().stream()
                    .mapToDouble(o -> o.horas_electricas() + o.horas_mecanicas())
                    .sum();

            // Paso 3: recalcular porcentajes sobre el total real
            obraMap.replaceAll((id, o) -> {
                double pctE = totalHoras > 0 ? (o.horas_electricas() / totalHoras) * 100.0 : 0.0;
                double pctM = totalHoras > 0 ? (o.horas_mecanicas()  / totalHoras) * 100.0 : 0.0;
                return new resumen_obra_dto(
                        o.nombre_obra(), o.codigo_obra(),
                        o.horas_electricas(), o.horas_mecanicas(),
                        Math.round(pctE * 100.0) / 100.0,
                        Math.round(pctM * 100.0) / 100.0);
            });

            Map<String, Object> usuarioData = new LinkedHashMap<>();
            usuarioData.put("nombre", (p.getName() + " " + p.getApellidos()).trim());
            usuarioData.put("total_horas_laborables", totalHoras);
            usuarioData.put("obras", new ArrayList<>(obraMap.values()));
            resultado.add(usuarioData);
        }
        return resultado;
    }

    // ─── EXCEL ────────────────────────────────────────────────────────────────

    /*
     * Genera un archivo Excel (.xlsx) con el resumen mensual de dedicación de
     * todos los jefes de obra para el año y mes indicados.
     *
     * Estructura del Excel:
     * - Una hoja por jefe de obra (nombre de hoja = nombre del jefe)
     * - Cabecera con nombre del jefe y total de horas
     * - Filas de obras con: nombre, código, horas eléctricas, % eléctrico,
     *   horas mecánicas, % mecánico
     * - Fila de totales al final de cada hoja
     *
     * Los valores numéricos se escriben como double directamente para preservar
     * todos los decimales sin redondear.
     */
    public byte[] generarXlsxResumenMensual(int anio, int mes) throws Exception {
        List<Map<String, Object>> usuarios = get_resumen_mensual_por_usuario(anio, mes);

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            // ── Estilos reutilizables ──
            org.apache.poi.xssf.usermodel.XSSFCellStyle estCabecera = wb.createCellStyle();
            estCabecera.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)0x15, (byte)0x65, (byte)0xC0}, null)); // azul oscuro
            estCabecera.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.xssf.usermodel.XSSFFont fCabecera = wb.createFont();
            fCabecera.setBold(true);
            fCabecera.setColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF}, null));
            estCabecera.setFont(fCabecera);
            estCabecera.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            org.apache.poi.xssf.usermodel.XSSFCellStyle estColHead = wb.createCellStyle();
            estColHead.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)0xE3,(byte)0xF2,(byte)0xFD}, null)); // azul pálido
            estColHead.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.xssf.usermodel.XSSFFont fColHead = wb.createFont();
            fColHead.setBold(true);
            estColHead.setFont(fColHead);

            org.apache.poi.xssf.usermodel.XSSFCellStyle estTotal = wb.createCellStyle();
            estTotal.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)0xE8,(byte)0xF5,(byte)0xE9}, null)); // verde pálido
            estTotal.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.xssf.usermodel.XSSFFont fTotal = wb.createFont();
            fTotal.setBold(true);
            estTotal.setFont(fTotal);

            org.apache.poi.xssf.usermodel.XSSFCellStyle estFilaPar = wb.createCellStyle();
            estFilaPar.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)0xFA,(byte)0xFA,(byte)0xFA}, null));
            estFilaPar.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // Formato numérico: todos los decimales que tenga el double
            org.apache.poi.ss.usermodel.DataFormat fmt = wb.createDataFormat();
            short fmtNum = fmt.getFormat("0.###############"); // hasta 15 decimales, sin ceros innecesarios

            org.apache.poi.xssf.usermodel.XSSFCellStyle estNum = wb.createCellStyle();
            estNum.setDataFormat(fmtNum);

            org.apache.poi.xssf.usermodel.XSSFCellStyle estNumPar = wb.createCellStyle();
            estNumPar.cloneStyleFrom(estFilaPar);
            estNumPar.setDataFormat(fmtNum);

            org.apache.poi.xssf.usermodel.XSSFCellStyle estNumTotal = wb.createCellStyle();
            estNumTotal.cloneStyleFrom(estTotal);
            estNumTotal.setDataFormat(fmtNum);

            for (Map<String, Object> u : usuarios) {
                String nombre = (String) u.getOrDefault("nombre", "Sin nombre");
                double totalHoras = u.get("total_horas_laborables") instanceof Number n
                        ? n.doubleValue() : 0.0;
                @SuppressWarnings("unchecked")
                List<resumen_obra_dto> obras = (List<resumen_obra_dto>) u.get("obras");

                // Nombre de hoja: máx 31 chars, sin caracteres prohibidos por Excel
                String nombreHoja = nombre.replaceAll("[\\\\/*?:\\[\\]]", "_");
                if (nombreHoja.length() > 31) nombreHoja = nombreHoja.substring(0, 31);

                org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(nombreHoja);

                // ── Fila 0: cabecera del jefe ──
                org.apache.poi.ss.usermodel.Row fila0 = sheet.createRow(0);
                ponerCelda(fila0, 0, "Jefe de obra", estCabecera);
                ponerCelda(fila0, 1, nombre,         estCabecera);
                ponerCelda(fila0, 2, "Total horas",  estCabecera);
                ponerCeldaNum(fila0, 3, totalHoras,  estCabecera); // total en la cabecera sin formato decimal especial
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

                // ── Fila 1: vacía ──
                sheet.createRow(1);

                // ── Fila 2: cabeceras de columnas ──
                org.apache.poi.ss.usermodel.Row fila2 = sheet.createRow(2);
                String[] cols = {"Obra","Código","Horas eléctricas","% Eléctrico","Horas mecánicas","% Mecánico"};
                for (int c = 0; c < cols.length; c++) ponerCelda(fila2, c, cols[c], estColHead);

                // ── Filas de obras ──
                double sumHE = 0, sumHM = 0;
                int filaIdx = 3;
                for (int i = 0; i < obras.size(); i++, filaIdx++) {
                    resumen_obra_dto o = obras.get(i);
                    double hE = o.horas_electricas()    != null ? o.horas_electricas()    : 0.0;
                    double hM = o.horas_mecanicas()     != null ? o.horas_mecanicas()     : 0.0;
                    double pE = o.porcentaje_electrico() != null ? o.porcentaje_electrico() : 0.0;
                    double pM = o.porcentaje_mecanico()  != null ? o.porcentaje_mecanico()  : 0.0;
                    sumHE += hE;
                    sumHM += hM;

                    boolean par = i % 2 == 0;
                    org.apache.poi.ss.usermodel.Row fila = sheet.createRow(filaIdx);
                    ponerCelda(fila, 0, o.nombre_obra() != null ? o.nombre_obra() : "--",  par ? estFilaPar : null);
                    ponerCelda(fila, 1, o.codigo_obra() != null ? o.codigo_obra() : "--",  par ? estFilaPar : null);
                    ponerCeldaNum(fila, 2, hE, par ? estNumPar : estNum);
                    ponerCeldaNum(fila, 3, pE, par ? estNumPar : estNum);
                    ponerCeldaNum(fila, 4, hM, par ? estNumPar : estNum);
                    ponerCeldaNum(fila, 5, pM, par ? estNumPar : estNum);
                }

                // ── Fila de totales ──
                double base = totalHoras > 0 ? totalHoras : 1.0;
                org.apache.poi.ss.usermodel.Row filaTot = sheet.createRow(filaIdx);
                ponerCelda(filaTot, 0, "TOTAL", estTotal);
                ponerCelda(filaTot, 1, "",      estTotal);
                ponerCeldaNum(filaTot, 2, sumHE,              estNumTotal);
                ponerCeldaNum(filaTot, 3, (sumHE / base) * 100, estNumTotal);
                ponerCeldaNum(filaTot, 4, sumHM,              estNumTotal);
                ponerCeldaNum(filaTot, 5, (sumHM / base) * 100, estNumTotal);

                // ── Anchos de columna ──
                sheet.setColumnWidth(0, 50 * 256);
                sheet.setColumnWidth(1, 14 * 256);
                sheet.setColumnWidth(2, 20 * 256);
                sheet.setColumnWidth(3, 16 * 256);
                sheet.setColumnWidth(4, 20 * 256);
                sheet.setColumnWidth(5, 16 * 256);
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    /* Escribe una celda de texto con estilo opcional. */
    private void ponerCelda(org.apache.poi.ss.usermodel.Row row, int col, String val,
                            org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(val);
        if (style != null) c.setCellStyle(style);
    }

    /* Escribe una celda numérica (double) con estilo. Preserva todos los decimales. */
    private void ponerCeldaNum(org.apache.poi.ss.usermodel.Row row, int col, double val,
                               org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(val);
        if (style != null) c.setCellStyle(style);
    }
}