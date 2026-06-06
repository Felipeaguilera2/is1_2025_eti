package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("plan_materias")
public class PlanMateria extends Model {
    static {
        validatePresenceOf("plan_estudio_id", "materia_id", "anio_cursado", "cuatrimestre");
    }
}
