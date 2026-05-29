package com.is1.proyecto; // Define el paquete de la aplicación, debe coincidir con la estructura de carpetas.

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner; 

import org.javalite.activejdbc.Base; // Clase central de ActiveJDBC para gestionar la conexión a la base de datos.
import org.mindrot.jbcrypt.BCrypt; // Utilidad para hashear y verificar contraseñas de forma segura.

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is1.proyecto.config.DBConfigSingleton;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.Profesor;
import com.is1.proyecto.models.User; // IMPORTANTE: Agregamos el import de tu nuevo modelo

import spark.ModelAndView;
import static spark.Spark.after;
import static spark.Spark.before; 
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;
import static spark.Spark.post;
import spark.template.mustache.MustacheTemplateEngine;

/**
 * Clase principal de la aplicación Spark.
 * Configura las rutas, filtros y el inicio del servidor web.
 */
public class App {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Método principal que se ejecuta al iniciar la aplicación.
     * Aquí se configuran todas las rutas y filtros de Spark.
     */
    public static void main(String[] args) {
        port(8080); // Configura el puerto del servidor web.

        // Obtener la instancia única del singleton de configuración de la base de datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();

        // Inicialización limpia de la Base de Datos al arrancar la App
        try {
            Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
            InputStream is = App.class.getResourceAsStream("/scheme.sql");
            if (is != null) {
                Scanner s = new Scanner(is).useDelimiter("\\A");
                String sql = s.hasNext() ? s.next() : "";
                Base.exec(sql);
                System.out.println(">>> BASE DE DATOS INICIALIZADA CON TABLAS <<<");
            } else {
                System.err.println(">>> ERROR: No se encontró scheme.sql <<<");
            }
        } catch (Exception e) {
            System.err.println(">>> Error al inicializar la DB: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Base.close(); 
        }

        // --- Rutas GET para renderizar formularios y páginas HTML ---

        // GET: Raíz / Inicio de sesión (login)
        get("/", (req, res) -> {
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
        }, new MustacheTemplateEngine());

        // GET: Muestra el formulario de creación de cuenta de usuario.
        get("/user/create", (req, res) -> {
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
        }, new MustacheTemplateEngine());

        // GET: Alias alternativo para el formulario de creación de cuenta
        get("/user/new", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "user_form.mustache");
        }, new MustacheTemplateEngine());

