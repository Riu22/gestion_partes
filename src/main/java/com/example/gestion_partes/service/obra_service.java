package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.obra_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.model.user_rol;
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
    public List<obra> getAllObras() {
        return obra_repo.findAll();
    }

    public void delete_obra(Long id, user_rol user_rol){
        if(user_rol != user_rol.ADMINISTRACION){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permisos para eliminar obras contacte con el administrador"
            );
        }
        obra_repo.deleteById(id);
    }
    public obra create_obra(obra_dto new_obra){
        obra_repo.save(new obra(
                new_obra.nombre(),
                new_obra.direccion(),
                new_obra.municipio(),
                new_obra.poblacion()
        ));
        return obra_repo.findAll().get(obra_repo.findAll().size()-1);
    }
}
