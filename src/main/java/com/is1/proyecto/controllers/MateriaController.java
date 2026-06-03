package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.is1.proyecto.models.Materia;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class MateriaController {
    public MateriaController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/materias", this::listMaterias, mustache);
        post("/materias/new", this::handleCreateMateria);
        get("/materias/edit/:id", this::showEditMateria, mustache);
        post("/materias/edit/:id", this::handleEditMateria);
        get("/materias/delete/:id", this::handleDeleteMateria);
    }

    private ModelAndView listMaterias(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        model.put("materias", Materia.findAll().toMaps());            
        String error = req.queryParams("error");
        if (error != null) model.put("errorMessage", error);
        String msg = req.queryParams("message");
        if (msg != null) model.put("successMessage", msg);        
        return new ModelAndView(model, "materia_gestion.mustache");
    }

    private String handleCreateMateria(Request req, Response res) {
        String codigoStr = req.queryParams("codigo_materia");
        String nombre = req.queryParams("nombre");
        String plan = req.queryParams("plan_materia");
        String[] correlativasSeleccionadas = req.queryParamsValues("correlativas");
        String obligatoriaParam = req.queryParams("es_obligatoria");
        int esObligatoria = (obligatoriaParam != null && obligatoriaParam.equals("on")) ? 1 : 0;

        try {
            if (Materia.findFirst("codigo_materia = ?", codigoStr) != null) {
                res.redirect("/materias?error=" + URLEncoder.encode("El código de materia ya existe.", StandardCharsets.UTF_8));
                return "";
            }

            Materia m = new Materia();
            m.set("codigo_materia", Integer.valueOf(codigoStr));
            m.set("nombre", nombre);
            m.set("plan_materia", plan);
            m.set("es_obligatoria", esObligatoria);
            m.saveIt();

            if (correlativasSeleccionadas != null) {
                for (String idCorr : correlativasSeleccionadas) {
                    m.agregarCorrelativa(Integer.valueOf(idCorr));
                }
            }

            // Recargar en memoria el grafo de correlatividades
            com.is1.proyecto.CorrelatividadesManager.getInstance().reload();

            res.redirect("/materias?message=" + URLEncoder.encode("Materia creada con éxito.", StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error al crear materia: " + e.getMessage());
            res.redirect("/materias?error=" + URLEncoder.encode("Error interno al crear la materia.", StandardCharsets.UTF_8));
        }
        return "";
    }

    private ModelAndView showEditMateria(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        Materia m = Materia.findById(req.params(":id"));
        if (m == null) {
            res.redirect("/materias?error=Materia no encontrada");
            return null;
        }
        model.put("materia", m.toMap());

        java.util.List<Materia> correlativas = m.getCorrelativas();
        java.util.Set<Integer> correlativasIds = new java.util.HashSet<>();
        for (Materia c : correlativas) {
            correlativasIds.add(((Number) c.getId()).intValue());
        }

        java.util.List<java.util.Map<String, Object>> todasMateriasMaps = Materia.where("id != ?", m.getId()).toMaps();
        for (java.util.Map<String, Object> map : todasMateriasMaps) {
            int id = ((Number) map.get("id")).intValue();
            if (correlativasIds.contains(id)) {
                map.put("checked", true);
            }
        }
        model.put("todasMaterias", todasMateriasMaps);

        String error = req.queryParams("error");
        if (error != null) {
            model.put("errorMessage", error);
        }

        return new ModelAndView(model, "materia_edit.mustache");
    }

    private String handleEditMateria(Request req, Response res) {
        Materia m = Materia.findById(req.params(":id"));
        if (m != null) {
            try {
                String obligatoriaParam = req.queryParams("es_obligatoria");
                int esObligatoria = (obligatoriaParam != null && obligatoriaParam.equals("on")) ? 1 : 0;
                
                String[] correlativasSeleccionadas = req.queryParamsValues("correlativas");
                int thisMateriaId = ((Number) m.getId()).intValue();

                // Verificar que no se generen ciclos de correlatividades
                if (correlativasSeleccionadas != null) {
                    for (String idCorr : correlativasSeleccionadas) {
                        int prospectiveCorrelativaId = Integer.parseInt(idCorr);
                        if (com.is1.proyecto.CorrelatividadesManager.getInstance().checkCycle(thisMateriaId, prospectiveCorrelativaId)) {
                            res.redirect("/materias/edit/" + m.getId() + "?error=" + URLEncoder.encode("Error: Agregar esta correlativa genera una dependencia circular.", StandardCharsets.UTF_8));
                            return "";
                        }
                    }
                }

                m.set("nombre", req.queryParams("nombre"));
                m.set("plan_materia", req.queryParams("plan_materia"));
                m.set("es_obligatoria", esObligatoria);
                m.saveIt();

                m.borrarCorrelatividades();
                if (correlativasSeleccionadas != null) {
                    for (String idCorr : correlativasSeleccionadas) {
                        m.agregarCorrelativa(Integer.valueOf(idCorr));
                    }
                }

                // Recargar el manager de correlativas en memoria
                com.is1.proyecto.CorrelatividadesManager.getInstance().reload();

                res.redirect("/materias?message=" + URLEncoder.encode("Materia modificada correctamente.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("Error al editar materia: " + e.getMessage());
                res.redirect("/materias?error=" + URLEncoder.encode("Error interno al editar la materia.", StandardCharsets.UTF_8));
            }
        } else {
            res.redirect("/materias?error=Error al modificar.");
        }
        return "";
    }

    private String handleDeleteMateria(Request req, Response res) {
        Materia m = Materia.findById(req.params(":id"));
        if (m != null) {
            try {
                m.borrarCorrelatividades();
                m.delete();
                
                // Recargar el manager de correlativas en memoria
                com.is1.proyecto.CorrelatividadesManager.getInstance().reload();

                res.redirect("/materias?message=" + URLEncoder.encode("Materia y correlatividades eliminadas.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("Error al borrar materia: " + e.getMessage());
                res.redirect("/materias?error=" + URLEncoder.encode("Error interno al borrar la materia.", StandardCharsets.UTF_8));
            }
        } else {
            res.redirect("/materias?error=No se pudo eliminar la materia.");
        }
        return "";
    }
}
