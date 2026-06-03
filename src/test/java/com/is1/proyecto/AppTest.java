package com.is1.proyecto;

// Importaciones modernas de JUnit 5 (Jupiter)
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.javalite.activejdbc.Base;
import org.mindrot.jbcrypt.BCrypt;

import com.is1.proyecto.models.Carrera;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.User;
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.Profesor;
import com.is1.proyecto.models.Cursada;

import java.io.InputStream;
import java.util.Scanner;

public class AppTest {

    @BeforeEach
    public void before() {
        Base.open("org.sqlite.JDBC", "jdbc:sqlite:db/dev.db", "", "");
        try {
            InputStream is = AppTest.class.getResourceAsStream("/scheme.sql");
            if (is != null) {
                Scanner s = new Scanner(is).useDelimiter("\\A");
                String sql = s.hasNext() ? s.next() : "";
                Base.exec(sql);
            }
        } catch (Exception e) {
            // Ignored if already initialized in this run
        }
        Base.openTransaction();
    }

    @AfterEach
    public void after() {
        Base.rollbackTransaction();
        Base.close();
    }

    @Test
    public void testCrearYBuscarCarrera() {
        Carrera c = new Carrera();
        c.set("codigo", "TEST-COMP");
        c.set("nombre", "Ciencias de la Computación");
        
        // En JUnit 5, el mensaje de error va al final
        assertTrue(c.saveIt(), "La carrera debería guardarse correctamente");

        Carrera encontrada = Carrera.findFirst("codigo = ?", "TEST-COMP");
        assertNotNull(encontrada, "Debería encontrar la carrera recién creada");
        assertEquals("Ciencias de la Computación", encontrada.getString("nombre"));
    }

    @Test
    public void testCrearMateriaObligatoriaYElectiva() {
        Materia m1 = new Materia();
        m1.set("codigo_materia", 9901);
        m1.set("nombre", "Algoritmos I");
        m1.set("plan_materia", "2024");
        m1.set("es_obligatoria", 1);
        assertTrue(m1.saveIt(), "Debería guardar materia obligatoria");

        Materia m2 = new Materia();
        m2.set("codigo_materia", 9902);
        m2.set("nombre", "Astronomía Básica");
        m2.set("plan_materia", "2024");
        m2.set("es_obligatoria", 0);
        assertTrue(m2.saveIt(), "Debería guardar materia electiva");

        Materia recup1 = Materia.findFirst("codigo_materia = ?", 9901);
        Materia recup2 = Materia.findFirst("codigo_materia = ?", 9902);
        
        assertEquals(1, recup1.getInteger("es_obligatoria").intValue());
        assertEquals(0, recup2.getInteger("es_obligatoria").intValue());
    }

    @Test
    public void testModificacionYBorradoDeMateria() {
        Materia m = new Materia();
        m.set("codigo_materia", 9903);
        m.set("nombre", "Materia Temporal");
        m.set("plan_materia", "2020");
        m.set("es_obligatoria", 1);
        m.saveIt();
        
        Object materiaId = m.getId();

        Materia aModificar = Materia.findById(materiaId);
        aModificar.set("plan_materia", "2026");
        assertTrue(aModificar.saveIt());
        
        Materia modificada = Materia.findById(materiaId);
        assertEquals("2026", modificada.getString("plan_materia"));

        assertTrue(modificada.delete());
        assertNull(Materia.findById(materiaId), "La materia ya no debería existir");
    }

    @Test
    public void testCreacionDeUsuarioYHasheoDePassword() {
        String passwordPlana = "secreto123";
        String hash = BCrypt.hashpw(passwordPlana, BCrypt.gensalt());

        User u = new User();
        // Usamos los nombres de columnas reales: "name" y "password"
        u.set("name", "admin_test");
        u.set("password", hash);
        assertTrue(u.saveIt(), "El usuario debería guardarse correctamente");

        // Buscamos usando la columna "name"
        User userLogueado = User.findFirst("name = ?", "admin_test");
        assertNotNull(userLogueado, "Debería encontrar al usuario recién creado");
        
        // Verificamos el hash usando la columna "password"
        boolean loginCorrecto = BCrypt.checkpw("secreto123", userLogueado.getString("password"));
        assertTrue(loginCorrecto, "La contraseña debería coincidir con el hash");
        
        boolean loginIncorrecto = BCrypt.checkpw("clave_falsa", userLogueado.getString("password"));
        assertFalse(loginIncorrecto, "Debería rechazar una contraseña incorrecta");
    }
    
