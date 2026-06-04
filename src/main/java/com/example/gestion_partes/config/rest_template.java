/* Configuración que expone un bean RestTemplate para que pueda ser inyectado en cualquier servicio.
   RestTemplate se usa para hacer peticiones HTTP a servicios externos (ej. Supabase Auth, APIs de terceros). */
package com.example.gestion_partes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class rest_template {
    @Bean
    public RestTemplate RestTemplate() {
        return new RestTemplate();
    }
}
