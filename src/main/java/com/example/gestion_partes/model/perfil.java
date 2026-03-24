package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "perfiles", schema = "public")
public class perfil {
    @Id
    UUID id;
    String email;
    @Column(name = "nombre_completo")
    String name;
    @Enumerated(EnumType.STRING)
    user_rol rol;
    boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jefe_directo_id")
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
}
