package com.is1.proyecto.controllers;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.halt;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.models.User;
import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.Profesor;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.mustache.MustacheTemplateEngine;

public class AuthController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AuthController() {
        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/", this::showLogin, mustache);
        get("/login", this::showLogin, mustache);
        post("/login", this::handleLogin, mustache);
        get("/user/create", this::showCreateUser, mustache);
        get("/user/new", this::showNewUser, mustache);
        post("/user/new", this::handleCreateUser);
        post("/add_users", this::handleAddUsers);
        get("/logout", this::handleLogout);
        get("/dashboard", this::showDashboard, mustache);
    }

    private ModelAndView showLogin(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        return new ModelAndView(model, "login.mustache");
    }

    private ModelAndView handleLogin(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();

        String username = req.queryParams("username");
        String plainTextPassword = req.queryParams("password");

        if (username == null || username.isEmpty() || plainTextPassword == null || plainTextPassword.isEmpty()) {
            res.status(400);
            model.put("errorMessage", "El nombre de usuario y la contraseña son requeridos.");
            return new ModelAndView(model, "login.mustache");
        }

        User ac = User.findFirst("name = ?", username);

        if (ac == null) {
            res.status(401);
            model.put("errorMessage", "Usuario o contraseña incorrectos.");
            return new ModelAndView(model, "login.mustache");
        }

        String storedHashedPassword = ac.getString("password");
        if (BCrypt.checkpw(plainTextPassword, storedHashedPassword)) {
            res.status(200);
            req.session(true).attribute("currentUserUsername", username);
            req.session().attribute("userId", ac.getId());
            req.session().attribute("loggedIn", true);

            String rolBD = ac.getString("rol");
            System.out.println("DEBUG: Login exitoso para la cuenta: " + username + " con rol: " + rolBD);
            System.out.println("DEBUG: ID de Sesión: " + req.session().id());

            if ("dual".equals(rolBD)) {
                req.session().attribute("rolTemporal", "dual");
                res.redirect("/seleccionar-perfil");
                halt();
                return null;
            } else if ("admin".equals(rolBD)) {
                req.session().attribute("rol", "admin");
                res.redirect("/dashboard");
                halt();
                return null;
            } else if ("estudiante".equals(rolBD)) {
                req.session().attribute("rol", "estudiante");
                res.redirect("/estudiante/dashboard");
                halt();
                return null;
            } else if ("profesor".equals(rolBD)) {
                req.session().attribute("rol", "profesor");
                res.redirect("/profesor/dashboard");
                halt();
                return null;
            } else {
                req.session().attribute("rol", rolBD);
                res.redirect("/dashboard");
                halt();
                return null;
            }
        } else {
            res.status(401);
            System.out.println("DEBUG: Intento de login fallido para: " + username);
            model.put("errorMessage", "Usuario o contraseña incorrectos.");
            return new ModelAndView(model, "login.mustache");
        }
    }

    private ModelAndView showCreateUser(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }
        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }
        return new ModelAndView(model, "user_form.mustache");
    }

    private ModelAndView showNewUser(Request req, Response res) {
        return new ModelAndView(new HashMap<>(), "user_form.mustache");
    }

    private String handleCreateUser(Request req, Response res) {
        String dni = req.queryParams("dni");
        String password = req.queryParams("password");

        if (dni == null || dni.isEmpty() || password == null || password.isEmpty()) {
            res.status(400);
            res.redirect("/user/create?error=" + URLEncoder.encode("DNI y contraseña son requeridos.", StandardCharsets.UTF_8));
            return "";
        }

        try {
            String rol = null;
            Estudiante estudiante = Estudiante.findFirst("dni = ?", dni);
            Profesor profesor = Profesor.findFirst("dni = ?", dni);

            if (estudiante != null && profesor != null) {
                rol = "dual";
            } else if (estudiante != null) {
                rol = "estudiante";
            } else if (profesor != null) {
                rol = "profesor";
            }

            if (rol == null) {
                res.status(400);
                res.redirect("/user/create?error=" + URLEncoder.encode("Usted no está registrado en el padrón de la institución", StandardCharsets.UTF_8));
                return "";
            }

            User ac = new User();
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            ac.set("name", dni);
            ac.set("persona_dni", Integer.parseInt(dni));
            ac.set("rol", rol);
            ac.set("password", hashedPassword);
            ac.saveIt();

            res.status(201);
            res.redirect("/?message=" + URLEncoder.encode("Cuenta creada exitosamente para " + dni + "!", StandardCharsets.UTF_8));
            return "";
        } catch (Exception e) {
            System.err.println("Error al registrar la cuenta: " + e.getMessage());
            e.printStackTrace();
            res.status(500);
            res.redirect("/user/create?error=" + URLEncoder.encode("Error interno al crear la cuenta. Intente de nuevo.", StandardCharsets.UTF_8));
            return "";
        }
    }

    private String handleAddUsers(Request req, Response res) {
        res.type("application/json");
        String name = req.queryParams("name");
        String password = req.queryParams("password");

        if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
            res.status(400);
            try {
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            } catch (Exception e) {
                return "{\"error\": \"Nombre y contraseña son requeridos.\"}";
            }
        }

        try {
            User newUser = new User();
            newUser.set("name", name);
            newUser.set("password", password);
            newUser.saveIt();

            res.status(201);
            return objectMapper.writeValueAsString(Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));
        } catch (Exception e) {
            System.err.println("Error al registrar usuario: " + e.getMessage());
            e.printStackTrace();
            res.status(500);
            try {
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar usuario: " + e.getMessage()));
            } catch (Exception ex) {
                return "{\"error\": \"Error interno.\"}";
            }
        }
    }

    private String handleLogout(Request req, Response res) {
        req.session().removeAttribute("currentUserUsername");
        req.session().removeAttribute("loggedIn");
        req.session().invalidate();
        System.out.println("DEBUG: Sesión cerrada. Redirigiendo a login.");
        res.redirect("/?message=" + URLEncoder.encode("Has cerrado sesión exitosamente.", StandardCharsets.UTF_8));
        return null;
    }

    private ModelAndView showDashboard(Request req, Response res) {
        Map<String, Object> model = new HashMap<>();
        String currentUsername = req.session().attribute("currentUserUsername");
        Boolean loggedIn = req.session().attribute("loggedIn");

        if (currentUsername == null || loggedIn == null || !loggedIn) {
            System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
            res.redirect("/?error=Debes iniciar sesión para acceder a esta página.");
            halt();
            return null;
        }

        model.put("username", currentUsername);

        String successMessage = req.queryParams("message");
        if (successMessage != null && !successMessage.isEmpty()) {
            model.put("successMessage", successMessage);
        }

        String errorMessage = req.queryParams("error");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            model.put("errorMessage", errorMessage);
        }

        return new ModelAndView(model, "dashboard.mustache");
    }

    public static ModelAndView mostrarSeleccionPerfil(Request req, Response res) {
        return new ModelAndView(new HashMap<>(), "seleccionar_perfil.mustache");
    }

    public static Object setPerfil(Request req, Response res) {
        String tipo = req.params(":tipo");
        String rolTemporal = req.session().attribute("rolTemporal");
        if ("dual".equals(rolTemporal)) {
            req.session().attribute("rol", tipo);
            req.session().removeAttribute("rolTemporal");
            res.redirect("/" + tipo + "/dashboard");
        } else {
            res.redirect("/");
        }
        return null;
    }
}
