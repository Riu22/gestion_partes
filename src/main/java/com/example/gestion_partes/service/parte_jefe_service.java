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
    @Autowired
    calendario_laboral_helper calendarioHelper;

    @Transactional
    public partes_jefe create_parte_jefe(partes_jefe_dto dto, String sub) {

        perfil jefe = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        if (jefe.getRol() != user_rol.JEFE_DE_OBRA)
            throw new ResponseStatusException(FORBIDDEN, "Solo jefes de obra pueden crear este parte");

        // Calcular días laborables del período × 8h
        double horasTotales = calendarioHelper.calcularHorasLaborales(
                dto.fecha_inicio(), dto.fecha_fin(), 8.0);

        if (horasTotales <= 0)
            throw new ResponseStatusException(BAD_REQUEST, "El período no contiene días laborables");

        // Validar que la suma de horas no supere el total disponible
        double sumaHoras = dto.obras().stream()
                .mapToDouble(o -> o.horas_electricas() + o.horas_mecanicas())
                .sum();
        if (sumaHoras > horasTotales + 0.01)
            throw new ResponseStatusException(BAD_REQUEST,
                    "Las horas totales (" + sumaHoras + "h) superan las horas laborables del período (" + horasTotales + "h)");

        // Crear el parte
        partes_jefe nuevo = new partes_jefe();
        nuevo.setPerfil(jefe);
        nuevo.setDescripcion(dto.descripcion());
        nuevo.setFechaInicio(dto.fecha_inicio());
        nuevo.setFechaFin(dto.fecha_fin());
        nuevo.setTotalHorasLaborables(horasTotales);
        partes_jefe saved = parte_jefe_repo.save(nuevo);

        // Crear líneas por obra con porcentajes calculados
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

    public List<partes_jefe> get_partes_jefe(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.ADMINISTRACION
                || usuario.getRol() == user_rol.GESTION) {
            return parte_jefe_repo.findAll();
        }

        // Jefe de obra ve los suyos propios
        if (usuario.getRol() == user_rol.JEFE_DE_OBRA) {
            return parte_jefe_repo.findByPerfilId(usuario.getId());
        }

        return List.of();
    }

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

    public void delete_parte_jefe(Long parteId) {
        if (!parte_jefe_repo.existsById(parteId)) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Parte no encontrado");
        }
        parte_jefe_repo.deleteById(parteId);
    }

    public informe_jefe_dto generar_informe(Long parteId, String sub) {
        partes_jefe parte = parte_jefe_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Parte no encontrado"));

        // Jefe solo ve sus propios partes
        perfil usuario = perfil_repo.findById(UUID.fromString(sub)).orElseThrow();
        if (usuario.getRol() == user_rol.JEFE_DE_OBRA
                && !parte.getPerfil().getId().equals(usuario.getId()))
            throw new ResponseStatusException(FORBIDDEN, "No puedes ver este parte");

        List<informe_linea_dto> lineas = parte.getObras().stream()
                .map(l -> new informe_linea_dto(
                        l.getObra().getNombre(),
                        l.getHoras_electricas(),
                        l.getHoras_mecanicas(),
                        // Redondear a 2 decimales para el informe
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

    public resumen_mensual_jefe_dto get_resumen_mensual(String sub, int anio, int mes) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        List<partes_jefe> partes;
        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            partes = parte_jefe_repo.findAllByMes(anio, mes);
        } else {
            partes = parte_jefe_repo.findByPerfilIdAndMes(usuario.getId(), anio, mes);
        }

        // Sumar horas totales del mes
        double totalHoras = partes.stream()
                .mapToDouble(p -> p.getTotalHorasLaborables() != null ? p.getTotalHorasLaborables() : 0.0)
                .sum();

        // Agregar por obra
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

                double pctE = totalHoras > 0 ? (hE / totalHoras) * 100.0 : 0.0;
                double pctM = totalHoras > 0 ? (hM / totalHoras) * 100.0 : 0.0;

                obraMap.put(obraId, new resumen_obra_dto(
                        linea.getObra().getNombre(),
                        linea.getObra().getCodigo(),
                        hE, hM,
                        Math.round(pctE * 100.0) / 100.0,
                        Math.round(pctM * 100.0) / 100.0
                ));
            }
        }

        // Lista partes individuales
        List<resumen_parte_dto> partesDto = partes.stream()
                .map(p -> new resumen_parte_dto(
                        p.getId(),
                        p.getFechaInicio(),
                        p.getFechaFin(),
                        p.getTotalHorasLaborables(),
                        p.getDescripcion()
                ))
                .toList();

        return new resumen_mensual_jefe_dto(
                anio, mes, totalHoras,
                new ArrayList<>(obraMap.values()),
                partesDto
        );
    }

    public informe_jefe_dto generar_informe_rango(String sub, LocalDate desde, LocalDate hasta) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Perfil no encontrado"));

        List<partes_jefe> partes;
        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            partes = parte_jefe_repo.findByFechaInicioBetween(desde, hasta);
        } else {
            partes = parte_jefe_repo.findByPerfilIdAndFechaInicioBetween(usuario.getId(), desde, hasta);
        }

        // Sumar horas totales del rango
        double totalHoras = partes.stream()
                .mapToDouble(p -> p.getTotalHorasLaborables() != null ? p.getTotalHorasLaborables() : 0.0)
                .sum();

        // Agregar por obra
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
                        linea.getObra().getNombre(),
                        hE, hM,
                        Math.round(pctE * 100.0) / 100.0,
                        Math.round(pctM * 100.0) / 100.0
                ));
            }
        }

        return new informe_jefe_dto(
                null,
                null,
                desde,
                hasta,
                totalHoras,
                new ArrayList<>(obraMap.values())
        );
    }

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

            double totalHoras = partesUsuario.stream()
                    .mapToDouble(x -> x.getTotalHorasLaborables() != null
                            ? x.getTotalHorasLaborables() : 0.0)
                    .sum();

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
                    double pctE = totalHoras > 0 ? (hE / totalHoras) * 100.0 : 0.0;
                    double pctM = totalHoras > 0 ? (hM / totalHoras) * 100.0 : 0.0;
                    obraMap.put(obraId, new resumen_obra_dto(
                            linea.getObra().getNombre(),
                            linea.getObra().getCodigo(),
                            hE, hM,
                            Math.round(pctE * 100.0) / 100.0,
                            Math.round(pctM * 100.0) / 100.0));
                }
            }

            Map<String, Object> usuarioData = new LinkedHashMap<>();
            usuarioData.put("nombre", (p.getName() + " " + p.getApellidos()).trim());
            usuarioData.put("total_horas_laborables", totalHoras);
            usuarioData.put("obras", new ArrayList<>(obraMap.values()));
            resultado.add(usuarioData);
        }
        return resultado;
    }
}