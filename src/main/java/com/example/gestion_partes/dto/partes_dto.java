    package com.example.gestion_partes.dto;

    import com.example.gestion_partes.model.especialidad;
    import com.fasterxml.jackson.annotation.JsonFormat;
    import com.fasterxml.jackson.annotation.JsonProperty;
    import jakarta.validation.constraints.NotNull;
    import jakarta.validation.constraints.PastOrPresent;

    import java.time.LocalDate;
    import java.util.UUID;

    public record partes_dto(
            Long id_obra,
            UUID id_perfil,
            @NotNull @PastOrPresent @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fecha,
            String descripcion,
            Double horas_normales,
            Double horas_extra,
            especialidad especialidad,
            String firma_base64,
            String nombre_firmado,
            @JsonProperty("trabajos_extra") String trabajo_extra
    ) {}
