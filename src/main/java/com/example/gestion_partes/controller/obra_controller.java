package com.example.gestion_partes.controller;

import com.example.gestion_partes.dto.obra_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.service.obra_service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("api/v1/obra")
public class obra_controller {
    @Autowired
    obra_service obra_service;

    @GetMapping()
    public ResponseEntity<List<obra>> get_all_obra(){
        return ResponseEntity.ok(obra_service.getAllObras());
    }

    @PostMapping
    public ResponseEntity<obra> create_obra(@RequestBody obra_dto new_obra){
        return ResponseEntity.ok(obra_service.create_obra(new_obra));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete_obra(@PathVariable String id){
        obra_service.delete_obra(Long.parseLong(id),null);
        return new ResponseEntity(HttpStatus.OK);
    }
}
