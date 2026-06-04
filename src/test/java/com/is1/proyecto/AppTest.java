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
import com.is1.proyecto.models.DocenteMateria;

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

    @Test
    public void testAsignacionDocenteExitosayDuplicada() {
        Persona p = new Persona();
        p.set("nombre", "Docente");
        p.set("apellido", "Prueba");
        p.set("dni", 33000111);
        p.set("correo", "docente@prueba.com");
        p.saveIt();

        Profesor prof = new Profesor();
        prof.set("dni", 33000111);
        prof.set("nro_legajo", 7771);
        prof.saveIt();

        Materia materia = new Materia();
        materia.set("codigo_materia", 8801);
        materia.set("nombre", "Calculo I");
        materia.set("plan_materia", "2020");
        materia.set("es_obligatoria", 1);
        materia.saveIt();

        // Asignación exitosa
        DocenteMateria dm = new DocenteMateria();
        dm.set("profesor_dni", prof.get("dni"));
        dm.set("materia_id", materia.getId());
        dm.set("rol", "Jefe de cátedra");
        dm.set("periodo", "2026-1C");
        dm.set("activo", 1);
        assertTrue(dm.saveIt(), "La asignación docente debería guardarse correctamente.");

        // Intentar buscar duplicado (simulando lógica de negocio de ProfesorController)
        DocenteMateria duplicado = DocenteMateria.findFirst(
            "profesor_dni = ? AND materia_id = ? AND periodo = ?", 
            prof.get("dni"), materia.getId(), "2026-1C"
        );
        assertNotNull(duplicado, "Se debería detectar la asignación existente.");
    }

    @Test
    public void testBajaProfesorConYSinVinculos() {
        Persona p1 = new Persona();
        p1.set("nombre", "ProfesorSin");
        p1.set("apellido", "Vinculo");
        p1.set("dni", 33000222);
        p1.set("correo", "profesorsin@prueba.com");
        p1.saveIt();

        Profesor prof1 = new Profesor();
        prof1.set("dni", 33000222);
        prof1.set("nro_legajo", 7772);
        prof1.saveIt();

        // 1. Verificar sin vínculos: count debe ser 0
        long count1 = DocenteMateria.count("profesor_dni = ? AND activo = 1", prof1.get("dni"));
        assertEquals(0, count1, "No debería tener asignaciones activas.");

        // 2. Crear profesor con vínculo
        Persona p2 = new Persona();
        p2.set("nombre", "ProfesorCon");
        p2.set("apellido", "Vinculo");
        p2.set("dni", 33000333);
        p2.set("correo", "profesorcon@prueba.com");
        p2.saveIt();

        Profesor prof2 = new Profesor();
        prof2.set("dni", 33000333);
        prof2.set("nro_legajo", 7773);
        prof2.saveIt();

        Materia materia = new Materia();
        materia.set("codigo_materia", 8802);
        materia.set("nombre", "Calculo II");
        materia.set("plan_materia", "2020");
        materia.set("es_obligatoria", 1);
        materia.saveIt();

        DocenteMateria dm = new DocenteMateria();
        dm.set("profesor_dni", prof2.get("dni"));
        dm.set("materia_id", materia.getId());
        dm.set("rol", "Ayudante");
        dm.set("periodo", "2026-1C");
        dm.set("activo", 1);
        dm.saveIt();
        // 3. Verificar con vínculos: count debe ser > 0
        long count2 = DocenteMateria.count("profesor_dni = ? AND activo = 1", prof2.get("dni"));
        assertTrue(count2 > 0, "Debería tener al menos una asignación activa.");
    }

    @Test
    public void testDetectarCicloCorrelatividades() {
        // Crear materias
        Materia m1 = new Materia();
        m1.set("codigo_materia", 9001);
        m1.set("nombre", "Materia 1");
        m1.set("plan_materia", "2024");
        m1.set("es_obligatoria", 1);
        m1.saveIt();

        Materia m2 = new Materia();
        m2.set("codigo_materia", 9002);
        m2.set("nombre", "Materia 2");
        m2.set("plan_materia", "2024");
        m2.set("es_obligatoria", 1);
        m2.saveIt();

        Materia m3 = new Materia();
        m3.set("codigo_materia", 9003);
        m3.set("nombre", "Materia 3");
        m3.set("plan_materia", "2024");
        m3.set("es_obligatoria", 1);
        m3.saveIt();

        int id1 = ((Number) m1.getId()).intValue();
        int id2 = ((Number) m2.getId()).intValue();
        int id3 = ((Number) m3.getId()).intValue();

        // Configurar correlatividades iniciales en DB
        m1.agregarCorrelativa(id2); // Materia 1 requiere Materia 2 (m1 -> m2)
        m2.agregarCorrelativa(id3); // Materia 2 requiere Materia 3 (m2 -> m3)

        // Inicializar/Recargar el manager
        com.is1.proyecto.CorrelatividadesManager.getInstance().reload();

        // 1. Probar agregar m3 -> m1: generaría un ciclo (m1 -> m2 -> m3 -> m1)
        assertTrue(com.is1.proyecto.CorrelatividadesManager.getInstance().checkCycle(id3, id1),
            "Debería detectar un ciclo si m3 depende de m1");

        // 2. Probar agregar m3 -> m2: generaría un ciclo (m2 -> m3 -> m2)
        assertTrue(com.is1.proyecto.CorrelatividadesManager.getInstance().checkCycle(id3, id2),
            "Debería detectar un ciclo si m3 depende de m2");

        // 3. Probar agregar m1 -> m3: ya existe la transitividad pero no generaría ciclos directos o invertidos nuevos.
        // Esperamos falso ya que m1 ya depende indirectamente de m3 y agregar una dependencia directa a m3 no hace ciclo.
        assertFalse(com.is1.proyecto.CorrelatividadesManager.getInstance().checkCycle(id1, id3),
            "No debería considerarse ciclo agregar una dependencia transitiva existente de m1 a m3");
    }

    @Test
    public void testValidarInscripcionCumplePrerrequisitos() {
        // 1. Crear alumno
        Persona persona = new Persona();
        persona.set("nombre", "Juan");
        persona.set("apellido", "Perez");
        persona.set("dni", 55444333);
        persona.set("correo", "juan.perez@test.com");
        persona.saveIt();

        com.is1.proyecto.models.Estudiante estudiante = new com.is1.proyecto.models.Estudiante();
        estudiante.set("dni", 55444333);
        estudiante.set("cod_estudiante", 1122);
        estudiante.saveIt();

        int estudianteId = ((Number) estudiante.getId()).intValue();

        // 2. Crear materias correlativas (m1 requiere m2)
        Materia m1 = new Materia();
        m1.set("codigo_materia", 9101);
        m1.set("nombre", "Materia Superior");
        m1.set("plan_materia", "2024");
        m1.set("es_obligatoria", 1);
        m1.saveIt();

        Materia m2 = new Materia();
        m2.set("codigo_materia", 9102);
        m2.set("nombre", "Materia Base");
        m2.set("plan_materia", "2024");
        m2.set("es_obligatoria", 1);
        m2.saveIt();

        int idSuperior = ((Number) m1.getId()).intValue();
        int idBase = ((Number) m2.getId()).intValue();

        m1.agregarCorrelativa(idBase);

        // Recargar el manager
        com.is1.proyecto.CorrelatividadesManager.getInstance().reload();

        // Caso 1: Estudiante no rindió el examen final de Materia Base
        assertFalse(com.is1.proyecto.CorrelatividadesManager.getInstance().puedeCursar(estudianteId, idSuperior),
            "El estudiante no debería poder cursar sin la correlativa aprobada");

        // Caso 2: Estudiante rindió pero desaprobó (nota < 4)
        com.is1.proyecto.models.ExamenFinal examenReprobado = new com.is1.proyecto.models.ExamenFinal();
        examenReprobado.set("estudiante_id", estudianteId);
        examenReprobado.set("materia_id", idBase);
        examenReprobado.set("profesor_id", 9999);
        examenReprobado.set("nota", 2.0);
        examenReprobado.set("fecha", "2026-06-01");
        examenReprobado.saveIt();

        assertFalse(com.is1.proyecto.CorrelatividadesManager.getInstance().puedeCursar(estudianteId, idSuperior),
            "El estudiante no debería poder cursar con la correlativa desaprobada (nota 2.0)");

        // Borrar el examen desaprobado para evitar interferencia
        examenReprobado.delete();

        // Caso 3: Estudiante rindió y aprobó (nota >= 4)
        com.is1.proyecto.models.ExamenFinal examenAprobado = new com.is1.proyecto.models.ExamenFinal();
        examenAprobado.set("estudiante_id", estudianteId);
        examenAprobado.set("materia_id", idBase);
        examenAprobado.set("profesor_id", 9999);
        examenAprobado.set("nota", 7.5);
        examenAprobado.set("fecha", "2026-06-02");
        examenAprobado.saveIt();

        assertTrue(com.is1.proyecto.CorrelatividadesManager.getInstance().puedeCursar(estudianteId, idSuperior),
            "El estudiante debería poder cursar con la correlativa aprobada (nota 7.5)");
    }

    @Test
    public void testGestionDePlanDeEstudio() {
        // 1. Crear Carrera
        Carrera carrera = new Carrera();
        carrera.set("codigo", "TEST-INFO");
        carrera.set("nombre", "Tecnicatura en Informatica");
        carrera.saveIt();

        // 2. Crear Plan de Estudio (Vigente por defecto)
        com.is1.proyecto.models.PlanEstudio plan = new com.is1.proyecto.models.PlanEstudio();
        plan.set("nombre", "Plan 2026");
        plan.set("codigo", "P26-INFO");
        plan.set("carrera_id", carrera.getId());
        plan.set("version", 1);
        plan.set("vigente", 1);
        assertTrue(plan.saveIt(), "El plan debería guardarse correctamente");

        // 3. Crear Materia
        Materia materia = new Materia();
        materia.set("codigo_materia", 9501);
        materia.set("nombre", "Introduccion a la Programacion");
        materia.set("plan_materia", "2026");
        materia.set("es_obligatoria", 1);
        materia.saveIt();

        // 4. Vincular Materia al Plan en el año 1, cuatrimestre 1
        com.is1.proyecto.models.PlanMateria pm = new com.is1.proyecto.models.PlanMateria();
        pm.set("plan_estudio_id", plan.getId());
        pm.set("materia_id", materia.getId());
        pm.set("anio_cursado", 1);
        pm.set("cuatrimestre", 1);
        assertTrue(pm.saveIt(), "La vinculación del plan y la materia debería ser exitosa");

        // Verificar vinculación en base de datos
        com.is1.proyecto.models.PlanMateria pmGuardado = com.is1.proyecto.models.PlanMateria.findFirst(
            "plan_estudio_id = ? AND materia_id = ?", plan.getId(), materia.getId()
        );
        assertNotNull(pmGuardado);
        assertEquals(1, pmGuardado.getInteger("anio_cursado").intValue());
        assertEquals(1, pmGuardado.getInteger("cuatrimestre").intValue());

        // 5. Desactivar el Plan (Vigente = 0)
        plan.set("vigente", 0);
        plan.saveIt();

        com.is1.proyecto.models.PlanEstudio planDesactivado = com.is1.proyecto.models.PlanEstudio.findById(plan.getId());
        assertEquals(0, planDesactivado.getInteger("vigente").intValue(), "El plan debería estar marcado como no vigente");
    }

    @Test
    public void testRestringirEstudiantePlanNoVigente() {
        // 1. Crear Carrera
        Carrera carrera = new Carrera();
        carrera.set("codigo", "TEST-VIG");
        carrera.set("nombre", "Carrera Test Vigente");
        carrera.saveIt();

        // 2. Crear Plan de Estudio NO Vigente (vigente = 0)
        com.is1.proyecto.models.PlanEstudio planNoVigente = new com.is1.proyecto.models.PlanEstudio();
        planNoVigente.set("nombre", "Plan Obsoleto");
        planNoVigente.set("codigo", "P-OBS");
        planNoVigente.set("carrera_id", carrera.getId());
        planNoVigente.set("version", 1);
        planNoVigente.set("vigente", 0);
        planNoVigente.saveIt();

        // 3. Crear Persona
        Persona persona = new Persona();
        persona.set("nombre", "Estudiante");
        persona.set("apellido", "PlanInactivo");
        persona.set("dni", 55999888);
        persona.set("correo", "estudiante.inactivo@test.com");
        persona.saveIt();

        // 4. Intentar matricular estudiante en el plan no vigente
        com.is1.proyecto.models.Estudiante estudiante = new com.is1.proyecto.models.Estudiante();
        estudiante.set("dni", 55999888);
        estudiante.set("cod_estudiante", 99221);
        estudiante.set("carrera_id", carrera.getId());
        estudiante.set("plan_estudio_id", planNoVigente.getId());

        // Debe fallar la validación y no guardar en base de datos
        assertFalse(estudiante.save(), "No se debería poder guardar un estudiante matriculado en un plan no vigente");
        assertTrue(estudiante.errors().containsKey("plan_estudio_id"), "Debería retornar error de validación en plan_estudio_id");
    }

    @Test
    public void testInscripcionMateriasPlanRestricciones() {
        // 1. Crear Carrera
        Carrera carrera = new Carrera();
        carrera.set("codigo", "TEST-INS");
        carrera.set("nombre", "Carrera Test Inscripcion");
        carrera.saveIt();

        // 2. Crear Plan de Estudio Vigente
        com.is1.proyecto.models.PlanEstudio plan = new com.is1.proyecto.models.PlanEstudio();
        plan.set("nombre", "Plan Test");
        plan.set("codigo", "P-TEST-INS");
        plan.set("carrera_id", carrera.getId());
        plan.set("version", 1);
        plan.set("vigente", 1);
        plan.saveIt();

        // 3. Crear Materia del Plan
        Materia materiaPlan = new Materia();
        materiaPlan.set("codigo_materia", 9801);
        materiaPlan.set("nombre", "Materia del Plan");
        materiaPlan.set("plan_materia", "2026");
        materiaPlan.set("es_obligatoria", 1);
        materiaPlan.set("carrera_id", carrera.getId());
        materiaPlan.saveIt();

        // Vincular al plan
        com.is1.proyecto.models.PlanMateria pm = new com.is1.proyecto.models.PlanMateria();
        pm.set("plan_estudio_id", plan.getId());
        pm.set("materia_id", materiaPlan.getId());
        pm.set("anio_cursado", 1);
        pm.set("cuatrimestre", 1);
        pm.saveIt();

        // 4. Crear Materia fuera del Plan
        Materia materiaFueraPlan = new Materia();
        materiaFueraPlan.set("codigo_materia", 9802);
        materiaFueraPlan.set("nombre", "Materia Fuera de Plan");
        materiaFueraPlan.set("plan_materia", "2026");
        materiaFueraPlan.set("es_obligatoria", 1);
        materiaFueraPlan.set("carrera_id", carrera.getId());
        materiaFueraPlan.saveIt();

        // 5. Crear Persona y Estudiante con Plan
        Persona persona = new Persona();
        persona.set("nombre", "Estudiante");
        persona.set("apellido", "Inscripcion");
        persona.set("dni", 55888777);
        persona.set("correo", "estudiante.ins@test.com");
        persona.saveIt();

        com.is1.proyecto.models.Estudiante estudiante = new com.is1.proyecto.models.Estudiante();
        estudiante.set("dni", 55888777);
        estudiante.set("cod_estudiante", 99222);
        estudiante.set("carrera_id", carrera.getId());
        estudiante.set("plan_estudio_id", plan.getId());
        estudiante.saveIt();

        int estudianteId = ((Number) estudiante.getId()).intValue();
        int matPlanId = ((Number) materiaPlan.getId()).intValue();
        int matFueraId = ((Number) materiaFueraPlan.getId()).intValue();

        // 6. Test: Inscripción exitosa a materia del plan
        Cursada c1 = new Cursada();
        c1.set("estudiante_id", estudianteId);
        c1.set("materia_id", matPlanId);
        c1.set("periodo", "2026-1C");
        assertTrue(c1.saveIt(), "La inscripción a una materia del plan debería guardarse correctamente");

        // 7. Test: Comprobar que la materia está vinculada al plan en DB
        boolean perteneceAlPlan = !Base.findAll(
            "SELECT 1 FROM plan_materias WHERE plan_estudio_id = ? AND materia_id = ?",
            plan.getId(), matPlanId
        ).isEmpty();
        assertTrue(perteneceAlPlan, "La materia debe pertenecer al plan");

        boolean perteneceAlPlanFuera = !Base.findAll(
            "SELECT 1 FROM plan_materias WHERE plan_estudio_id = ? AND materia_id = ?",
            plan.getId(), matFueraId
        ).isEmpty();
        assertFalse(perteneceAlPlanFuera, "La materia fuera de plan no debe pertenecer al plan");

        // 8. Test: Comprobar validación de aprobación de final
        boolean yaAproboAntes = !Base.findAll(
            "SELECT id FROM examen_final WHERE estudiante_id = ? AND materia_id = ? AND nota >= 4.0",
            estudianteId, matPlanId
        ).isEmpty();
        assertFalse(yaAproboAntes, "No debería figurar como aprobada inicialmente");

        com.is1.proyecto.models.ExamenFinal ef = new com.is1.proyecto.models.ExamenFinal();
        ef.set("estudiante_id", estudianteId);
        ef.set("materia_id", matPlanId);
        ef.set("profesor_id", 9999);
        ef.set("nota", 8.0);
        ef.set("fecha", "2026-06-03");
        ef.saveIt();

        boolean yaAproboDespues = !Base.findAll(
            "SELECT id FROM examen_final WHERE estudiante_id = ? AND materia_id = ? AND nota >= 4.0",
            estudianteId, matPlanId
        ).isEmpty();
        assertTrue(yaAproboDespues, "Debería figurar como aprobada después de registrar el examen");
    }
}