package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class contabilidad_service {

    @Autowired
    private partes_trabajo_repo partes_trabajo_repo;

    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partes_trabajo_repo.getResumenQuincena(desde, hasta);
    }

    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        return procesarLogicaDetalle(desde, hasta);
    }

    private List<Map<String, Object>> procesarLogicaDetalle(
            LocalDate desde, LocalDate hasta) {

        List<contabilidad_detalle_dto> datos =
                partes_trabajo_repo.getDetalleContabilidad(desde, hasta);

        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObra = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String codigoUser = d.getCodigo()      != null ? d.getCodigo()      : "000";
            String clave      = codigoUser + "|" + nombreObra;

            mapaAgrupado.computeIfAbsent(clave, k -> {
                String aps = d.getApellidos() != null ? d.getApellidos().toUpperCase() : "";
                String nom = d.getNombre()    != null ? d.getNombre()                  : "S/N";
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("codigo",           codigoUser);
                fila.put("operario",         operarioFull);
                fila.put("obra",             nombreObra);
                fila.put("grupo_profesional",
                        d.getGrupo_profesional() != null ? d.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",    new HashMap<LocalDate, Double>());
                fila.put("total_horas",      0.0);
                return fila;
            });

            @SuppressWarnings("unchecked")
            Map<LocalDate, Double> horasDia =
                    (Map<LocalDate, Double>) mapaAgrupado.get(clave).get("horas_por_dia");

            LocalDate fechaKey = d.getFecha();
            double horas = d.getHoras_totales() != null ? d.getHoras_totales() : 0.0;

            if (fechaKey != null) {
                horasDia.merge(fechaKey, horas, Double::sum);
            }

            mapaAgrupado.get(clave)
                    .merge("total_horas", horas, (a, b) -> (double) a + (double) b);
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}