package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.obra_repo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
@Service
public class partes_service {
    @Autowired
    partes_trabajo_repo partes_trabajo_repo;
    @Autowired
    perfil_repo perfil_repo;
    @Autowired
    obra_repo obra_repo;

    public partes_trabajo create_parte(partes_dto dto, String emailAutenticado) {
        perfil solicitante = perfil_repo.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        // Solo GESTION/ADMIN pueden crear partes para otros usuarios
        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        if (!esGestor && !solicitante.getId().equals(dto.id_perfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes crear partes para ti mismo");
        }

        obra obra = obra_repo.findById(dto.id_obra())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada"));

        perfil perfil = perfil_repo.findById(dto.id_perfil())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        partes_trabajo new_parte = new partes_trabajo();
        new_parte.setObra(obra);
        new_parte.setPerfil(perfil);
        new_parte.setFecha(dto.fecha());
        new_parte.setDescripcion(dto.descripcion());
        new_parte.setHoras_normales(dto.horas_normales());
        new_parte.setHoras_extra(dto.horas_extra());
        new_parte.setFirmado(false);

        return partes_trabajo_repo.save(new_parte);
    }

    public List<partes_trabajo> get_partes_jerarquico(String emailAutenticado) {
        perfil usuario = perfil_repo.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            return partes_trabajo_repo.findAll();
        }
        if (usuario.getRol() == user_rol.JEFE_DE_OBRA) {
            return partes_trabajo_repo.findPartesParaJefeObra(usuario.getId());
        }
        if (usuario.getRol() == user_rol.ENCARGADO) {
            return partes_trabajo_repo.findPartesParaEncargado(usuario.getId());
        }
        // OPERARIO: solo los suyos
        return partes_trabajo_repo.findByPerfilId(usuario.getId());
    }

    public void validar_parte(Long parteId, String emailRevisor) {
        partes_trabajo parte = partes_trabajo_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parte no encontrado"));

        perfil revisor = perfil_repo.findByEmail(emailRevisor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revisor no encontrado"));

        // ADMIN y GESTION validan todo
        if (revisor.getRol() == user_rol.ADMINISTRACION || revisor.getRol() == user_rol.GESTION) {
            marcarComoFirmado(parte);
            return;
        }

        perfil jefeDirecto = parte.getPerfil().getJefeDirecto();

        // ENCARGADO: es el jefe directo del operario
        if (jefeDirecto != null && jefeDirecto.getId().equals(revisor.getId())) {
            marcarComoFirmado(parte);
            return;
        }

        // JEFE DE OBRA: es el jefe del encargado
        if (jefeDirecto != null && jefeDirecto.getJefeDirecto() != null
                && jefeDirecto.getJefeDirecto().getId().equals(revisor.getId())) {
            marcarComoFirmado(parte);
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes autoridad sobre este operario para validar el parte");
    }

    public void delete_parte(Long parteId) {
        if (!partes_trabajo_repo.existsById(parteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parte no encontrado");
        }
        partes_trabajo_repo.deleteById(parteId);
    }

    private void marcarComoFirmado(partes_trabajo parte) {
        parte.setFirmado(true);
        partes_trabajo_repo.save(parte);
    }
}