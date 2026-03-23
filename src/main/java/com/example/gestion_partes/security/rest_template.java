package com.example.gestion_partes.security;

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
