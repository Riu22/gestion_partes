/*
 * SERVICIO: FechasPermitidasCleanupJob (Tarea de limpieza nocturna)
 *
 * Esta clase ejecuta una tarea programada automaticamente cada noche
 * a las 00:05 (hora de Madrid) para limpiar la tabla de fechas
 * habilitadas para edicion retroactiva.
 *
 * Cuando un administrador habilita una fecha pasada para que un
 * trabajador pueda editar un parte, esa habilitacion se guarda en
 * la tabla "fechas_permitidas". Una vez que el trabajador ya ha
 * registrado un parte completo (8+ horas) en esa fecha, la
 * habilitacion ya no es necesaria.
 *
 * Esta tarea se encarga de borrar automaticamente esos registros
 * que ya no son necesarios, manteniendo la tabla limpia.
 */
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

    /*
     * Metodo que se ejecuta automaticamente a las 00:05 cada dia.
     * Llama al repositorio para eliminar las fechas habilitadas que
     * ya tienen un parte completo (8+ horas) y que ademas son
     * fechas pasadas.
     *
     * No recibe nada. Devuelve void (el resultado se registra en log).
     * Si ocurre un error, se registra pero no se interrumpe el sistema.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Madrid")
    public void eliminarFechasConParteCompleto() {
        try {
            int eliminadas = fechaPermitidaRepo.eliminarFechasConParteCompleto();
            log.info("[Limpieza nocturna] Fechas permitidas eliminadas: {}", eliminadas);
        } catch (Exception e) {
            log.error("[Limpieza nocturna] Error al eliminar fechas permitidas", e);
        }
    }
}
