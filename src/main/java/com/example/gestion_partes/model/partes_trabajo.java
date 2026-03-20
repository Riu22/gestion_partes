package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Table(name = "partes_trabajo", schema = "public")
@Entity
public class partes_trabajo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "id_obra", nullable = false)
    obra obra;

    @ManyToOne
    @JoinColumn(name = "id_perfil", nullable = false)
    perfil perfil;

    LocalDate fecha;

    String descripcion;

    @Column(name = "horas_normales")
    Double horas_normales;

    @Column(name = "horas_extra")
    Double horas_extra;
    boolean firmado = false;
}
