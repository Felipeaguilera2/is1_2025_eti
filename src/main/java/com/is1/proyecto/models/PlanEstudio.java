package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("planes_estudio")
public class PlanEstudio extends Model {
    static {
        validatePresenceOf("nombre", "codigo", "carrera_id");
    }
}