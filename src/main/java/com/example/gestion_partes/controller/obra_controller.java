package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.obra_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.service.obra_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/obra")
public class obra_controller {
    @Autowired
    obra_service obra_service;

    @GetMapping()
    public ResponseEntity<List<obra>> get_all_obra(){
        return ResponseEntity.ok(obra_service.get_all_obras());
    }

    @GetMapping("/activas")
    ResponseEntity<List<obra>> get_obra_activas(){
        return ResponseEntity.ok(obra_service.get_obras_activas());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<?> create_obra(@RequestBody obra_dto new_obra){
        try {
            obra createdObra = obra_service.create_obra(new_obra);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdObra);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error al crear la obra");
        }
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION')")
    public ResponseEntity<?> delete_obra(@PathVariable Long id){
        obra_service.delete_obra(id);
        return new ResponseEntity(HttpStatus.OK);
    }
    @PutMapping("/update_obra/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRACION','GESTION')")
    public ResponseEntity<obra> update_obra(@PathVariable Long id, @RequestBody obra_dto obra_datos){
        return ResponseEntity.ok(obra_service.update_obra(id, obra_datos));
    }
}
