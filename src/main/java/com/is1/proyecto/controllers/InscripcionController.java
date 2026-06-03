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
    }

    private ModelAndView showInscripciones(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String estudianteId = req.params(":estudiante_id");

        // 1. Obtener los datos del estudiante cruzando con Persona para el nombre
        String sqlEstudiante = "SELECT e.id, e.carrera_id, p.nombre, p.apellido " +
                               "FROM estudiante e JOIN person p ON e.dni = p.dni WHERE e.id = ?";
        var estudiantes = Base.findAll(sqlEstudiante, estudianteId);

        if (estudiantes.isEmpty()) {
            res.redirect("/estudiantes?error=" + URLEncoder.encode("Estudiante no encontrado", StandardCharsets.UTF_8));
            return null;
        }
        Map<String, Object> estudiante = estudiantes.get(0);
        model.put("estudiante", estudiante);

        // 2. Traer SOLO las materias que pertenecen al plan de su carrera
        Object carreraId = estudiante.get("carrera_id");
        var materiasFiltradas = Materia.where("carrera_id = ?", carreraId).toMaps();
        model.put("materiasDisponibles", materiasFiltradas);

        // 3. Traer las materias en las que ya se inscribió
        String sqlCursadas = "SELECT c.id, m.nombre AS materia_nombre, c.periodo " +
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
            // Validación extra: verificar si ya existe la inscripción para ese período
            Cursada existente = Cursada.findFirst("estudiante_id = ? AND materia_id = ? AND periodo = ?",
                                                estudianteId, materiaId, periodo);
            if (existente != null) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Ya estás inscripto a esta materia en este período", StandardCharsets.UTF_8));
                return "";
            }

            // Validar correlativas
            int estId = Integer.parseInt(estudianteId);
            int matId = Integer.parseInt(materiaId);
            if (!com.is1.proyecto.CorrelatividadesManager.getInstance().puedeCursar(estId, matId)) {
                res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("No cumple con las correlatividades requeridas para cursar esta materia.", StandardCharsets.UTF_8));
                return "";
            }

            // Guardar relación
            Cursada nuevaInscripcion = new Cursada();
            nuevaInscripcion.set("estudiante_id", Integer.parseInt(estudianteId));
            nuevaInscripcion.set("materia_id", Integer.parseInt(materiaId));
            nuevaInscripcion.set("periodo", periodo);
            nuevaInscripcion.saveIt();

            res.redirect("/inscripciones/" + estudianteId + "?message=" + URLEncoder.encode("Inscripción realizada con éxito", StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.redirect("/inscripciones/" + estudianteId + "?error=" + URLEncoder.encode("Error al procesar la inscripción", StandardCharsets.UTF_8));
        }
        return "";
    }
}
