/* Clase de entrada principal de la aplicación Spring Boot.
   Inicia la aplicación y habilita la programación de tareas periódicas (@EnableScheduling)
   para el job de limpieza de fechas permitidas (FechasPermitidasCleanupJob). */
package com.example.gestion_partes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestionPartesApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionPartesApplication.class, args);
	}

}
