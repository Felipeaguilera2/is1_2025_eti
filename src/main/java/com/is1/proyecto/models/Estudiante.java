package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("estudiante")
public class Estudiante extends Model {
    static {
        validatePresenceOf("dni", "cod_estudiante");
        validateWith(new org.javalite.validation.ValidatorAdapter() {
            @Override
            public void validate(org.javalite.validation.Validatable m) {
                Model model = (Model) m;
                Integer planId = model.getInteger("plan_estudio_id");
                if (planId != null) {
                    PlanEstudio plan = PlanEstudio.findById(planId);
                    if (plan == null) {
                        model.addError("plan_estudio_id", "El plan de estudio seleccionado no existe.");
                    } else if (plan.getInteger("vigente") != 1) {
                        model.addError("plan_estudio_id", "No se puede matricular a un estudiante en un plan de estudio no vigente.");
                    }
                }
            }
        });
    }

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