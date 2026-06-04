/*
 * SERVICIO: obra_service (Gestion de obras/proyectos)
 *
 * Proporciona la logica de negocio para crear, modificar, eliminar
 * y consultar las obras de la empresa.
 *
 * Metodos:
 * - get_all_obras:        Devuelve todas las obras ordenadas alfabeticamente
 * - get_obras_activas:    Devuelve solo las obras que estan activas
 * - create_obra:          Crea una nueva obra con los datos recibidos
 * - delete_obra:          Elimina una obra por su ID
 * - update_obra:          Modifica los datos de una obra existente
 * - getObrasAsignadasAUsuario: IDs de las obras asignadas a un trabajador
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.obra_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.repo.asignacion_obra_repo;
import com.example.gestion_partes.repo.obra_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class obra_service {
    @Autowired
    obra_repo obra_repo;
    @Autowired
    asignacion_obra_repo asignaciones_obra_repo;

    /*
     * Obtiene todas las obras del sistema, ordenadas alfabeticamente.
     * Sin filtros: devuelve tanto activas como inactivas.
     */
    public List<obra> get_all_obras() {
        return obra_repo.findAllByOrderByNombreAsc();
    }

    /*
     * Obtiene solo las obras que estan marcadas como activas
     * (en ejecucion), ordenadas alfabeticamente.
     */
    public List<obra> get_obras_activas() {
        return obra_repo.findByActivaTrueOrderByNombreAsc();
    }

    /*
     * Crea una nueva obra en el sistema.
     * Recibe: un objeto obra_dto con los datos de la obra
     * Devuelve: la obra creada con su ID asignado
     * Si no se especifica si esta activa, se crea como activa (true)
     */
    public obra create_obra(obra_dto new_obra){
        obra obraEntity = new obra(
                new_obra.nombre(),
                new_obra.direccion(),
                new_obra.municipio(),
                new_obra.poblacion(),
                new_obra.codigo(),
                new_obra.activa() != null ? new_obra.activa() : true,
                new_obra.postventa() != null ? new_obra.postventa() : false
        );
        return obra_repo.save(obraEntity);
    }

    /*
     * Elimina una obra del sistema por su ID.
     * Recibe: el ID de la obra a eliminar
     * Lanza error si la obra no existe.
     */
    public void delete_obra(Long id){
        if (!obra_repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        obra_repo.deleteById(id);
    }

    /*
     * Modifica los datos de una obra existente.
     * Recibe: el ID de la obra y un objeto obra_dto con los campos a actualizar
     * Devuelve: la obra actualizada
     * Solo se actualizan los campos que vienen informados (no null)
     */
    public obra update_obra(Long id, obra_dto obraDatos) {
        obra obra_existente = obra_repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada"));

        if (obraDatos.nombre() != null)     obra_existente.setNombre(obraDatos.nombre());
        if (obraDatos.direccion() != null)  obra_existente.setUbicacion(obraDatos.direccion());
        if (obraDatos.municipio() != null)  obra_existente.setMunicipio(obraDatos.municipio());
        if (obraDatos.poblacion() != null)  obra_existente.setPoblacion(obraDatos.poblacion());
        if (obraDatos.codigo() != null)     obra_existente.setCodigo(obraDatos.codigo());
        if (obraDatos.activa() != null)     obra_existente.setActiva(obraDatos.activa());
        if(obraDatos.postventa() != null)   obra_existente.setPostventa(obraDatos.postventa());

        return obra_repo.save(obra_existente);
    }

    /*
     * Obtiene los IDs de las obras a las que esta asignado un trabajador.
     * Recibe: el UUID del trabajador
     * Devuelve: lista de IDs de obras
     */
    public List<Long> getObrasAsignadasAUsuario(UUID perfilId) {
        return asignaciones_obra_repo.findObraIdsByPerfilId(perfilId);
    }
}
