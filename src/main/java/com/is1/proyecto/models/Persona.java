package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("person") // Esta anotación asocia explícitamente el modelo 'User' con la tabla 'person' en la DB.
public class Persona extends Model {

    public String getNombre() {
        return getString("nombre"); // Obtiene el valor de la columna 'nombre'
    }

    public void setNombre(String nombre) {
        set("nombre", nombre); // Establece el valor para la columna 'nombre'
    }

    public String getApellido() {
        return getString("apellido"); // Obtiene el valor de la columna 'apellido'
    }

    public void setApellido(String apellido) {
        set("apellido", apellido); // Establece el valor para la columna 'apellido'
    }
    public String getDni() {
        return getString("dni"); // Obtiene el valor de la columna 'dni'
    }

    public void setDni(int dni){
        set("dni", dni);    //Establece el valor para la columna 'dni'
    }

    public String getCorreo (){
        return getString("correo"); //Obtiene el valor de la columna 'correo'
    }

    public void setCorreo (String correo){  //Establece el valor para la columna 'correo'
        set("correo", correo);
    }
}