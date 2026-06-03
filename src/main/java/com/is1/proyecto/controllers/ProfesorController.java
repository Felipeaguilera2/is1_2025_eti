package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.ExamenFinal;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.Profesor;
import com.is1.proyecto.models.DocenteMateria;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class ProfesorController {
    public ProfesorController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/profesor/login", this::showLogin, mustache);
        post("/profesor/login", this::handleLogin);
        get("/profesor/gestion", this::showGestion, mustache);
        
        get("/profesor/baja", this::showBaja, mustache);
        post("/profesor/baja/verificar", this::handleBajaVerificar, mustache);
        post("/profesor/baja/confirmar", this::handleBajaConfirmar, mustache);
        
        get("/profesor/consulta", this::showConsulta, mustache);
        post("/profesor/consulta", this::handleConsulta, mustache);
        post("/profesor/editar", this::handleEditar);
        
        get("/profesor/cargar-nota", this::showCargarNota, mustache);
        post("/profesor/cargar-nota", this::handleCargarNota);

        // Rutas para asignación de docentes a materias
        get("/profesor/asignar", this::showAsignarDocente, mustache);
        post("/profesor/asignar", this::handleAsignarDocente);
    }

    private ModelAndView showLogin(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        return new ModelAndView(model, "profesor/professor_login.mustache"); 
    }

    private String handleLogin(Request req, Response res) {
        String dni = req.queryParams("prof_dni");
        String legajo = req.queryParams("nro_legajo");

        if (dni == null || dni.isEmpty() || legajo == null || legajo.isEmpty()) { 
            res.status(400);
            String msg = URLEncoder.encode("El DNI y el Número de Legajo son requeridos.", StandardCharsets.UTF_8);
            res.redirect("/profesor/login?error=" + msg);
            return null;
        }
        
        Integer pdni;
        Integer plegajo;
        try {
            pdni = Integer.valueOf(dni.trim());
            plegajo = Integer.valueOf(legajo.trim());
        } catch (NumberFormatException e) {
             res.status(400);
             String msg = URLEncoder.encode("El DNI y el Número de Legajo deben ser números válidos.", StandardCharsets.UTF_8);
             res.redirect("/profesor/login?error=" + msg);
             return null;
        }
    
        try {
            Profesor existingProfesor = Profesor.findFirst("nro_legajo = ?", plegajo);

            if (existingProfesor != null) {
                res.status(409);
                String msg = URLEncoder.encode("El Número de Legajo (" + legajo + ") ya está en uso por otro profesor.", StandardCharsets.UTF_8);
                res.redirect("/profesor/login?error=" + msg);
                return null;
            }
            
            Persona dn = Persona.findFirst("dni = ?", pdni);

            if (dn == null) {
                res.status(401);
                String msg = URLEncoder.encode("El DNI " + dni + " no está cargado en el sistema. Por favor, registra la persona primero.", StandardCharsets.UTF_8); 
                res.redirect("/profesor/login?error=" + msg);
                return null;
            }
            
            Profesor newProfesor = new Profesor();
            newProfesor.set("dni", pdni);
            newProfesor.set("nro_legajo", plegajo);
            newProfesor.saveIt();

            res.status(201);
            String mensajeExito = "Profesor con Legajo " + legajo + " cargado exitosamente.";
            String msgEncoded = URLEncoder.encode(mensajeExito, StandardCharsets.UTF_8);
            res.redirect("/profesor/login?message=" + msgEncoded);
            return null;

        } catch (Exception e) {
            System.err.println("Error al registrar profesor: " + e.getMessage());
            e.printStackTrace(); 
            res.status(500);
            String msg = URLEncoder.encode("Error interno al registrar el profesor: " + e.getMessage(), StandardCharsets.UTF_8);
            res.redirect("/profesor/login?error=" + msg);
            return null;
        }
    }

    private ModelAndView showGestion(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        return new ModelAndView(model, "profesor/gestion_profesor.mustache"); 
    }

    private ModelAndView showBaja(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        return new ModelAndView(model, "profesor/baja_profesor.mustache");
    }

    private ModelAndView handleBajaVerificar(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String legajoStr = req.queryParams("nro_legajo");

        if (legajoStr == null || legajoStr.isEmpty()) {
            res.redirect("/profesor/baja?error=" + URLEncoder.encode("Debe ingresar un número de legajo válido.", StandardCharsets.UTF_8));
            return null;
        }

        try {
            Integer legajo = Integer.valueOf(legajoStr.trim());
            Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

            if (prof == null) {
                String msg = URLEncoder.encode("El profesor con Legajo " + legajoStr + " no se encuentra en la base de datos.", StandardCharsets.UTF_8);
                res.redirect("/profesor/baja?error=" + msg);
                return null;
            }

            model.put("profesorEncontrado", true);
            model.put("legajoEliminar", String.valueOf(prof.get("nro_legajo")));
            model.put("dniAsociado", String.valueOf(prof.get("dni")));

            return new ModelAndView(model, "profesor/baja_profesor.mustache");

        } catch (NumberFormatException e) {
            res.redirect("/profesor/baja?error=" + URLEncoder.encode("El legajo debe ser un valor numérico.", StandardCharsets.UTF_8));
            return null;
        } catch (Exception e) {
            System.err.println("Error en verificar: " + e.getMessage());
            res.redirect("/profesor/baja?error=" + URLEncoder.encode("Error interno al verificar el legajo.", StandardCharsets.UTF_8));
            return null;
        }
    }

    private ModelAndView handleBajaConfirmar(Request req, Response res) {
        String legajoStr = req.queryParams("nro_legajo");

        if (legajoStr == null || legajoStr.isEmpty()) {
            res.redirect("/profesor/baja?error=" + URLEncoder.encode("Falta el número de legajo para procesar la baja.", StandardCharsets.UTF_8));
            return null;
        }

        try {
            Integer legajo = Integer.valueOf(legajoStr.trim());
            Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

            if (prof != null) {
                // Baja de profesor con vínculos: Impedir borrado si está relacionado con una materia en un periodo activo (activo = 1)
                long activeAssignments = DocenteMateria.count("profesor_dni = ? AND activo = 1", prof.get("dni"));
                if (activeAssignments > 0) {
                    String msg = URLEncoder.encode("No se puede eliminar el profesor porque posee materias asignadas en períodos activos.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/baja?error=" + msg);
                    return null;
                }

                Base.exec("DELETE FROM profesor WHERE nro_legajo = ?", legajo);
                String msg = URLEncoder.encode("La operación ocurrió exitosamente. Profesor eliminado.", StandardCharsets.UTF_8);
                res.redirect("/profesor/baja?message=" + msg);
            } else {
                String msg = URLEncoder.encode("El profesor ya no existe o fue eliminado por otro proceso.", StandardCharsets.UTF_8);
                res.redirect("/profesor/baja?error=" + msg);
            }
            return null;

        } catch (Exception e) {
            System.err.println("CRÍTICO: Error en confirmación: " + e.getMessage());
            e.printStackTrace();
            String msg = URLEncoder.encode("Error al confirmar la eliminación: " + e.getMessage(), StandardCharsets.UTF_8);
            res.redirect("/profesor/baja?error=" + msg);
            return null;
        }
    }

    private ModelAndView showConsulta(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        return new ModelAndView(model, "profesor/profesor_consulta.mustache");
    }

    private ModelAndView handleConsulta(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String legajoStr = req.queryParams("nro_legajo");

        if (legajoStr == null || legajoStr.isEmpty()) {
            res.redirect("/profesor/consulta?error=" + URLEncoder.encode("Debe ingresar un número de legajo.", StandardCharsets.UTF_8));
            return null;
        }

        try {
            Integer legajo = Integer.valueOf(legajoStr.trim());
            model.put("legajoBuscado", legajo);

            Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

            if (prof == null) {
                String msg = URLEncoder.encode("El profesor con el legajo ingresado no existe en el sistema.", StandardCharsets.UTF_8);
                res.redirect("/profesor/consulta?error=" + msg);
                return null;
            }

            Integer personaDni = prof.getInteger("dni");
            Persona persona = Persona.findFirst("dni = ?", personaDni);

            if (persona != null) {
                model.put("profesorEncontrado", true);
                model.put("legajo", String.valueOf(prof.get("nro_legajo")));
                model.put("dni", String.valueOf(persona.get("dni")));
                model.put("nombre", persona.get("nombre"));
                model.put("apellido", persona.get("apellido"));
                model.put("correo", persona.get("correo"));
            } else {
                model.put("errorMessage", "Error: No se encontraron datos personales válidos para este profesor.");
            }

            return new ModelAndView(model, "profesor/profesor_consulta.mustache");

        } catch (NumberFormatException e) {
            res.redirect("/profesor/consulta?error=" + URLEncoder.encode("El legajo debe ser un valor numérico.", StandardCharsets.UTF_8));
            return null;
        } catch (Exception e) {
            res.redirect("/profesor/consulta?error=" + URLEncoder.encode("Error interno al procesar la consulta.", StandardCharsets.UTF_8));
            return null;
        }
    }

    private String handleEditar(Request req, Response res) {
        String legajoStr = req.queryParams("nro_legajo");
        String dniStr = req.queryParams("dni");
        String nombre = req.queryParams("nombre");
        String apellido = req.queryParams("apellido");
        String correo = req.queryParams("correo");

        if (dniStr == null || dniStr.trim().isEmpty()) {
            String msg = URLEncoder.encode("Error: El formulario no envió el DNI del profesor.", StandardCharsets.UTF_8);
            res.redirect("/profesor/consulta?error=" + msg);
            return null;
        }

        try {
            Integer pdni = Integer.valueOf(dniStr.trim());
            Persona persona = Persona.findFirst("dni = ?", pdni);

            if (persona != null) {
                String nuevoNombre = (nombre != null) ? nombre.trim() : persona.getString("nombre");
                String nuevoApellido = (apellido != null) ? apellido.trim() : persona.getString("apellido");
                String nuevoCorreo = (correo != null) ? correo.trim() : persona.getString("correo");

                Base.exec("UPDATE person SET nombre = ?, apellido = ?, correo = ? WHERE dni = ?", 
                          nuevoNombre, nuevoApellido, nuevoCorreo, pdni);

                String msg = URLEncoder.encode("Datos del profesor con Legajo " + legajoStr + " actualizados con éxito.", StandardCharsets.UTF_8);
                res.redirect("/profesor/consulta?message=" + msg);
            } else {
                String msg = URLEncoder.encode("Error: El DNI " + dniStr + " no corresponde a ninguna persona registrada.", StandardCharsets.UTF_8);
                res.redirect("/profesor/consulta?error=" + msg);
            }
            return null;

        } catch (Exception e) {
            System.err.println("CRÍTICO: Error al actualizar la persona en la DB:");
            e.printStackTrace(); 
            
            String msg = URLEncoder.encode("Error interno al guardar los cambios: " + e.getMessage(), StandardCharsets.UTF_8);
            res.redirect("/profesor/consulta?error=" + msg);
            return null;
        }
    }

    private ModelAndView showCargarNota(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        
        model.put("materias", Materia.findAll().toMaps());
        
        String sqlEstudiantes = "SELECT e.id, p.nombre, p.apellido, e.cod_estudiante " +
                                "FROM estudiante e JOIN person p ON e.dni = p.dni";
        model.put("estudiantes", Base.findAll(sqlEstudiantes));
        
        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);

        return new ModelAndView(model, "profesor/cargar_nota.mustache");
    }

    private String handleCargarNota(Request req, Response res) {
        String estudianteIdStr = req.queryParams("estudiante_id");
        String materiaIdStr = req.queryParams("materia_id");
        String legajoProfesorStr = req.queryParams("nro_legajo"); 
        String notaStr = req.queryParams("nota");
        String fecha = req.queryParams("fecha");

        if (estudianteIdStr == null || estudianteIdStr.isEmpty() || 
            materiaIdStr == null || materiaIdStr.isEmpty() || 
            legajoProfesorStr == null || legajoProfesorStr.isEmpty() ||
            notaStr == null || notaStr.isEmpty() || fecha == null || fecha.isEmpty()) {
            
            res.redirect("/profesor/cargar-nota?error=" + URLEncoder.encode("Todos los campos son estrictamente obligatorios.", StandardCharsets.UTF_8));
            return "";
        }

        try {
            Integer legajoProf = Integer.valueOf(legajoProfesorStr.trim());
            Profesor prof = Profesor.findFirst("nro_legajo = ?", legajoProf);
            if (prof == null) {
                res.redirect("/profesor/cargar-nota?error=" + URLEncoder.encode("Error de permisos: El legajo de profesor ingresado no es válido.", StandardCharsets.UTF_8));
                return "";
            }

            double notaDouble = Double.parseDouble(notaStr.trim());
            if (notaDouble < 1 || notaDouble > 10) {
                res.redirect("/profesor/cargar-nota?error=" + URLEncoder.encode("La nota debe ser un valor numérico entre 1 y 10.", StandardCharsets.UTF_8));
                return "";
            }

            ExamenFinal examen = new ExamenFinal();
            examen.set("estudiante_id", Integer.valueOf(estudianteIdStr));
            examen.set("materia_id", Integer.valueOf(materiaIdStr));
            examen.set("profesor_id", prof.get("dni"));
            examen.set("nota", notaDouble);
            examen.set("fecha", fecha); 
            examen.saveIt();

            String msgExito = "Nota (" + notaDouble + ") registrada exitosamente.";
            res.redirect("/profesor/cargar-nota?message=" + URLEncoder.encode(msgExito, StandardCharsets.UTF_8));
            return "";

        } catch (NumberFormatException e) {
            res.redirect("/profesor/cargar-nota?error=" + URLEncoder.encode("Formato incorrecto: La nota y el legajo deben ser números válidos.", StandardCharsets.UTF_8));
            return "";
        } catch (Exception e) {
            res.redirect("/profesor/cargar-nota?error=" + URLEncoder.encode("Error interno al procesar el examen: " + e.getMessage(), StandardCharsets.UTF_8));
            return "";
        }
    }

    private ModelAndView showAsignarDocente(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();

        // 1. Obtener lista de profesores con sus datos personales
        String sqlProfesores = "SELECT pr.dni, pr.nro_legajo, pe.nombre, pe.apellido " +
                               "FROM profesor pr JOIN person pe ON pr.dni = pe.dni";
        model.put("profesores", Base.findAll(sqlProfesores));

        // 2. Obtener todas las materias
        model.put("materias", Materia.findAll().toMaps());

        // 3. Obtener asignaciones actuales
        String sqlAsignaciones = "SELECT dm.id, pe.nombre, pe.apellido, m.nombre AS materia_nombre, dm.rol, dm.periodo " +
                                 "FROM docente_materia dm " +
                                 "JOIN person pe ON dm.profesor_dni = pe.dni " +
                                 "JOIN materia m ON dm.materia_id = m.id WHERE dm.activo = 1";
        model.put("asignaciones", Base.findAll(sqlAsignaciones));

        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);

        return new ModelAndView(model, "profesor/asignar_docente.mustache");
    }

    private String handleAsignarDocente(Request req, Response res) {
        String profesorDniStr = req.queryParams("profesor_dni");
        String materiaIdStr = req.queryParams("materia_id");
        String rol = req.queryParams("rol");
        String periodo = req.queryParams("periodo");

        if (profesorDniStr == null || profesorDniStr.isEmpty() ||
            materiaIdStr == null || materiaIdStr.isEmpty() ||
            rol == null || rol.isEmpty() ||
            periodo == null || periodo.isEmpty()) {
            
            res.redirect("/profesor/asignar?error=" + URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8));
            return "";
        }

        try {
            Integer profesorDni = Integer.valueOf(profesorDniStr);
            Integer materiaId = Integer.valueOf(materiaIdStr);

            // Criterio de Aceptación: El sistema detecta y arroja un error si ya está asignado
            DocenteMateria existente = DocenteMateria.findFirst(
                "profesor_dni = ? AND materia_id = ? AND periodo = ?", 
                profesorDni, materiaId, periodo
            );

            if (existente != null) {
                String errorMsg = "El profesor ya se encuentra asignado a esta materia en este período.";
                if (!existente.getString("rol").equals(rol)) {
                    errorMsg = "Conflicto: El profesor ya está asignado a esta materia en este período con el rol de " + existente.getString("rol") + ".";
                }
                res.redirect("/profesor/asignar?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                return "";
            }

            DocenteMateria dm = new DocenteMateria();
            dm.set("profesor_dni", profesorDni);
            dm.set("materia_id", materiaId);
            dm.set("rol", rol);
            dm.set("periodo", periodo);
            dm.set("activo", 1);
            dm.saveIt();

            res.redirect("/profesor/asignar?message=" + URLEncoder.encode("Asignación docente realizada con éxito.", StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.redirect("/profesor/asignar?error=" + URLEncoder.encode("Error interno al registrar la asignación.", StandardCharsets.UTF_8));
        }
        return "";
    }
}
