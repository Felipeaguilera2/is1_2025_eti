package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.is1.proyecto.models.Persona;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class PersonaController {
    public PersonaController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/person/new", this::showCreatePersona, mustache);
        post("/person/new", this::handleCreatePersona);
    }

    private ModelAndView showCreatePersona(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();

        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }

        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }

        String returnTo = req.queryParams("returnTo");
        if (returnTo != null && !returnTo.isEmpty()) {
            model.put("returnTo", returnTo);
        }        

        return new ModelAndView(model, "person_form.mustache");
    }

    private String handleCreatePersona(Request req, Response res) {
        String name = req.queryParams("name");
        String apellido = req.queryParams("apellido");
        String dni = req.queryParams("dni");
        String correo = req.queryParams("correo");
        String returnTo = req.queryParams("returnTo");

        if (name == null || name.isEmpty() || apellido == null || apellido.isEmpty() 
            || dni == null || dni.isEmpty() || correo == null || correo.isEmpty()) {
            res.status(400);
            String msg = URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8);
            
            String dest = "/person/new?error=" + msg;
            if (returnTo != null && !returnTo.isEmpty()) {
                dest += "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
            }
            res.redirect(dest);
            return "";
        }

        Integer personaDni;
        try {
            personaDni = Integer.valueOf(dni);
        } catch (NumberFormatException e) {
            res.status(400);
            String msg = URLEncoder.encode("El DNI debe ser un número válido.", StandardCharsets.UTF_8);
            
            String dest = "/person/new?error=" + msg;
            if (returnTo != null && !returnTo.isEmpty()) {
                dest += "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
            }
            res.redirect(dest);
            return "";
        }

        try {
            if (Persona.findFirst("dni = ?", dni) != null) {
                res.status(409);
                String msg = URLEncoder.encode("El DNI " + DniFormated(dni) + " ya se encuentra registrado.", StandardCharsets.UTF_8);
                String dest = "/person/new?error=" + msg;
                if (returnTo != null && !returnTo.isEmpty()) {
                    dest += "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
                }
                res.redirect(dest);
                return "";
            }
            
            if (Persona.findFirst("correo = ?", correo) != null) {
                res.status(409);
                String msg = URLEncoder.encode("El correo electrónico ya se encuentra registrado.", StandardCharsets.UTF_8);
                String dest = "/person/new?error=" + msg;
                if (returnTo != null && !returnTo.isEmpty()) {
                    dest += "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
                }
                res.redirect(dest);
                return "";
            }

            Persona newPersona = new Persona();
            newPersona.set("nombre", name);
            newPersona.set("apellido", apellido);
            newPersona.setDni(personaDni);
            newPersona.set("correo", correo); 
            newPersona.saveIt(); 

            res.status(201);
            String mensajeExito = "Persona " + name + " " + apellido + " registrada con éxito.";
            String msgEncoded = URLEncoder.encode(mensajeExito, StandardCharsets.UTF_8);
            if (returnTo != null && !returnTo.isEmpty()) {
                String separador = returnTo.contains("?") ? "&" : "?";
                res.redirect(returnTo + separador + "message=" + msgEncoded);
            } else {
                res.redirect("/person/new?message=" + msgEncoded);
            }
            return "";

        } catch (Exception e) {
            System.err.println("Error al registrar la persona: " + e.getMessage());
            e.printStackTrace(); 
            res.status(500);
            String msg = URLEncoder.encode("Error interno al crear la persona. Intente de nuevo.", StandardCharsets.UTF_8);
            res.redirect("/person/new?error=" + msg);
            return ""; 
        }
    }

    private String DniFormated(String dni) {
        return dni != null ? dni.trim() : "";
    }
}
