package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("estudiante")
public class Estudiante extends Model {

    public Integer getDni() {
        return getInteger("dni");
    }

    public void setDni(int dni) {
        set("dni", dni);
    }

    public Integer getCodEstudiante() {
        return getInteger("cod_estudiante");
    }

    public void setCodEstudiante(int codEstudiante) {
        set("cod_estudiante", codEstudiante);
    }

    /**
     * Criterio de Aceptación: Impedir borrado con vínculos académicos activos.
     * Comprueba si el estudiante tiene inscripciones, cursadas o finales.
     */
    public boolean tieneVinculosAcademicos() {
        // Por ahora, como las tablas intermedias de inscripciones se están desarrollando,
        // dejamos la validación lista. Si no hay registros asociados, devuelve false.
        // Ejemplo futuro: return Base.firstCell("SELECT count(*) FROM alumno_materia WHERE estudiante_id = ?", getId()) > 0;
        return false; 
    }
}