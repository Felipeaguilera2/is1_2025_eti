package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("profesor") // Esta anotación asocia explícitamente el modelo 'User' con la tabla 'person' en la DB.
public class Profesor extends Model {

    public String getDni ()
    {
        return getString("dni");
    }

    public void setDni(int dni)
    {
        set("dni",dni);
    }

    public String getLegajo ()
    {
        return getString("nro_legajo");
    }

    public void setLegajo (int legajo)
    { 
        set("nro_legajo", legajo);
    }
}