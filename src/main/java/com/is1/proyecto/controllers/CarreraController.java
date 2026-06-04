package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Carrera;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class CarreraController {
    public CarreraController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/carrera/new", this::showCreateCarrera, mustache);
        post("/carrera/create", this::handleCreateCarrera, mustache);
        get("/carreras", this::listCarreras, mustache);

        // Nuevas rutas de Planes de Estudio
        get("/carrera/:id/planes", this::listPlanesEstudio, mustache);
        get("/carrera/:id/gestion-plan", this::showGestionPlan, mustache);
        post("/carrera/:id/planes/new", this::handleCreatePlan);
        post("/carrera/:id/planes/edit/:plan_id", this::handleEditPlan);

        // Gestión de Materias vinculadas al Plan de Estudio
        get("/carrera/:id/planes/:plan_id/materias", this::showPlanMaterias, mustache);
        post("/plan/materia/add", this::handleAddPlanMateria);
        get("/plan/:plan_id/materia/:materia_id/delete", this::handleDeletePlanMateria);
    }

    private ModelAndView showCreateCarrera(Request req, Response res) {
        return new ModelAndView(new HashMap<>(), "carrera_form.mustache");
    }

    private ModelAndView handleCreateCarrera(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String codigo = req.queryParams("codigo");
        String nombre = req.queryParams("nombre");

        try {
            if (codigo == null || codigo.trim().isEmpty() || nombre == null || nombre.trim().isEmpty()) {
                model.put("error", "Todos los campos son obligatorios.");
                return new ModelAndView(model, "carrera_form.mustache");
            }

            Carrera carrera = new Carrera();
            carrera.set("codigo", codigo);
            carrera.set("nombre", nombre);
            carrera.saveIt();

            model.put("success", true);
        } catch (Exception e) {
            model.put("error", "Error al guardar. Es posible que el código ya exista.");
        }

        return new ModelAndView(model, "carrera_form.mustache");
    }

    private ModelAndView listCarreras(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        model.put("carreras", Carrera.findAll().toMaps());
        return new ModelAndView(model, "carreras_list.mustache");
    }

    private ModelAndView listPlanesEstudio(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String carreraId = req.params(":id");
        Carrera carrera = Carrera.findById(carreraId);
        if (carrera == null) {
            res.redirect("/carreras?error=Carrera no encontrada");
            return null;
        }
        model.put("carrera", carrera.toMap());
        
        List<Map<String, Object>> planesList = com.is1.proyecto.models.PlanEstudio.where("carrera_id = ?", carreraId).toMaps();
        for (Map<String, Object> p : planesList) {
            int vigente = ((Number) p.get("vigente")).intValue();
            p.put("vigente_text", vigente == 1);
        }
        model.put("planes", planesList);
        return new ModelAndView(model, "carrera_planes.mustache");
    }

    private ModelAndView showGestionPlan(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String carreraId = req.params(":id");
        Carrera carrera = Carrera.findById(carreraId);
        if (carrera == null) {
            res.redirect("/carreras?error=Carrera no encontrada");
            return null;
        }
        model.put("carrera", carrera.toMap());

        List<Map<String, Object>> planesList = com.is1.proyecto.models.PlanEstudio.where("carrera_id = ?", carreraId).toMaps();
        for (Map<String, Object> p : planesList) {
            int vigente = ((Number) p.get("vigente")).intValue();
            p.put("vigente_text", vigente == 1);
        }
        model.put("planes", planesList);
        
        String planId = req.queryParams("edit_plan_id");
        if (planId != null && !planId.isEmpty()) {
            com.is1.proyecto.models.PlanEstudio planToEdit = com.is1.proyecto.models.PlanEstudio.findById(planId);
            if (planToEdit != null) {
                Map<String, Object> planMap = planToEdit.toMap();
                int vigente = ((Number) planMap.get("vigente")).intValue();
                planMap.put("vigente_checked", vigente == 1);
                model.put("editPlan", planMap);
            }
        }
        
        String error = req.queryParams("error");
        if (error != null) model.put("error", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("success", msg);

        return new ModelAndView(model, "carrera_gestion_plan.mustache");
    }

    private String handleCreatePlan(Request req, Response res) {
        String carreraId = req.params(":id");
        String nombre = req.queryParams("nombre");
        String codigo = req.queryParams("codigo");

        try {
            if (nombre == null || nombre.trim().isEmpty() || codigo == null || codigo.trim().isEmpty()) {
                res.redirect("/carrera/" + carreraId + "/gestion-plan?error=Campos obligatorios vacios");
                return "";
            }

            com.is1.proyecto.models.PlanEstudio plan = new com.is1.proyecto.models.PlanEstudio();
            plan.set("nombre", nombre.trim());
            plan.set("codigo", codigo.trim());
            plan.set("carrera_id", Integer.valueOf(carreraId));
            plan.set("version", 1); // Versión inicial
            plan.set("vigente", 1); // Activo por defecto
            plan.saveIt();

            res.redirect("/carrera/" + carreraId + "/gestion-plan?message=Plan creado con éxito");
        } catch (Exception e) {
            res.redirect("/carrera/" + carreraId + "/gestion-plan?error=Error al crear el plan.");
        }
        return "";
    }

    private String handleEditPlan(Request req, Response res) {
        String carreraId = req.params(":id");
        String planId = req.params(":plan_id");
        String nombre = req.queryParams("nombre");
        String codigo = req.queryParams("codigo");
        String vigenteParam = req.queryParams("vigente");
        int vigente = (vigenteParam != null && vigenteParam.equals("on")) ? 1 : 0;

        try {
            com.is1.proyecto.models.PlanEstudio plan = com.is1.proyecto.models.PlanEstudio.findById(planId);
            if (plan == null) {
                res.redirect("/carrera/" + carreraId + "/gestion-plan?error=Plan no encontrado");
                return "";
            }

            if (nombre != null && !nombre.trim().isEmpty()) {
                plan.set("nombre", nombre.trim());
            }
            if (codigo != null && !codigo.trim().isEmpty()) {
                plan.set("codigo", codigo.trim());
            }
            plan.set("vigente", vigente);

            // Lógica de negocio específica: Autoincrementar version
            Integer currentVersion = plan.getInteger("version");
            if (currentVersion == null) {
                currentVersion = 1;
            }
            plan.set("version", currentVersion + 1);
            plan.saveIt();

            res.redirect("/carrera/" + carreraId + "/gestion-plan?message=Plan actualizado a version " + (currentVersion + 1));
        } catch (Exception e) {
            res.redirect("/carrera/" + carreraId + "/gestion-plan?error=Error al actualizar el plan.");
        }
        return "";
    }

    private ModelAndView showPlanMaterias(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String carreraId = req.params(":id");
        String planId = req.params(":plan_id");

        com.is1.proyecto.models.PlanEstudio plan = com.is1.proyecto.models.PlanEstudio.findById(planId);
        if (plan == null) {
            res.redirect("/carrera/" + carreraId + "/gestion-plan?error=Plan no encontrado");
            return null;
        }

        model.put("plan", plan.toMap());
        model.put("carrera_id", carreraId);

        // Materias vinculadas a este plan
        String sqlPlanMaterias = "SELECT m.id, m.codigo_materia, m.nombre, pm.anio_cursado, pm.cuatrimestre " +
                                 "FROM plan_materias pm JOIN materia m ON pm.materia_id = m.id " +
                                 "WHERE pm.plan_estudio_id = ?";
        model.put("planMaterias", Base.findAll(sqlPlanMaterias, planId));

        // Todas las materias de esta carrera
        model.put("todasMaterias", com.is1.proyecto.models.Materia.where("carrera_id = ?", carreraId).toMaps());

        // Mensajes de feedback
        String error = req.queryParams("error");
        if (error != null) model.put("error", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("message", msg);

        return new ModelAndView(model, "plan_materias_gestion.mustache");
    }

    private String handleAddPlanMateria(Request req, Response res) {
        String planId = req.queryParams("plan_id");
        String carreraId = req.queryParams("carrera_id");
        String materiaId = req.queryParams("materia_id");
        String anioCursadoStr = req.queryParams("anio_cursado");
        String cuatrimestreStr = req.queryParams("cuatrimestre");

        try {
            if (planId == null || materiaId == null || anioCursadoStr == null || cuatrimestreStr == null) {
                res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?error=" + URLEncoder.encode("Campos incompletos", StandardCharsets.UTF_8));
                return "";
            }

            int anio = Integer.parseInt(anioCursadoStr);
            int cuatrimestre = Integer.parseInt(cuatrimestreStr);

            // Verificar si ya está vinculada
            com.is1.proyecto.models.PlanMateria existente = com.is1.proyecto.models.PlanMateria.findFirst(
                "plan_estudio_id = ? AND materia_id = ?", planId, materiaId
            );
            if (existente != null) {
                res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?error=" + URLEncoder.encode("La materia ya está vinculada a este plan", StandardCharsets.UTF_8));
                return "";
            }

            com.is1.proyecto.models.PlanMateria pm = new com.is1.proyecto.models.PlanMateria();
            pm.set("plan_estudio_id", Integer.parseInt(planId));
            pm.set("materia_id", Integer.parseInt(materiaId));
            pm.set("anio_cursado", anio);
            pm.set("cuatrimestre", cuatrimestre);
            pm.saveIt();

            res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?message=" + URLEncoder.encode("Materia vinculada con éxito", StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?error=" + URLEncoder.encode("Error al vincular materia", StandardCharsets.UTF_8));
        }
        return "";
    }

    private String handleDeletePlanMateria(Request req, Response res) {
        String planId = req.params(":plan_id");
        String materiaId = req.params(":materia_id");
        String carreraId = req.queryParams("carrera_id");

        try {
            com.is1.proyecto.models.PlanMateria pm = com.is1.proyecto.models.PlanMateria.findFirst(
                "plan_estudio_id = ? AND materia_id = ?", planId, materiaId
            );
            if (pm != null) {
                pm.delete();
                res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?message=" + URLEncoder.encode("Materia desvinculada del plan", StandardCharsets.UTF_8));
            } else {
                res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?error=" + URLEncoder.encode("Registro no encontrado", StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            res.redirect("/carrera/" + carreraId + "/planes/" + planId + "/materias?error=" + URLEncoder.encode("Error al desvincular materia", StandardCharsets.UTF_8));
        }
        return "";
    }
}