        // GET: Panel de Control (Dashboard) - Requiere sesión activa
        get("/dashboard", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            if (currentUsername == null || loggedIn == null || !loggedIn) {
                System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
                res.redirect("/?error=Debes iniciar sesión para acceder a esta página.");
                return null;
            }

            model.put("username", currentUsername);
            return new ModelAndView(model, "dashboard.mustache");
        }, new MustacheTemplateEngine());

        // GET: Cierre de Sesión (Logout)
        get("/logout", (req, res) -> {
            req.session().invalidate();
            System.out.println("DEBUG: Sesión cerrada. Redirigiendo a /login.");
            res.redirect("/");
            return null;
        });

        // GET: Formulario para iniciar sesión como profesor
        get("/profesor/login", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "professor_login.mustache");
        }, new MustacheTemplateEngine());

        // GET: Muestra el formulario para registrar una nueva Persona
        get("/person/new", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); 
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            return new ModelAndView(model, "person_form.mustache");
        }, new MustacheTemplateEngine());


        // GET: Listar materias en el panel principal
        get("/materias", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("materias", Materia.findAll().toMaps());
            
            String error = req.queryParams("error");
            if (error != null) model.put("errorMessage", error);
            String msg = req.queryParams("message");
            if (msg != null) model.put("successMessage", msg);

            return new ModelAndView(model, "materia_gestion.mustache");
        }, new MustacheTemplateEngine());

        // POST: Registrar el Alta de una nueva Materia
        post("/materias/new", (req, res) -> {
            String codigoStr = req.queryParams("codigo_materia");
            String nombre = req.queryParams("nombre");
            String plan = req.queryParams("plan_materia");
            String[] correlativasSeleccionadas = req.queryParamsValues("correlativas");

            if (Materia.findFirst("codigo_materia = ?", codigoStr) != null) {
                res.redirect("/materias?error=" + URLEncoder.encode("El código de materia ya existe.", StandardCharsets.UTF_8));
                return "";
            }

            Materia m = new Materia();
            m.set("codigo_materia", Integer.valueOf(codigoStr));
            m.set("nombre", nombre);
            m.set("plan_materia", plan);
            m.saveIt();

            if (correlativasSeleccionadas != null) {
                for (String idCorr : correlativasSeleccionadas) {
                    m.agregarCorrelativa(Integer.valueOf(idCorr));
                }
            }

            res.redirect("/materias?message=" + URLEncoder.encode("Materia creada con éxito.", StandardCharsets.UTF_8));
            return "";
        });

        // GET: Renderizar formulario para Modificar una materia existente
        get("/materias/edit/:id", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            Materia m = Materia.findById(req.params(":id"));
            if (m == null) {
                res.redirect("/materias?error=Materia no encontrada");
                return null;
            }
            model.put("materia", m.toMap());
            model.put("todasMaterias", Materia.where("id != ?", m.getId()).toMaps());
            return new ModelAndView(model, "materia_edit.mustache");
        }, new MustacheTemplateEngine());

        // POST: Procesar y aplicar la Modificación de datos
        post("/materias/edit/:id", (req, res) -> {
            Materia m = Materia.findById(req.params(":id"));
            if (m != null) {
                m.set("nombre", req.queryParams("nombre"));
                m.set("plan_materia", req.queryParams("plan_materia"));
                m.saveIt();

                m.borrarCorrelatividades();
                String[] correlativasSeleccionadas = req.queryParamsValues("correlativas");
                if (correlativasSeleccionadas != null) {
                    for (String idCorr : correlativasSeleccionadas) {
                        m.agregarCorrelativa(Integer.valueOf(idCorr));
                    }
                }
                res.redirect("/materias?message=" + URLEncoder.encode("Materia modificada correctamente.", StandardCharsets.UTF_8));
            } else {
                res.redirect("/materias?error=Error al modificar.");
            }
            return "";
        });

        // GET: Procesar la Baja de una materia rompiendo dependencias recursivas
        get("/materias/delete/:id", (req, res) -> {
            Materia m = Materia.findById(req.params(":id"));
            if (m != null) {
                m.borrarCorrelatividades();
                m.delete();
                res.redirect("/materias?message=" + URLEncoder.encode("Materia y correlatividades eliminadas.", StandardCharsets.UTF_8));
            } else {
                res.redirect("/materias?error=No se pudo eliminar la materia.");
            }
            return "";
        });


        // --- Rutas POST para manejar envíos de formularios y APIs de Autenticación ---

        // POST: Envío del formulario de creación de nuevo Usuario
        post("/user/new", (req, res) -> {
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400);
                res.redirect("/user/create?error=Nombre y contraseña son requeridos.");
                return "";
            }

            try {
                User ac = new User();
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                ac.set("name", name);
                ac.set("password", hashedPassword);
                ac.saveIt();

                res.status(201);
                res.redirect("/user/create?message=Cuenta creada exitosamente para " + name + "!");
                return "";
            } catch (Exception e) {
                System.err.println("Error al registrar la cuenta: " + e.getMessage());
                res.status(500);
                res.redirect("/user/create?error=Error interno al crear la cuenta. Intente de nuevo.");
                return "";
            }
        });

        // POST: Procesar el inicio de sesión convencional
        post("/login", (req, res) -> {
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

                model.put("username", username);
                return new ModelAndView(model, "dashboard.mustache");
            } else {
                res.status(401);
                model.put("errorMessage", "Usuario o contraseña incorrectos.");
                return new ModelAndView(model, "login.mustache");
            }
        }, new MustacheTemplateEngine());

        // POST: Endpoint de API para añadir usuarios en lote (Retorna JSON)
        post("/add_users", (req, res) -> {
            res.type("application/json");
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400);
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            }

            try {
                User newUser = new User();
                newUser.set("name", name);
                newUser.set("password", password);
                newUser.saveIt();
                res.status(201);
                return objectMapper.writeValueAsString(Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));
            } catch (Exception e) {
                res.status(500);
                return objectMapper.writeValueAsString(Map.of("error", "Error interno: " + e.getMessage()));
            }
        });

        // POST: Procesar Alta de un Profesor vinculándolo a su DNI de Persona
        post("/profesor/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String dni = req.queryParams("prof_dni");
            String legajo = req.queryParams("nro_legajo");

            if (dni == null || dni.isEmpty() || legajo == null || legajo.isEmpty()) { 
                res.status(400);
                model.put("errorMessage", "Dni y legajo son necesarios.");
                return new ModelAndView(model, "professor_login.mustache");
            }
            
            Integer pdni;
            Integer plegajo;
            try {
                pdni = Integer.valueOf(dni);
                plegajo = Integer.valueOf(legajo);
            } catch (NumberFormatException e) {
                 res.status(400);
                 model.put("errorMessage", "El Dni y el Número de Legajo deben ser números válidos.");
                 return new ModelAndView(model, "professor_login.mustache");
            }
            
            try {
                Persona dn = Persona.findFirst("dni = ?", dni);
                if (dn == null) {
                    res.status(401);
                    model.put("errorMessage", "El dni: " + dni + " no está cargado. Por favor registra la persona"); 
                    return new ModelAndView(model, "professor_login.mustache");
                }
                
                Profesor existingProfesor = Profesor.findFirst("dni = ?", dni);
                if (existingProfesor != null) {
                    res.status(200);
                    res.redirect("/profesor/login?message=El profesor (DNI: " + dni + ") ya estaba cargado.");
                    return null; 
                }
                
                Profesor newProfesor = new Profesor();
                newProfesor.setDni(pdni);
                newProfesor.setLegajo(plegajo);
                newProfesor.saveIt();

                res.status(201);
                res.redirect("/profesor/login?message=Profesor cargado exitosamente.");
                return null;
            } catch (Exception e) {
                res.status(500);
                if (e.getMessage().contains("nro_legajo")) {
                    model.put("errorMessage", "El Número de Legajo (" + legajo + ") ya está en uso.");
                } else {
                    model.put("errorMessage", "Error interno al registrar el profesor.");
                }
                return new ModelAndView(model, "professor_login.mustache");
            }
        }, new MustacheTemplateEngine()); 

        // POST: Registrar el formulario de creación de una Persona física
        post("/person/new", (req, res) -> {
            String name = req.queryParams("name");
            String apellido = req.queryParams("apellido");
            String dni = req.queryParams("dni");
            String correo = req.queryParams("correo");

            if (name == null || name.isEmpty() || apellido == null || apellido.isEmpty() 
                || dni == null || dni.isEmpty() || correo == null || correo.isEmpty()) {
                res.status(400); 
                res.redirect("/person/new?error=" + URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8));
                return "";
            }

            Integer personaDni;
            try {
                personaDni = Integer.valueOf(dni);
            } catch (NumberFormatException e) {
                res.status(400);
                res.redirect("/person/new?error=" + URLEncoder.encode("El DNI debe ser un número válido.", StandardCharsets.UTF_8));
                return "";
            }

            try {
                if (Persona.findFirst("dni = ?", dni) != null) {
                    res.status(409);
                    res.redirect("/person/new?error=" + URLEncoder.encode("El DNI " + dni + " ya se encuentra registrado.", StandardCharsets.UTF_8));
                    return "";
                }
                if (Persona.findFirst("correo = ?", correo) != null) {
                    res.status(409);
                    res.redirect("/person/new?error=" + URLEncoder.encode("El correo electrónico ya está registrado.", StandardCharsets.UTF_8));
                    return "";
                }

                Persona newPersona = new Persona();
                newPersona.set("nombre", name);
                newPersona.set("apellido", apellido);
                newPersona.setDni(personaDni);
                newPersona.set("correo", correo); 
                newPersona.saveIt(); 

                res.status(201); 
                res.redirect("/person/new?message=" + URLEncoder.encode("Persona registrada con éxito.", StandardCharsets.UTF_8));
                return ""; 
            } catch (Exception e) {
                res.status(500);
                res.redirect("/person/new?error=" + URLEncoder.encode("Error interno al crear la persona.", StandardCharsets.UTF_8));
                return ""; 
            }
        });


        // -------------------------------------------------------------
        // FILTROS DE INTERCEPCIÓN DE CONEXIONES (SIEMPRE AL FINAL DEL MAIN)
        // -------------------------------------------------------------
        before((req, res) -> {
            try {
                Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                System.out.println(req.url());
            } catch (Exception e) {
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error de conexión a la base de datos.\"}");
            }
        });

        after((req, res) -> {
            try {
                Base.close();
            } catch (Exception e) {
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

    } // Fin del método main
} // Fin de la clase App