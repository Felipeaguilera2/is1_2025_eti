package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Cursada;
import com.is1.proyecto.models.Materia;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class InscripcionController {
    public InscripcionController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/inscripciones/:estudiante_id", this::showInscripciones, mustache);
        post("/inscripciones/new", this::handleInscripcion);
        get("/inscripciones/:estudiante_id/delete/:cursada_id", this::handleDeleteInscripcion);
    }

    private ModelAndView showInscripciones(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String estudianteId = req.params(":estudiante_id");

        // 1. Obtener los datos del estudiante cruzando con Persona para el nombre, incluyendo plan_estudio_id
        String sqlEstudiante = "SELECT e.id, e.carrera_id, e.plan_estudio_id, p.nombre, p.apellido, pe.nombre AS plan_nombre " +
                               "FROM estudiante e " +
                               "JOIN person p ON e.dni = p.dni " +
                               "LEFT JOIN planes_estudio pe ON e.plan_estudio_id = pe.id " +
                               "WHERE e.id = ?";
        var estudiantes = Base.findAll(sqlEstudiante, estudianteId);

        if (estudiantes.isEmpty()) {
            res.redirect("/estudiantes?error=" + URLEncoder.encode("Estudiante no encontrado", StandardCharsets.UTF_8));
            return null;
        }
        Map<String, Object> estudiante = estudiantes.get(0);
        model.put("estudiante", estudiante);

        // 2. Traer SOLO las materias que pertenecen al plan de estudio del estudiante
        Object planEstudioId = estudiante.get("plan_estudio_id");
        java.util.List<Map> materiasFiltradas;
        if (planEstudioId != null) {
            String sqlMaterias = "SELECT m.id, m.nombre, m.codigo_materia " +
                                 "FROM plan_materias pm " +
                                 "JOIN materia m ON pm.materia_id = m.id " +
                                 "WHERE pm.plan_estudio_id = ?";
            materiasFiltradas = Base.findAll(sqlMaterias, planEstudioId);
        } else {
            materiasFiltradas = new java.util.ArrayList<>();
            model.put("errorMessage", "El estudiante no tiene asignado un plan de estudio. Asigne uno antes de inscribir.");
        }
        model.put("materiasDisponibles", materiasFiltradas);

        // 3. Traer las materias en las que ya se inscribió
        String sqlCursadas = "SELECT c.id, c.estudiante_id, m.nombre AS materia_nombre, c.periodo " +
                             "FROM cursadas c JOIN materia m ON c.materia_id = m.id " +
                             "WHERE c.estudiante_id = ?";
        var inscripcionesActuales = Base.findAll(sqlCursadas, estudianteId);
        model.put("inscripciones", inscripcionesActuales);

        // Mensajes de feedback
        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);

        return new ModelAndView(model, "inscripcion_form.mustache");
    }

    private String handleInscripcion(Request req, Response res) {
        String estudianteId = req.queryParams("estudiante_id");
        String materiaId = req.queryParams("materia_id");
        String periodo = req.queryParams("periodo");

        if (estudianteId == null || materiaId == null || periodo == null || periodo.isEmpty()) {
            res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Todos los campos son obligatorios", StandardCharsets.UTF_8));
            return "";
        }

        try {
            // Obtener el plan del estudiante para validaciones adicionales
            var estudianteRows = Base.findAll("SELECT plan_estudio_id FROM estudiante WHERE id = ?", estudianteId);
            if (estudianteRows.isEmpty()) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("Estudiante no encontrado", StandardCharsets.UTF_8));
                return "";
            }
            Object planId = estudianteRows.get(0).get("plan_estudio_id");
            if (planId == null) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("El estudiante no tiene un plan de estudio asignado.", StandardCharsets.UTF_8));
                return "";
            }

            int estId = Integer.parseInt(estudianteId);
            int matId = Integer.parseInt(materiaId);

            // Validar que la materia pertenezca al plan de estudio del estudiante
            boolean perteneceAlPlan = !Base.findAll(
                "SELECT 1 FROM plan_materias WHERE plan_estudio_id = ? AND materia_id = ?",
                planId, matId
            ).isEmpty();
            if (!perteneceAlPlan) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("La materia seleccionada no pertenece al plan de estudio del estudiante.", StandardCharsets.UTF_8));
                return "";
            }

            // Validar si ya aprobó la materia (examen_final con nota >= 4)
            boolean yaAprobo = !Base.findAll(
                "SELECT id FROM examen_final WHERE estudiante_id = ? AND materia_id = ? AND nota >= 4.0",
                estId, matId
            ).isEmpty();
            if (yaAprobo) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("El estudiante ya aprobó esta materia.", StandardCharsets.UTF_8));
                return "";
            }

            // Validación extra: verificar si ya existe la inscripción para ese período
            Cursada existente = Cursada.findFirst("estudiante_id = ? AND materia_id = ? AND periodo = ?",
                                                estudianteId, materiaId, periodo);
            if (existente != null) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Ya estás inscripto a esta materia en este período", StandardCharsets.UTF_8));
                return "";
            }

            // Validar correlativas
            if (!com.is1.proyecto.CorrelatividadesManager.getInstance().puedeCursar(estId, matId)) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("No cumple con las correlatividades requeridas para cursar esta materia.", StandardCharsets.UTF_8));
                return "";
            }

            // Guardar relación
            Cursada nuevaInscripcion = new Cursada();
            nuevaInscripcion.set("estudiante_id", estId);
            nuevaInscripcion.set("materia_id", matId);
            nuevaInscripcion.set("periodo", periodo);
            nuevaInscripcion.saveIt();

            res.redirect("/inscripciones/" + estudianteId + "?message=" + URLEncoder.encode("Inscripción realizada con éxito", StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Error al procesar la inscripción", StandardCharsets.UTF_8));
        }
        return "";
    }

    private String handleDeleteInscripcion(Request req, Response res) {
        String estudianteId = req.params(":estudiante_id");
        String cursadaId = req.params(":cursada_id");

        try {
            Cursada cursada = Cursada.findById(cursadaId);
            if (cursada != null) {
                if (cursada.getInteger("estudiante_id") == Integer.parseInt(estudianteId)) {
                    cursada.delete();
                    res.redirect("/inscripciones/" + estudianteId + "?message=" + URLEncoder.encode("Inscripción dada de baja con éxito.", StandardCharsets.UTF_8));
                } else {
                    res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("La inscripción no corresponde al estudiante.", StandardCharsets.UTF_8));
                }
            } else {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Inscripción no encontrada.", StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Error al procesar la baja de la inscripción.", StandardCharsets.UTF_8));
        }
        return "";
    }
}
