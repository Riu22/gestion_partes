package com.example.gestion_partes.service;

import com.example.gestion_partes.repo.FechaPermitidaRepo;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FechasPermitidasCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FechasPermitidasCleanupJob.class);

    @Autowired
    private FechaPermitidaRepo fechaPermitidaRepo;

    //@Scheduled(cron = "0 5 0 * * *", zone = "Europe/Madrid") // cada día a las 00:05
    @Scheduled(fixedDelay = 5000)
    public void eliminarFechasConParteCompleto() {
        try {
            int eliminadas = fechaPermitidaRepo.eliminarFechasConParteCompleto();
            log.info("[Limpieza nocturna] Fechas permitidas eliminadas: {}", eliminadas);
        } catch (Exception e) {
            log.error("[Limpieza nocturna] Error al eliminar fechas permitidas", e);
        }
    }
}
