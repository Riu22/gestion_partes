package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "obras", schema = "public")
public class obra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String nombre;
    @Column(nullable = false)
    String ubicacion;
    @Column(nullable = false)
    String municipio;
    @Column(nullable = false)
    String poblacion;

    LocalDate fecha_inicio;
    boolean activa;

    public obra() {
    }

    public obra (String nombre, String direccion, String municipio, String poblacion){
        this.nombre = nombre;
        this.ubicacion = direccion;
        this.municipio = municipio;
        this.poblacion = poblacion;
        this.fecha_inicio = LocalDate.now();
        this.activa = true;
    }
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getMunicipio() {
        return municipio;
    }

    public void setMunicipio(String municipio) {
        this.municipio = municipio;
    }

    public String getPoblacion() {
        return poblacion;
    }

    public void setPoblacion(String poblacion) {
        this.poblacion = poblacion;
    }

    public LocalDate getFecha_inicio() {
        return fecha_inicio;
    }

    public void setFecha_inicio(LocalDate fecha_inicio) {
        this.fecha_inicio = fecha_inicio;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }
}
