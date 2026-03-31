package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "perfiles", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class perfil {
    @Id
    UUID id;
    String email;
    @Column(name = "nombre_completo")
    String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", columnDefinition = "usuario_rol")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    user_rol rol;
    boolean activo = true;
    String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jefe_directo_id")
    @JsonIgnoreProperties({"jefeDirecto", "handler", "hibernateLazyInitializer"}) // <--- ESTO ES CLAVE
    private perfil jefeDirecto;

    public perfil(){}

    public perfil(String email,String name,user_rol rol,boolean activo){
        this.email = email;
        this.name = name;
        this.rol = rol;
        this.activo = activo;
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
}
