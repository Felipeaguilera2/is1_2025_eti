package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("cursadas")
public class Cursada extends Model {
    static {
        validatePresenceOf("estudiante_id", "materia_id", "periodo");
    }
}
