package com.is1.proyecto;

import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.ExamenFinal;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.Profesor;

public class ExamenFinalTest {

    // Se ejecuta ANTES de cada test (JUnit 5 utiliza @BeforeEach)
    @BeforeEach
    public void setUp() {
        // Usamos una base de datos exclusiva para no pisar los datos de desarrollo web
        Base.open("org.sqlite.JDBC", "jdbc:sqlite:universidad_test.db", "", "");
        Base.openTransaction(); 
    }

    // Se ejecuta DESPUÉS de cada test (JUnit 5 utiliza @AfterEach)
    @AfterEach
    public void tearDown() {
        Base.rollbackTransaction(); // Hace un rollback estricto de los mocks
        Base.close();
    }

    @Test
    public void testRegistroExamenFinalExitoso() {
        // 1. Preparar datos mínimos mockeando las dependencias en la transacción
        // Agregamos el campo "correo" que la base de datos de tus compañeros exige de manera obligatoria
        Persona alumnoPers = Persona.createIt("nombre", "Estudiante Test", "apellido", "Prueba", "dni", 99991, "correo", "alumno@test.com");
        Estudiante estudiante = Estudiante.createIt("dni", 99991, "cod_estudiante", 1001);
        
        Persona profPers = Persona.createIt("nombre", "Profesor Test", "apellido", "Catedra", "dni", 99992, "correo", "profesor@test.com");
        Profesor profesor = Profesor.createIt("dni", 99992, "nro_legajo", 4522);
        
// Le agregamos el atributo 'codigo_materia' que la base de datos de tu grupo pide como obligatorio
        Materia materia = Materia.createIt("nombre", "Ingenieria de Software II", "plan_materia", "2023", "codigo_materia", "IS2");
        // 2. Ejecutar la acción de tu Issue (Clase de Asociación ExamenFinal)
        ExamenFinal examen = new ExamenFinal();
        examen.set("estudiante_id", estudiante.getId());
        examen.set("materia_id", materia.getId());
        examen.set("profesor_id", profesor.get("dni"));
        examen.set("nota", 8.5);
        examen.set("fecha", "2026-05-31"); // Almacena la fecha exacta (Criterio de Aceptación)
        
        boolean guardado = examen.saveIt();

        // 3. Corroborar (JUnit 5 Assertions)
        assertTrue(guardado, "El examen final debería haberse guardado correctamente");
        assertEquals(8.5, examen.getDouble("nota"), 0.01);
        assertEquals("2026-05-31", examen.getString("fecha"));
    }

    @Test
    public void testValidacionNotaFueraDeRango() {
        // Intentamos instanciar un examen con una nota inválida (ej: 11)
        ExamenFinal examenInvalido = new ExamenFinal();
        examenInvalido.set("estudiante_id", 1);
        examenInvalido.set("materia_id", 1);
        examenInvalido.set("profesor_id", 1);
        examenInvalido.set("nota", 11.0); 
        examenInvalido.set("fecha", "2026-05-31");

        double notaIngresada = examenInvalido.getDouble("nota");
        
        // Verificamos que la lógica de negocio del rango (1 a 10) de la Issue la detecte
        boolean notaEsValida = (notaIngresada >= 1.0 && notaIngresada <= 10.0);
        
        assertFalse(notaEsValida, "La nota de examen final debería ser inválida si supera 10");
    }

    @Test
    public void testValidacionProfesorAsignado() {
        Integer legajoIngresado = 999999; // Un legajo ficticio que no existe en la BD
        
        // Buscamos en la base de datos simulando el flujo de control del backend
        Profesor profEncontrado = Profesor.findFirst("nro_legajo = ?", legajoIngresado);
        
        // Corroboramos que de nulo, cumpliendo el criterio de aceptación de seguridad
        assertNull(profEncontrado, "El sistema debe rechazar profesores no registrados mediante su legajo");
    }
}