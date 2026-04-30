package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.obra_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.repo.obra_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class obra_service {
    @Autowired
    obra_repo obra_repo;
    public List<obra> get_all_obras() {
        return obra_repo.findAllByOrderByNombreAsc();
    }
    public List<obra> get_obras_activas() {
        return obra_repo.findByActivaTrueOrderByNombreAsc();
    }

    public obra create_obra(obra_dto new_obra){
        obra obraEntity = new obra(
                new_obra.nombre(),
                new_obra.direccion(),
                new_obra.municipio(),
                new_obra.poblacion(),
                new_obra.codigo(),
                new_obra.activa() != null ? new_obra.activa() : true
        );
        return obra_repo.save(obraEntity);
    }

    public void delete_obra(Long id){

        if (!obra_repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        obra_repo.deleteById(id);
    }

    public obra update_obra(Long id, obra_dto obraDatos) {
        obra obra_existente = obra_repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada"));

        if (obraDatos.nombre() != null) {
            obra_existente.setNombre(obraDatos.nombre());
        }
        if (obraDatos.direccion() != null) {
            obra_existente.setUbicacion(obraDatos.direccion());
        }
        if (obraDatos.municipio() != null) {
            obra_existente.setMunicipio(obraDatos.municipio());
        }
        if (obraDatos.poblacion() != null) {
            obra_existente.setPoblacion(obraDatos.poblacion());
        }
        if (obraDatos.codigo() != null) {
            obra_existente.setCodigo(obraDatos.codigo());
        }
        if(obraDatos.activa() != null){
            obra_existente.setActiva(obraDatos.activa());
        }

        return obra_repo.save(obra_existente);
    }
}
