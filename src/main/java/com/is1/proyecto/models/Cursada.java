package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("cursadas")
public class Cursada extends Model {

    // ActiveJDBC se encarga de la magia de los atributos dinámicamente
    static {
        validatePresenceOf("estudiante_id", "materia_id", "periodo");
    }
}

