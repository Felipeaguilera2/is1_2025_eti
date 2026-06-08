package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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
        String sql = "SELECT e.id, e.dni, e.cod_estudiante, p.nombre, p.apellido, c.nombre AS carrera_nombre, pe.nombre AS plan_nombre " +
                     "FROM estudiante e " +
                     "JOIN person p ON e.dni = p.dni " +
                     "LEFT JOIN carrera c ON e.carrera_id = c.id " +
                     "LEFT JOIN planes_estudio pe ON e.plan_estudio_id = pe.id";
        model.put("estudiantes", Base.findAll(sql));

        // Cargar carreras y planes activos para el formulario de alta
        model.put("carreras", com.is1.proyecto.models.Carrera.findAll().toMaps());
        model.put("planes", com.is1.proyecto.models.PlanEstudio.where("vigente = 1").toMaps());

        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);

        return new ModelAndView(model, "estudiante_gestion.mustache");
    }

    private String handleCreateEstudiante(Request req, Response res) {
        String dniStr = req.queryParams("dni");
        String codEstudianteStr = req.queryParams("cod_estudiante");
        String carreraIdStr = req.queryParams("carrera_id");
        String planEstudioIdStr = req.queryParams("plan_estudio_id");

        if (dniStr == null || dniStr.isEmpty() || codEstudianteStr == null || codEstudianteStr.isEmpty()) {
            res.redirect("/estudiantes?error=" + URLEncoder.encode("DNI y Código de Estudiante son obligatorios.", StandardCharsets.UTF_8));
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

            Integer planEstudioId = (planEstudioIdStr != null && !planEstudioIdStr.isEmpty()) ? Integer.parseInt(planEstudioIdStr) : null;
            Integer carreraId = (carreraIdStr != null && !carreraIdStr.isEmpty()) ? Integer.parseInt(carreraIdStr) : null;

            // Restricción: Validar que el plan sea vigente
            if (planEstudioId != null) {
                com.is1.proyecto.models.PlanEstudio plan = com.is1.proyecto.models.PlanEstudio.findById(planEstudioId);
                if (plan == null) {
                    res.redirect("/estudiantes?error=" + URLEncoder.encode("El plan de estudio seleccionado no existe.", StandardCharsets.UTF_8));
                    return "";
                }
                if (plan.getInteger("vigente") != 1) {
                    res.redirect("/estudiantes?error=" + URLEncoder.encode("Restricción: No se puede matricular a un estudiante en un plan de estudio no vigente.", StandardCharsets.UTF_8));
                    return "";
                }
            }

            Estudiante est = new Estudiante();
            est.setDni(Integer.parseInt(dniStr));
            est.setCodEstudiante(Integer.parseInt(codEstudianteStr));
            if (carreraId != null) est.set("carrera_id", carreraId);
            if (planEstudioId != null) est.set("plan_estudio_id", planEstudioId);
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

        // Cargar carreras marcando la seleccionada
        List<Map<String, Object>> carrerasList = com.is1.proyecto.models.Carrera.findAll().toMaps();
        for (Map<String, Object> carr : carrerasList) {
            if (est.get("carrera_id") != null && ((Number) est.get("carrera_id")).intValue() == ((Number) carr.get("id")).intValue()) {
                carr.put("selected", true);
            }
        }
        model.put("carreras", carrerasList);

        // Cargar todos los planes, agregando texto (No Vigente) si aplica, y marcando el seleccionado
        List<Map<String, Object>> planesList = com.is1.proyecto.models.PlanEstudio.findAll().toMaps();
        for (Map<String, Object> plan : planesList) {
            int vigente = ((Number) plan.get("vigente")).intValue();
            plan.put("vigente_text", vigente == 1 ? "" : " (No Vigente)");
            if (est.get("plan_estudio_id") != null && ((Number) est.get("plan_estudio_id")).intValue() == ((Number) plan.get("id")).intValue()) {
                plan.put("selected", true);
            }
        }
        model.put("planes", planesList);

        return new ModelAndView(model, "estudiante_edit.mustache");
    }

    private String handleEditEstudiante(Request req, Response res) {
        Estudiante est = Estudiante.findById(req.params(":id"));
        String nuevoCodStr = req.queryParams("cod_estudiante");
        String carreraIdStr = req.queryParams("carrera_id");
        String planEstudioIdStr = req.queryParams("plan_estudio_id");

        if (est != null && nuevoCodStr != null) {
            try {
                Estudiante duplicado = Estudiante.findFirst("cod_estudiante = ? AND id != ?", nuevoCodStr, est.getId());
                if (duplicado != null) {
                    res.redirect("/estudiantes?error=" + URLEncoder.encode("Ese código de estudiante ya se encuentra en uso.", StandardCharsets.UTF_8));
                    return "";
                }

                Integer planEstudioId = (planEstudioIdStr != null && !planEstudioIdStr.isEmpty()) ? Integer.parseInt(planEstudioIdStr) : null;
                Integer carreraId = (carreraIdStr != null && !carreraIdStr.isEmpty()) ? Integer.parseInt(carreraIdStr) : null;

                // Restricción: Si cambió el plan o es nuevo, validar que sea vigente
                if (planEstudioId != null) {
                    Object currentPlanId = est.get("plan_estudio_id");
                    if (currentPlanId == null || ((Number) currentPlanId).intValue() != planEstudioId) {
                        com.is1.proyecto.models.PlanEstudio plan = com.is1.proyecto.models.PlanEstudio.findById(planEstudioId);
                        if (plan == null) {
                            res.redirect("/estudiantes?error=" + URLEncoder.encode("El plan de estudio seleccionado no existe.", StandardCharsets.UTF_8));
                            return "";
                        }
                        if (plan.getInteger("vigente") != 1) {
                            res.redirect("/estudiantes?error=" + URLEncoder.encode("Restricción: No se puede matricular a un estudiante en un plan de estudio no vigente.", StandardCharsets.UTF_8));
                            return "";
                        }
                    }
                }

                est.setCodEstudiante(Integer.parseInt(nuevoCodStr));
                est.set("carrera_id", carreraId);
                est.set("plan_estudio_id", planEstudioId);
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

    public static ModelAndView mostrarDashboard(Request req, Response res) {
        HashMap<String, Object> model = new HashMap<>();
        String username = req.session().attribute("currentUserUsername");
        
        if (username == null) {
            res.redirect("/");
            return null;
        }

        try {
            // 1. Obtener los datos del estudiante y cruzarlos con Persona y Carrera
            String sqlEstudiante = "SELECT e.id, e.dni, e.cod_estudiante, p.nombre, p.apellido, " +
                                   "c.nombre AS carrera_nombre, pe.nombre AS plan_nombre " +
                                   "FROM estudiante e " +
                                   "JOIN person p ON e.dni = p.dni " +
                                   "LEFT JOIN carrera c ON e.carrera_id = c.id " +
                                   "LEFT JOIN planes_estudio pe ON e.plan_estudio_id = pe.id " +
                                   "WHERE e.dni = ?";
            var estudiantesList = Base.findAll(sqlEstudiante, username);
            if (estudiantesList.isEmpty()) {
                res.redirect("/logout");
                return null;
            }
            
            Map<String, Object> estudiante = estudiantesList.get(0);
            model.putAll(estudiante); // Carga nombre, apellido, dni, cod_estudiante, carrera_nombre, plan_nombre, id
            
            Object estudianteId = estudiante.get("id");
            model.put("estudiante_id", estudianteId);

            // 2. Obtener materias aprobadas (examen_final con nota >= 4.0)
            String sqlAprobadas = "SELECT m.codigo_materia, m.nombre AS materia_nombre, ef.nota, ef.fecha " +
                                  "FROM examen_final ef " +
                                  "JOIN materia m ON ef.materia_id = m.id " +
                                  "WHERE ef.estudiante_id = ? AND ef.nota >= 4.0 " +
                                  "ORDER BY ef.fecha DESC";
            java.util.List<Map> aprobadasList = Base.findAll(sqlAprobadas, estudianteId);
            model.put("aprobadas", aprobadasList);

            // 3. Obtener inscripciones de cursadas activas
            String sqlCursadas = "SELECT m.codigo_materia, m.nombre AS materia_nombre, c.periodo " +
                                 "FROM cursadas c " +
                                 "JOIN materia m ON c.materia_id = m.id " +
                                 "WHERE c.estudiante_id = ?";
            java.util.List<Map> cursadasList = Base.findAll(sqlCursadas, estudianteId);
            model.put("cursadas", cursadasList);

        } catch (Exception e) {
            System.err.println("Error al cargar el dashboard del estudiante: " + e.getMessage());
            e.printStackTrace();
        }

        return new ModelAndView(model, "estudiante/dashboard_estudiante.mustache");
    }
}
