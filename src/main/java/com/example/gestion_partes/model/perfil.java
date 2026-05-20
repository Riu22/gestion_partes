package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "perfiles", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class perfil {
    @Id
    UUID id;
    String email;
    @Column(name = "nombre")
    String name;
    @Column(name = "apellidos")
    String apellidos;
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", columnDefinition = "usuario_rol")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    user_rol rol;
    boolean activo = true;
    String codigo;
    @Column(name = "postventa")
    private Boolean postventa = false;
    @Column(name = "especialidad")
    @Enumerated(EnumType.STRING)
    private especialidad especialidad;

    @Column(name = "grupo_profesional")
    String grupo_profesional;

    @Column(name = "creado_el", insertable = true, updatable = true)
    private java.time.OffsetDateTime creadoEl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jefe_directo_id")
    @JsonIgnore
    private perfil jefeDirecto;

    public perfil() {}

    public perfil(String email, String name, user_rol rol, boolean activo) {
        this.email = email;
        this.name = name;
        this.rol = rol;
        this.activo = activo;
    }

    public UUID getJefeDirectoId() {
        return jefeDirecto != null ? jefeDirecto.getId() : null;
    }

    public perfil getJefeDirecto() {
        return jefeDirecto;
    }

    public void setJefeDirecto(perfil jefeDirecto) {
        this.jefeDirecto = jefeDirecto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public user_rol getRol() {
        return rol;
    }

    public void setRol(user_rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Boolean getPostventa() {
        return postventa;
    }

    public void setPostventa(Boolean postventa) {
        this.postventa = postventa;
    }

    public especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(especialidad especialidad) {
        this.especialidad = especialidad;
    }

    public String getGrupo_profesional() {
        return grupo_profesional;
    }

    public void setGrupo_profesional(String grupo_profesional) {
        this.grupo_profesional = grupo_profesional;
    }

    public OffsetDateTime getCreadoEl() {
        return creadoEl;
    }

    public void setCreadoEl(OffsetDateTime creadoEl) {
        this.creadoEl = creadoEl;
    }
}