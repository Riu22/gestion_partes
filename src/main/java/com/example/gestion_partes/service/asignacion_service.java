package com.example.gestion_partes.service;

import com.example.gestion_partes.model.*;
import com.example.gestion_partes.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class asignacion_service {

    @Autowired
    perfil_repo perfil_repo;
    @Autowired
    obra_repo obra_repo;
    @Autowired
    asignacion_obra_repo asignacion_obra_repo;

    // Asigna un encargado o jefe de obra a una obra
    public asignacion_obra asignar_a_obra(UUID perfilId, Long obraId) {
        perfil perfil = perfil_repo.findById(perfilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
        obra obra = obra_repo.findById(obraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada"));

        if (perfil.getRol() == user_rol.ADMINISTRACION || perfil.getRol() == user_rol.GESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADMINISTRACION y GESTION no se asignan a obras, tienen visibilidad total");
        }
        if (perfil.getRol() == user_rol.OPERARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los operarios se asignan a un encargado, no directamente a una obra");
        }
        if (asignacion_obra_repo.existsByPerfilIdAndObraId(perfilId, obraId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este perfil ya está asignado a esta obra");
        }

        return asignacion_obra_repo.save(new asignacion_obra(perfil, obra));
    }

    // Asigna un operario a un encargado via jefe_directo_id
    public perfil asignar_operario_a_encargado(UUID operarioId, UUID encargadoId) {
        perfil operario = perfil_repo.findById(operarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operario no encontrado"));
        perfil encargado = perfil_repo.findById(encargadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encargado no encontrado"));

        if (operario.getRol() != user_rol.OPERARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil no tiene rol OPERARIO");
        }
        if (encargado.getRol() != user_rol.ENCARGADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil destino no tiene rol ENCARGADO");
        }

        operario.setJefeDirecto(encargado);
        return perfil_repo.save(operario);
    }

    // Asigna un encargado a un jefe de obra via jefe_directo_id
    public perfil asignar_encargado_a_jefe(UUID encargadoId, UUID jefeId) {
        perfil encargado = perfil_repo.findById(encargadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encargado no encontrado"));
        perfil jefe = perfil_repo.findById(jefeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jefe de obra no encontrado"));

        if (encargado.getRol() != user_rol.ENCARGADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil no tiene rol ENCARGADO");
        }
        if (jefe.getRol() != user_rol.JEFE_DE_OBRA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil destino no tiene rol JEFE_DE_OBRA");
        }

        encargado.setJefeDirecto(jefe);
        return perfil_repo.save(encargado);
    }

    // Ver todas las asignaciones de una obra
    public List<asignacion_obra> get_asignaciones_obra(Long obraId) {
        if (!obra_repo.existsById(obraId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        return asignacion_obra_repo.findByObraId(obraId);
    }

    // Ver todas las obras de un perfil
    public List<asignacion_obra> get_obras_de_perfil(UUID perfilId) {
        if (!perfil_repo.existsById(perfilId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado");
        }
        return asignacion_obra_repo.findByPerfilId(perfilId);
    }

    // Eliminar asignación de obra
    public void eliminar_asignacion_obra(Long asignacionId) {
        if (!asignacion_obra_repo.existsById(asignacionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada");
        }
        asignacion_obra_repo.deleteById(asignacionId);
    }
}