/* Controlador REST que expone la versión actual de la aplicación y la URL de descarga del APK.
   El frontend usa este endpoint para comprobar si hay una versión más reciente disponible. */
package com.example.gestion_partes.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/version")
public class update_controller {

    /* Número de versión actual de la app (configurado en application.properties). */
    @Value("${app.version.actual}")
    private String versionActual;

    /* URL de descarga del archivo APK (configurado en application.properties). */
    @Value("${app.apk.url}")
    private String apkUrl;

    /* GET /: devuelve un mapa con la versión actual y la URL de descarga. */
    @GetMapping
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of(
                "version", versionActual,
                "url", apkUrl
        ));
    }
}
