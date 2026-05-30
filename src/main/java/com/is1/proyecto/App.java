package com.is1.proyecto; // Define el paquete de la aplicación, debe coincidir con la estructura de carpetas.

import java.io.InputStream;
import java.util.Scanner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
// Importaciones necesarias para la aplicación Spark
import com.fasterxml.jackson.databind.ObjectMapper; // Utilidad para serializar/deserializar objetos Java a/desde JSON.
import static spark.Spark.*; // Importa los métodos estáticos principales de Spark (get, post, before, after, etc.).

// Importaciones específicas para ActiveJDBC (ORM para la base de datos)
import org.javalite.activejdbc.Base; // Clase central de ActiveJDBC para gestionar la conexión a la base de datos.
import org.mindrot.jbcrypt.BCrypt; // Utilidad para hashear y verificar contraseñas de forma segura.

// Importaciones de Spark para renderizado de plantillas
import spark.ModelAndView; // Representa un modelo de datos y el nombre de la vista a renderizar.
import spark.template.mustache.MustacheTemplateEngine; // Motor de plantillas Mustache para Spark.

// Importaciones estándar de Java
import java.util.HashMap; // Para crear mapas de datos (modelos para las plantillas).
import java.util.Map; // Interfaz Map, utilizada para Map.of() o HashMap.

// Importaciones de clases del proyecto
import com.is1.proyecto.config.DBConfigSingleton; // Clase Singleton para la configuración de la base de datos.
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.Profesor; 
import com.is1.proyecto.models.User; // Modelo de ActiveJDBC que representa la tabla 'users'.
import com.is1.proyecto.models.Carrera; // Modelo de ActiveJDBC que representa la tabla 'carrera'.
import com.is1.proyecto.models.Materia; //Modelo de ActiveJDBC que representa la tabla 'materia'

/**
 * Clase principal de la aplicación Spark.
 * Configura las rutas, filtros y el inicio del servidor web.
 */
public class App {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Método principal que se ejecuta al iniciar la aplicación.
     * Aquí se configuran todas las rutas y filtros de Spark.
     */
    public static void main(String[] args) {
        port(8080); // Configura el puerto en el que la aplicación Spark escuchará las peticiones (por defecto es 4567).

        // Obtener la instancia única del singleton de configuración de la base de datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();

        
        try {
            Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
            // Leemos el archivo scheme.sql de los recursos
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
            Base.close(); // Cerramos para que Spark maneje sus propias conexiones después
        }
        
        // --- Filtro 'before' para gestionar la conexión a la base de datos ---
        // Este filtro se ejecuta antes de cada solicitud HTTP.
        before((req, res) -> {
            try {
                // Abre una conexión a la base de datos utilizando las credenciales del singleton.
                Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                System.out.println(req.url());

            } catch (Exception e) {
                // Si ocurre un error al abrir la conexión, se registra y se detiene la solicitud
                // con un código de estado 500 (Internal Server Error) y un mensaje JSON.
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error interno del servidor: Fallo al conectar a la base de datos.\"}" + e.getMessage());
            }
        });

