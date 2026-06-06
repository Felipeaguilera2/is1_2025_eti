package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.util.HashMap;
import java.util.Map;

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
        // Importamos com.is1.proyecto.models.PlanEstudio
        model.put("planes", com.is1.proyecto.models.PlanEstudio.where("carrera_id = ?", carreraId).toMaps());
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
        model.put("planes", com.is1.proyecto.models.PlanEstudio.where("carrera_id = ?", carreraId).toMaps());
        
        String planId = req.queryParams("edit_plan_id");
        if (planId != null && !planId.isEmpty()) {
            com.is1.proyecto.models.PlanEstudio planToEdit = com.is1.proyecto.models.PlanEstudio.findById(planId);
            if (planToEdit != null) {
                model.put("editPlan", planToEdit.toMap());
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
}