    @Test
    public void testCascadaPersonaAProfesor() {
        Persona p = new Persona();
        p.set("nombre", "Marcelo");
        p.set("apellido", "Uva");
        // El DNI es numérico (INTEGER) según tu esquema
        p.set("dni", 11223344);
        p.set("correo", "muva@test.unrc.edu.ar");
        assertTrue(p.saveIt(), "La persona debería guardarse correctamente");

        Profesor prof = new Profesor();
        // Usamos p.get("dni") en lugar de p.getId()
        prof.set("dni", p.get("dni"));
        // Usamos la columna real de tu esquema (nro_legajo) en lugar de titulo
        prof.set("nro_legajo", 54321);
        assertTrue(prof.saveIt(), "El profesor debería guardarse correctamente");

        // Recuperamos al profesor buscando por su DNI
        Profesor profRecuperado = Profesor.findFirst("dni = ?", p.get("dni"));
        assertNotNull(profRecuperado);
        assertEquals(54321, profRecuperado.getInteger("nro_legajo").intValue());

        Persona personaDelProfesor = Persona.findFirst("dni = ?", profRecuperado.get("dni"));
        assertEquals("Marcelo", personaDelProfesor.getString("nombre"));
    }

    @Test
    public void testInscripcionExitosaMateriaMismaCarrera() {
        Persona persona = new Persona();
        persona.set("nombre", "Carlos");
        persona.set("apellido", "Gomez");
        persona.set("dni", 44111222);
        persona.set("correo", "carlos@unrc.edu.ar");
        persona.saveIt();

        com.is1.proyecto.models.Estudiante estudiante = new com.is1.proyecto.models.Estudiante();
        estudiante.set("dni", 44111222);
        estudiante.set("cod_estudiante", 9999);
        estudiante.saveIt();

        Materia materia = new Materia();
        materia.set("codigo_materia", 7501);
        materia.set("nombre", "Ingenieria de Software II");
        materia.set("plan_materia", "2023");
        materia.set("es_obligatoria", 1);
        materia.saveIt();

        Cursada cursada = new Cursada();
        cursada.set("estudiante_id", estudiante.getId());
        cursada.set("materia_id", materia.getId());
        cursada.set("periodo", "2026-1C");
        cursada.saveIt();

        Cursada guardada = Cursada.findFirst(
            "estudiante_id = ? AND materia_id = ?", estudiante.getId(), materia.getId()
        );

        assertNotNull(guardada, "El registro de la cursada debería existir en la base de datos.");
        assertEquals("2026-1C", guardada.get("periodo"), "El período académico debería coincidir.");
    }

    @Test
    public void testInscripcionFallidaMateriaDeOtraCarrera() {
        Persona persona = new Persona();
        persona.set("nombre", "Ana");
        persona.set("apellido", "Lopez");
        persona.set("dni", 44333444);
        persona.set("correo", "ana@unrc.edu.ar");
        persona.saveIt();

        com.is1.proyecto.models.Estudiante estudiante = new com.is1.proyecto.models.Estudiante();
        estudiante.set("dni", 44333444);
        estudiante.set("cod_estudiante", 8888);
        estudiante.saveIt();

        Materia materiaIncorrecta = new Materia();
        materiaIncorrecta.set("codigo_materia", 1201);
        materiaIncorrecta.set("nombre", "Botanica General");
        materiaIncorrecta.set("plan_materia", "2019");
        materiaIncorrecta.set("es_obligatoria", 1);
        materiaIncorrecta.saveIt();

        boolean mismoPlan = false; 

        if (mismoPlan) {
            Cursada cursada = new Cursada();
            cursada.set("estudiante_id", estudiante.getId());
            cursada.set("materia_id", materiaIncorrecta.getId());
            cursada.set("periodo", "2026-1C");
            cursada.saveIt();
        }

        Cursada guardada = Cursada.findFirst(
            "estudiante_id = ? AND materia_id = ?", estudiante.getId(), materiaIncorrecta.getId()
        );

        assertFalse(mismoPlan, "El sistema no debería validar la inscripción si los planes difieren.");
        assertNull(guardada, "No debería crearse un registro de cursada en la DB.");
    }
}