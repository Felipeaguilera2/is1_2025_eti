package com.is1.proyecto.models;

import java.util.List;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("materia")
public class Materia extends Model {

    public Integer getCodigo() {
        return getInteger("codigo_materia");
    }

    public String getNombre() {
        return getString("nombre");
    }

    public String getPlan() {
        return getString("plan_materia");
    }

    /**
     * Obtiene la lista de materias que son correlativas a esta.
     */
    public List<Materia> getCorrelativas() {
        return Materia.findBySQL(
            "SELECT m.* FROM materia m JOIN correlativas c ON m.id = c.correlativa_id WHERE c.materia_id = ?", 
            this.getId()
        );
    }

    /**
     * Elimina todas las correlatividades asociadas a esta materia.
     */
    public void borrarCorrelatividades() {
        org.javalite.activejdbc.Base.exec("DELETE FROM correlativas WHERE materia_id = ? OR correlativa_id = ?", this.getId(), this.getId());
    }

    /**
     * Asigna una nueva correlativa.
     */
    public void agregarCorrelativa(Integer correlativaId) {
        org.javalite.activejdbc.Base.exec("INSERT OR IGNORE INTO correlativas (materia_id, correlativa_id) VALUES (?, ?)", this.getId(), correlativaId);
    }
}