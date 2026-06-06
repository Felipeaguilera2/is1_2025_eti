package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("docente_materia")
public class DocenteMateria extends Model {
    static {
        validatePresenceOf("profesor_dni", "materia_id", "rol", "periodo");
    }
}
