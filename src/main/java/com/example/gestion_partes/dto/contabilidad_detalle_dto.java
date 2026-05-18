package com.example.gestion_partes.dto;

import java.time.LocalDate;

public interface contabilidad_detalle_dto {
    String getCodigo();
    String getNombre();
    String getApellidos();
    String getGrupo_profesional();
    String getObra_nombre();
    LocalDate getFecha();
    Double getHoras_totales();
    String getEspecialidad();
}