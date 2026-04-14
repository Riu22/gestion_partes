package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.obra_porcentaje_dto;
import com.example.gestion_partes.dto.partes_jefe_dto;
import com.example.gestion_partes.model.*;
import com.example.gestion_partes.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class parte_jefe_service {

    @Autowired parte_jefe_repo parte_jefe_repo;
    @Autowired partes_jefe_obra_repo parte_jefe_obra_repo;
    @Autowired perfil_repo perfil_repo;
    @Autowired obra_repo obra_repo;

    @Transactional
    public partes_jefe create_parte_jefe(partes_jefe_dto partes_jefe_dto, String sub) {
        perfil jefe = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (jefe.getRol() != user_rol.JEFE_DE_OBRA) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo los jefes de obra pueden crear este tipo de parte");
        }

        // Validar que los porcentajes sumen exactamente 100
        double total = partes_jefe_dto.obras().stream()
                .mapToDouble(obra_porcentaje_dto::porcentaje)
                .sum();
        if (Math.abs(total - 100.0) > 0.01) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los porcentajes deben sumar 100%. Suma actual: " + total + "%");
        }

        // Crear el parte — fecha automática
        partes_jefe nuevo = new partes_jefe();
        nuevo.setPerfil(jefe);
        nuevo.setDescripcion(partes_jefe_dto.descripcion());
        partes_jefe saved = parte_jefe_repo.save(nuevo);

        // Crear las líneas de porcentaje por obra
        for (obra_porcentaje_dto lineaDto : partes_jefe_dto.obras()) {
            obra obra = obra_repo.findById(lineaDto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Obra no encontrada: " + lineaDto.id_obra()));
            partes_jefe_obra linea = new partes_jefe_obra(saved, obra, lineaDto.porcentaje());
            parte_jefe_obra_repo.save(linea);
        }

        return parte_jefe_repo.findById(saved.getId()).orElseThrow();
    }

    public List<partes_jefe> get_partes_jefe(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

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
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));
        perfil revisor = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Revisor no encontrado"));

        if (revisor.getRol() == user_rol.ADMINISTRACION
                || revisor.getRol() == user_rol.GESTION) {
            parte_jefe_repo.save(parte);
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo GESTION o ADMINISTRACION pueden validar partes de jefes de obra");
    }

    public void delete_parte_jefe(Long parteId) {
        if (!parte_jefe_repo.existsById(parteId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Parte no encontrado");
        }
        parte_jefe_repo.deleteById(parteId);
    }
}