package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.Persona;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class EstudianteController {
    public EstudianteController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/estudiantes", this::listEstudiantes, mustache);
        post("/estudiantes/new", this::handleCreateEstudiante);
        get("/estudiantes/edit/:id", this::showEditEstudiante, mustache);
        post("/estudiantes/edit/:id", this::handleEditEstudiante);
        get("/estudiantes/delete/:id", this::handleDeleteEstudiante);
    }

    private ModelAndView listEstudiantes(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String sql = "SELECT e.id, e.dni, e.cod_estudiante, p.nombre, p.apellido " +
                     "FROM estudiante e JOIN person p ON e.dni = p.dni";
        model.put("estudiantes", Base.findAll(sql));

        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);

        return new ModelAndView(model, "estudiante_gestion.mustache");
    }

    private String handleCreateEstudiante(Request req, Response res) {
        String dniStr = req.queryParams("dni");
        String codEstudianteStr = req.queryParams("cod_estudiante");

        if (dniStr == null || dniStr.isEmpty() || codEstudianteStr == null || codEstudianteStr.isEmpty()) {
            res.redirect("/estudiantes?error=" + URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8));
            return "";
        }

        try {
            Persona persona = Persona.findFirst("dni = ?", dniStr);
            if (persona == null) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("El DNI ingresado no pertenece a ninguna Persona cargada. Regístrela primero.", StandardCharsets.UTF_8));
                return "";
            }

            if (Estudiante.findFirst("dni = ?", dniStr) != null) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("El estudiante con ese DNI ya se encuentra registrado.", StandardCharsets.UTF_8));
                return "";
            }

            if (Estudiante.findFirst("cod_estudiante = ?", codEstudianteStr) != null) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("El código de estudiante ya está asignado a otro alumno.", StandardCharsets.UTF_8));
                return "";
            }

            Estudiante est = new Estudiante();
            est.setDni(Integer.parseInt(dniStr));
            est.setCodEstudiante(Integer.parseInt(codEstudianteStr));
            est.saveIt();

            res.redirect("/estudiantes?message=" + URLEncoder.encode("Estudiante dado de alta con éxito.", StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.redirect("/estudiantes?error=" + URLEncoder.encode("Error interno del sistema al procesar el alta.", StandardCharsets.UTF_8));
        }
        return "";
    }

    private ModelAndView showEditEstudiante(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        Estudiante est = Estudiante.findById(req.params(":id"));
        if (est == null) {
            res.redirect("/estudiantes?error=Estudiante no encontrado.");
            return null;
        }
        
        Persona p = Persona.findFirst("dni = ?", est.getDni());
        model.put("estudiante", est.toMap());
        if (p != null) {
            model.put("nombre", p.getString("nombre"));
            model.put("apellido", p.getString("apellido"));
        }
        return new ModelAndView(model, "estudiante_edit.mustache");
    }

    private String handleEditEstudiante(Request req, Response res) {
        Estudiante est = Estudiante.findById(req.params(":id"));
        String nuevoCodStr = req.queryParams("cod_estudiante");

        if (est != null && nuevoCodStr != null) {
            try {
                Estudiante duplicado = Estudiante.findFirst("cod_estudiante = ? AND id != ?", nuevoCodStr, est.getId());
                if (duplicado != null) {
                    res.redirect("/estudiantes?error=" + URLEncoder.encode("Ese código de estudiante ya se encuentra en uso.", StandardCharsets.UTF_8));
                    return "";
                }

                est.setCodEstudiante(Integer.parseInt(nuevoCodStr));
                est.saveIt();
                res.redirect("/estudiantes?message=" + URLEncoder.encode("Datos del estudiante modificados correctamente.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("Error al modificar el registro.", StandardCharsets.UTF_8));
            }
        } else {
            res.redirect("/estudiantes?error=Registro no encontrado.");
        }
        return "";
    }

    private String handleDeleteEstudiante(Request req, Response res) {
        Estudiante est = Estudiante.findById(req.params(":id"));
        if (est != null) {
            if (est.tieneVinculosAcademicos()) {
                res.redirect("/estudiantes?error=" + URLEncoder.encode("No se puede eliminar el estudiante porque posee inscripciones o historial académico activo.", StandardCharsets.UTF_8));
            } else {
                est.delete();
                res.redirect("/estudiantes?message=" + URLEncoder.encode("Estudiante eliminado del sistema correctamente.", StandardCharsets.UTF_8));
            }
        } else {
            res.redirect("/estudiantes?error=No se pudo encontrar el estudiante a eliminar.");
        }
        return "";
    }
}