        // --- Filtro 'after' para cerrar la conexión a la base de datos ---
        // Este filtro se ejecuta después de que cada solicitud HTTP ha sido procesada.
        after((req, res) -> {
            try {
                // Cierra la conexión a la base de datos para liberar recursos.
                Base.close();
            } catch (Exception e) {
                // Si ocurre un error al cerrar la conexión, se registra.
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

        // --- Rutas GET para renderizar formularios y páginas HTML ---

        // GET: Muestra el formulario de creación de cuenta.
        // Soporta la visualización de mensajes de éxito o error pasados como query parameters.
        get("/user/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.

            // Obtener y añadir mensaje de éxito de los query parameters (ej. ?message=Cuenta creada!)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos vacíos)
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'user_form.mustache' con los datos del modelo.
            return new ModelAndView(model, "user_form.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/dashboard", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla del dashboard.

            // Intenta obtener el nombre de usuario y la bandera de login de la sesión.
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            // 1. Verificar si el usuario ha iniciado sesión.
            // Si no hay un nombre de usuario en la sesión, la bandera es nula o falsa,
            // significa que el usuario no está logueado o su sesión expiró.
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
                // Redirige al login con un mensaje de error.
                res.redirect("/login?error=Debes iniciar sesión para acceder a esta página.");
                return null; // Importante retornar null después de una redirección.
            }

            // 2. Si el usuario está logueado, añade el nombre de usuario al modelo para la plantilla.
            model.put("username", currentUsername);

            // 3. Renderiza la plantilla del dashboard con el nombre de usuario.
            return new ModelAndView(model, "dashboard.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para cerrar la sesión del usuario.
        get("/logout", (req, res) -> {
            // Invalida completamente la sesión del usuario.
            // Esto elimina todos los atributos guardados en la sesión y la marca como inválida.
            // La cookie JSESSIONID en el navegador también será gestionada para invalidarse.
            req.session().invalidate();

            System.out.println("DEBUG: Sesión cerrada. Redirigiendo a /login.");

            // Redirige al usuario a la página de login con un mensaje de éxito.
            res.redirect("/");

            return null; // Importante retornar null después de una redirección.
        });

        // GET: Muestra el formulario de inicio de sesión (login).
        // Nota: Esta ruta debería ser capaz de leer también mensajes de error/éxito de los query params
        // si se la usa como destino de redirecciones. (Tu código de /user/create ya lo hace, aplicar similar).
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
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta de alias para el formulario de creación de cuenta.
        // En una aplicación real, probablemente querrías unificar con '/user/create' para evitar duplicidad.
        get("/user/new", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "user_form.mustache"); // No pasa un modelo específico, solo el formulario.
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.


        // --- Rutas POST para manejar envíos de formularios y APIs ---

        // POST: Maneja el envío del formulario de creación de nueva cuenta.
        post("/user/new", (req, res) -> {
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // Validaciones básicas: campos no pueden ser nulos o vacíos.
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Código de estado HTTP 400 (Bad Request).
                // Redirige al formulario de creación con un mensaje de error.
                res.redirect("/user/create?error=Nombre y contraseña son requeridos.");
                return ""; // Retorna una cadena vacía ya que la respuesta ya fue redirigida.
            }

            try {
                // Intenta crear y guardar la nueva cuenta en la base de datos.
                User ac = new User(); // Crea una nueva instancia del modelo User.
                // Hashea la contraseña de forma segura antes de guardarla.
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                ac.set("name", name); // Asigna el nombre de usuario.
                ac.set("password", hashedPassword); // Asigna la contraseña hasheada.
                ac.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Código de estado HTTP 201 (Created) para una creación exitosa.
                // Redirige al formulario de creación con un mensaje de éxito.
                res.redirect("/user/create?message=Cuenta creada exitosamente para " + name + "!");
                return ""; // Retorna una cadena vacía.

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB (ej. nombre de usuario duplicado),
                // se captura aquí y se redirige con un mensaje de error.
                System.err.println("Error al registrar la cuenta: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Código de estado HTTP 500 (Internal Server Error).
                res.redirect("/user/create?error=Error interno al crear la cuenta. Intente de nuevo.");
                return ""; // Retorna una cadena vacía.
            }
        });


        // POST: Maneja el envío del formulario de inicio de sesión.
        post("/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla de login o dashboard.

            String username = req.queryParams("username");
            String plainTextPassword = req.queryParams("password");

            // Validaciones básicas: campos de usuario y contraseña no pueden ser nulos o vacíos.
            if (username == null || username.isEmpty() || plainTextPassword == null || plainTextPassword.isEmpty()) {
                res.status(400); // Bad Request.
                model.put("errorMessage", "El nombre de usuario y la contraseña son requeridos.");
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Busca la cuenta en la base de datos por el nombre de usuario.
            User ac = User.findFirst("name = ?", username);

            // Si no se encuentra ninguna cuenta con ese nombre de usuario.
            if (ac == null) {
                res.status(401); // Unauthorized.
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Obtiene la contraseña hasheada almacenada en la base de datos.
            String storedHashedPassword = ac.getString("password");

            // Compara la contraseña en texto plano ingresada con la contraseña hasheada almacenada.
            // BCrypt.checkpw hashea la plainTextPassword con el salt de storedHashedPassword y compara.
            if (BCrypt.checkpw(plainTextPassword, storedHashedPassword)) {
                // Autenticación exitosa.
                res.status(200); // OK.

                // --- Gestión de Sesión ---
                req.session(true).attribute("currentUserUsername", username); // Guarda el nombre de usuario en la sesión.
                req.session().attribute("userId", ac.getId()); // Guarda el ID de la cuenta en la sesión (útil).
                req.session().attribute("loggedIn", true); // Establece una bandera para indicar que el usuario está logueado.

                System.out.println("DEBUG: Login exitoso para la cuenta: " + username);
                System.out.println("DEBUG: ID de Sesión: " + req.session().id());


                model.put("username", username); // Añade el nombre de usuario al modelo para el dashboard.
                // Renderiza la plantilla del dashboard tras un login exitoso.
                return new ModelAndView(model, "dashboard.mustache");
            } else {
                // Contraseña incorrecta.
                res.status(401); // Unauthorized.
                System.out.println("DEBUG: Intento de login fallido para: " + username);
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta POST.


        // POST: Endpoint para añadir usuarios (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_users", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // --- Validaciones básicas ---
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                User newUser = new User(); // Crea una nueva instancia de tu modelo User.
                // ¡ADVERTENCIA DE SEGURIDAD CRÍTICA!
                // En una aplicación real, las contraseñas DEBEN ser hasheadas (ej. con BCrypt)
                // ANTES de guardarse en la base de datos, NUNCA en texto plano.
                // (Nota: El código original tenía la contraseña en texto plano aquí.
                // Se recomienda usar `BCrypt.hashpw(password, BCrypt.gensalt())` como en la ruta '/user/new').
                newUser.set("name", name); // Asigna el nombre al campo 'name'.
                newUser.set("password", password); // Asigna la contraseña al campo 'password'.
                newUser.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar usuario: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar usuario: " + e.getMessage()));
            }
        });

       // GET: Muestra el formulario de profesor y captura mensajes de éxito/error
        get("/profesor/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            // Capturar y añadir mensaje de éxito de los query parameters
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Capturar y añadir mensaje de error de los query parameters
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            return new ModelAndView(model, "profesor/professor_login.mustache"); 
        }, new MustacheTemplateEngine());

        // POST: Maneja el envío del formulario de inicio de sesión / alta de profesor
        post("/profesor/login", (req, res) -> {
            String dni = req.queryParams("prof_dni");
            String legajo = req.queryParams("nro_legajo");

            // Validaciones básicas: campos no nulos ni vacíos
            if (dni == null || dni.isEmpty() || legajo == null || legajo.isEmpty()) { 
                res.status(400); // Bad Request
                String msg = URLEncoder.encode("El DNI y el Número de Legajo son requeridos.", StandardCharsets.UTF_8);
                res.redirect("/profesor/login?error=" + msg);
                return null;
            }
            
            Integer pdni;
            Integer plegajo;
            try {
                pdni = Integer.valueOf(dni.trim());
                plegajo = Integer.valueOf(legajo.trim());
            } catch (NumberFormatException e) {
                 res.status(400); // Bad Request
                 String msg = URLEncoder.encode("El DNI y el Número de Legajo deben ser números válidos.", StandardCharsets.UTF_8);
                 res.redirect("/profesor/login?error=" + msg);
                 return null;
            }
        
            try {
                // 1. Criterio de Aceptación: Buscar primero por número de legajo numérico
                Profesor existingProfesor = Profesor.findFirst("nro_legajo = ?", plegajo);

                if (existingProfesor != null) {
                    res.status(409); // Conflict
                    String msg = URLEncoder.encode("El Número de Legajo (" + legajo + ") ya está en uso por otro profesor.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/login?error=" + msg);
                    return null;
                }
                
                // CORRECCIÓN CRÍTICA: Buscamos a la persona usando 'pdni' (Integer), NO el String 'dni'
                Persona dn = Persona.findFirst("dni = ?", pdni);

                if (dn == null) {
                    res.status(401); // Unauthorized
                    String msg = URLEncoder.encode("El DNI " + dni + " no está cargado en el sistema. Por favor, registra la persona primero.", StandardCharsets.UTF_8); 
                    res.redirect("/profesor/login?error=" + msg);
                    return null;
                }
                
                // 3. Registramos el nuevo profesor vinculando el DNI correspondiente
                Profesor newProfesor = new Profesor();
                newProfesor.set("dni", pdni);
                newProfesor.set("nro_legajo", plegajo);
                newProfesor.saveIt(); // Guarda de forma efectiva en la tabla 'profesor'

                res.status(201); // Created

                // Redirigir con mensaje codificado para el cartel verde
                String mensajeExito = "Profesor con Legajo " + legajo + " cargado exitosamente.";
                String msgEncoded = URLEncoder.encode(mensajeExito, StandardCharsets.UTF_8);
                res.redirect("/profesor/login?message=" + msgEncoded);
                return null;

            } catch (Exception e) {
                System.err.println("Error al registrar profesor: " + e.getMessage());
                e.printStackTrace(); 
                res.status(500); // Internal Server Error
                String msg = URLEncoder.encode("Error interno al registrar el profesor: " + e.getMessage(), StandardCharsets.UTF_8);
                res.redirect("/profesor/login?error=" + msg);
                return null;
            }
        }, new MustacheTemplateEngine());

        get("/profesor/gestion", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            // Capturar y añadir mensaje de éxito de los query parameters
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Capturar y añadir mensaje de error de los query parameters
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            return new ModelAndView(model, "profesor/gestion_profesor.mustache"); 
        }, new MustacheTemplateEngine());

         // GET: Muestra el formulario de persona y captura mensajes de éxito/error
        // Soporta la visualización de mensajes de éxito o error pasados como query parameters.
        get("/person/new", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); 

            // Obtener y añadir mensaje de éxito de los query parameters (ej. ?message=Cuenta creada!)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos vacíos)
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'professor_login.mustache' con los datos del modelo.
            return new ModelAndView(model, "person_form.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // POST: Maneja el envío del formulario de registro de nueva Persona.
        post("/person/new", (req, res) -> {
            String name = req.queryParams("name");
            String apellido = req.queryParams("apellido");
            String dni = req.queryParams("dni");
            String correo = req.queryParams("correo");

            
            if (name == null || name.isEmpty() || apellido == null || apellido.isEmpty() 
                || dni == null || dni.isEmpty() || correo == null || correo.isEmpty()) {
                res.status(400); 
                
                String msg = URLEncoder.encode("Todos los campos son obligatorios.", StandardCharsets.UTF_8);
                res.redirect("/person/new?error=" + msg);
                return "";
            }

            Integer personaDni;
            try {
                personaDni = Integer.valueOf(dni);
            } catch (NumberFormatException e) {
                res.status(400);
                
                String msg = URLEncoder.encode("El DNI debe ser un número válido.", StandardCharsets.UTF_8);
                res.redirect("/person/new?error=" + msg);
                return "";
            }

            try {
                
                if (Persona.findFirst("dni = ?", dni) != null) {
                    res.status(409);
                    String msg = URLEncoder.encode("El DNI " + dni + " ya se encuentra registrado.", StandardCharsets.UTF_8);
                    res.redirect("/person/new?error=" + msg);
                    return "";
                }
                
                
                if (Persona.findFirst("correo = ?", correo) != null) {
                    res.status(409);
                    String msg = URLEncoder.encode("El correo electrónico ya se encuentra registrado.", StandardCharsets.UTF_8);
                    res.redirect("/person/new?error=" + msg);
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
                
                res.redirect("/person/new?message=" + msgEncoded);
                return ""; 

            } catch (Exception e) {
                System.err.println("Error al registrar la persona: " + e.getMessage());
                e.printStackTrace(); 
                res.status(500);
                String msg = URLEncoder.encode("Error interno al crear la persona. Intente de nuevo.", StandardCharsets.UTF_8);
                res.redirect("/person/new?error=" + msg);
                return ""; 
            }
        });

        get("/carrera/new", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "carrera_form.mustache");
        }, new MustacheTemplateEngine());

        // Procesar los datos del formulario
        post("/carrera/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String codigo = req.queryParams("codigo");
            String nombre = req.queryParams("nombre");

            try {
                // Validación básica
                if (codigo == null || codigo.trim().isEmpty() || nombre == null || nombre.trim().isEmpty()) {
                    model.put("error", "Todos los campos son obligatorios.");
                    return new ModelAndView(model, "carrera_form.mustache");
                }

                // Guardar en la base de datos
                Carrera carrera = new Carrera();
                carrera.set("codigo", codigo);
                carrera.set("nombre", nombre);
                carrera.saveIt();

                model.put("success", true);
            } catch (Exception e) {
                // ActiveJDBC lanzará excepción si se viola la restricción UNIQUE del código
                model.put("error", "Error al guardar. Es posible que el código ya exista.");
            }

            return new ModelAndView(model, "carrera_form.mustache");
        }, new MustacheTemplateEngine());

        // Mostrar listado completo de carreras
        get("/carreras", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            
            // Trae todas las filas de la tabla 'carreras' y las convierte a mapas para la vista
            model.put("carreras", Carrera.findAll().toMaps());
            
            return new ModelAndView(model, "carreras_list.mustache");
        }, new MustacheTemplateEngine());
        // 1. GET: Inicializa la pantalla de baja
        get("/profesor/baja", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            return new ModelAndView(model, "profesor/baja_profesor.mustache");
        }, new MustacheTemplateEngine());

        // 2. POST: Verifica la existencia del legajo
        post("/profesor/baja/verificar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String legajoStr = req.queryParams("nro_legajo");

            if (legajoStr == null || legajoStr.isEmpty()) {
                res.redirect("/profesor/baja?error=" + URLEncoder.encode("Debe ingresar un número de legajo válido.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                Integer legajo = Integer.valueOf(legajoStr.trim());
                Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

                if (prof == null) {
                    String msg = URLEncoder.encode("El profesor con Legajo " + legajoStr + " no se encuentra en la base de datos.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/baja?error=" + msg);
                    return null;
                }

                // Inyectamos como Strings explícitos para que Mustache no falle al mapear objetos
                model.put("profesorEncontrado", true);
                model.put("legajoEliminar", String.valueOf(prof.get("nro_legajo")));
                model.put("dniAsociado", String.valueOf(prof.get("dni")));

                return new ModelAndView(model, "profesor/baja_profesor.mustache");

            } catch (NumberFormatException e) {
                res.redirect("/profesor/baja?error=" + URLEncoder.encode("El legajo debe ser un valor numérico.", StandardCharsets.UTF_8));
                return null;
            } catch (Exception e) {
                System.err.println("Error en verificar: " + e.getMessage());
                res.redirect("/profesor/baja?error=" + URLEncoder.encode("Error interno al verificar el legajo.", StandardCharsets.UTF_8));
                return null;
            }
        }, new MustacheTemplateEngine());

        // 3. POST: Confirma y elimina físicamente la fila en la tabla profesor
        post("/profesor/baja/confirmar", (req, res) -> {
            String legajoStr = req.queryParams("nro_legajo");

            if (legajoStr == null || legajoStr.isEmpty()) {
                res.redirect("/profesor/baja?error=" + URLEncoder.encode("Falta el número de legajo para procesar la baja.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                Integer legajo = Integer.valueOf(legajoStr.trim());
                
                // Verificamos primero si el profesor existe antes de borrar
                Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

                if (prof != null) {
                    // Base.exec ejecuta SQL puro de forma ultra rápida y segura en el hilo actual de la DB
                    Base.exec("DELETE FROM profesor WHERE nro_legajo = ?", legajo);
                    
                    String msg = URLEncoder.encode("La operación ocurrió exitosamente. Profesor eliminado.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/baja?message=" + msg);
                } else {
                    String msg = URLEncoder.encode("El profesor ya no existe o fue eliminado por otro proceso.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/baja?error=" + msg);
                }
                return null;

            } catch (Exception e) {
                System.err.println("CRÍTICO: Error en confirmación: " + e.getMessage());
                e.printStackTrace();
                String msg = URLEncoder.encode("Error al confirmar la eliminación: " + e.getMessage(), StandardCharsets.UTF_8);
                res.redirect("/profesor/baja?error=" + msg);
                return null;
            }
        }, new MustacheTemplateEngine());

        // 1. GET: Inicializa la pantalla de consulta/edición vacía
        get("/profesor/consulta", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            return new ModelAndView(model, "profesor/profesor_consulta.mustache");
        }, new MustacheTemplateEngine());

        // 2. POST: Procesa la búsqueda por legajo y acopla los datos de la Persona
        post("/profesor/consulta", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String legajoStr = req.queryParams("nro_legajo");

            if (legajoStr == null || legajoStr.isEmpty()) {
                res.redirect("/profesor/consulta?error=" + URLEncoder.encode("Debe ingresar un número de legajo.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                Integer legajo = Integer.valueOf(legajoStr.trim());
                model.put("legajoBuscado", legajo);

                Profesor prof = Profesor.findFirst("nro_legajo = ?", legajo);

                if (prof == null) {
                    String msg = URLEncoder.encode("El profesor con el legajo ingresado no existe en el sistema.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/consulta?error=" + msg);
                    return null;
                }

                Integer personaDni = prof.getInteger("dni");
                Persona persona = Persona.findFirst("dni = ?", personaDni);

                if (persona != null) {
                    model.put("profesorEncontrado", true);
                    model.put("legajo", String.valueOf(prof.get("nro_legajo")));
                    model.put("dni", String.valueOf(persona.get("dni")));
                    model.put("nombre", persona.get("nombre"));
                    model.put("apellido", persona.get("apellido"));
                    model.put("correo", persona.get("correo"));
                } else {
                    model.put("errorMessage", "Error: No se encontraron datos personales válidos para este profesor.");
                }

                return new ModelAndView(model, "profesor/profesor_consulta.mustache");

            } catch (NumberFormatException e) {
                res.redirect("/profesor/consulta?error=" + URLEncoder.encode("El legajo debe ser un valor numérico.", StandardCharsets.UTF_8));
                return null;
            } catch (Exception e) {
                res.redirect("/profesor/consulta?error=" + URLEncoder.encode("Error interno al procesar la consulta.", StandardCharsets.UTF_8));
                return null;
            }
        }, new MustacheTemplateEngine());

        // 3. POST: Procesa la modificación real y guarda los cambios mediante SQL directo
        post("/profesor/editar", (req, res) -> {
            String legajoStr = req.queryParams("nro_legajo");
            String dniStr = req.queryParams("dni");
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String correo = req.queryParams("correo");

            if (dniStr == null || dniStr.trim().isEmpty()) {
                String msg = URLEncoder.encode("Error: El formulario no envió el DNI del profesor.", StandardCharsets.UTF_8);
                res.redirect("/profesor/consulta?error=" + msg);
                return null;
            }

            try {
                Integer pdni = Integer.valueOf(dniStr.trim());
                
                // Verificamos primero si la persona existe en la base de datos
                Persona persona = Persona.findFirst("dni = ?", pdni);

                if (persona != null) {
                    // Preparamos los valores limpiando espacios, o manteniendo los actuales si vinieran vacíos
                    String nuevoNombre = (nombre != null) ? nombre.trim() : persona.getString("nombre");
                    String nuevoApellido = (apellido != null) ? apellido.trim() : persona.getString("apellido");
                    String nuevoCorreo = (correo != null) ? correo.trim() : persona.getString("correo");

                    Base.exec("UPDATE person SET nombre = ?, apellido = ?, correo = ? WHERE dni = ?", 
                              nuevoNombre, nuevoApellido, nuevoCorreo, pdni);

                    String msg = URLEncoder.encode("Datos del profesor con Legajo " + legajoStr + " actualizados con éxito.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/consulta?message=" + msg);
                } else {
                    String msg = URLEncoder.encode("Error: El DNI " + dniStr + " no corresponde a ninguna persona registrada.", StandardCharsets.UTF_8);
                    res.redirect("/profesor/consulta?error=" + msg);
                }
                return null;

            } catch (Exception e) {
                System.err.println("CRÍTICO: Error al actualizar la persona en la DB:");
                e.printStackTrace(); 
                
                String msg = URLEncoder.encode("Error interno al guardar los cambios: " + e.getMessage(), StandardCharsets.UTF_8);
                res.redirect("/profesor/consulta?error=" + msg);
                return null;
            }
        }, new MustacheTemplateEngine());

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
            String obligatoriaParam = req.queryParams("es_obligatoria");
            int esObligatoria = (obligatoriaParam != null && obligatoriaParam.equals("on")) ? 1 : 0;

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

            // Lógica para guardar correlativas (Requiere que el modelo Materia tenga este método)
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
                String obligatoriaParam = req.queryParams("es_obligatoria");
                int esObligatoria = (obligatoriaParam != null && obligatoriaParam.equals("on")) ? 1 : 0;
                m.set("nombre", req.queryParams("nombre"));
                m.set("plan_materia", req.queryParams("plan_materia"));
                m.set("es_obligatoria", esObligatoria);
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

        // GET: Procesar la Baja de una materia
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
    } // Fin del método main
} // Fin de la clase App