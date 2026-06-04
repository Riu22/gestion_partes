/* Controlador simple de prueba para verificar que la aplicación responde correctamente.
   Endpoint público (sin autenticación) que devuelve un mensaje de texto. */
package com.example.gestion_partes.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/prueba")
public class prueba_controller {
    /* GET /hola: devuelve un mensaje de texto confirmando que el endpoint funciona. */
    @GetMapping("/hola")
    public ResponseEntity<String> hola() {
        return ResponseEntity.ok("Hola, esta es una prueba de endpoint");
    }
}
