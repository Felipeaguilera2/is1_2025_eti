package com.is1.proyecto;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.javalite.activejdbc.Base;

import com.is1.proyecto.config.DBConfigSingleton;
import com.is1.proyecto.controllers.AuthController;
import com.is1.proyecto.controllers.CarreraController;
import com.is1.proyecto.controllers.EstudianteController;
import com.is1.proyecto.controllers.MateriaController;
import com.is1.proyecto.controllers.PersonaController;
import com.is1.proyecto.controllers.ProfesorController;

import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.halt;
import static spark.Spark.port;

/**
 * Clase principal de la aplicación Spark.
 * Configura los filtros globales de conexión/sesión e inicia los controladores MVC.
 */
public class App {

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
                // Inicializar y cargar correlativas en memoria
                com.is1.proyecto.CorrelatividadesManager.getInstance();
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
        // --- Filtro 'before' para gestionar la conexión a la base de datos ---
        before((req, res) -> {
            String path = req.pathInfo();

            // EVITAR EL ERROR DEL FAVICON: Si el navegador pide el icono, cortamos acá
            // y no abrimos una conexión duplicada en el mismo hilo.
            if (path.equals("/favicon.ico")) {
                halt(404); // Le devolvemos un No Encontrado limpio al navegador
            }

            try {
                // Abre una conexión a la base de datos utilizando las credenciales del singleton.
                Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                System.out.println(req.url());

            } catch (Exception e) {
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error interno del servidor: Fallo al conectar a la base de datos.\"}" + e.getMessage());
            }
            
            // Rutas públicas
            boolean esRutaPublica = path.equals("/") || 
                                    path.equals("/login") || 
                                    path.equals("/logout") || 
                                    path.startsWith("/user/new") || 
                                    path.startsWith("/user/create");

            // Solicitud de login para rutas privadas
            if (!esRutaPublica) {
                Boolean loggedIn = req.session().attribute("loggedIn");
                
                if (loggedIn == null || !loggedIn) {
                    res.redirect("/?error=" + URLEncoder.encode("Debes iniciar sesión para acceder a esta pantalla.", StandardCharsets.UTF_8));
                    halt(); 
                }
            }
        });

        // --- Filtro 'after' para cerrar la conexión a la base de datos ---
        // Este filtro se ejecuta después de que cada solicitud HTTP ha sido procesada.
       // --- Filtro 'after' para cerrar la conexión y limpiar caché ---
        after((req, res) -> {
        //Desactivar el almacenamiento en caché del navegador 
            res.header("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
            res.header("Pragma", "no-cache"); // HTTP 1.0
            res.header("Expires", "0"); // Proxies

            //Cierre de conexión de la base de datos
            try {
                Base.close();
            } catch (Exception e) {
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

        // --- Inicialización de los Controladores (Registran sus rutas en Spark) ---
        new AuthController();
        new PersonaController();
        new CarreraController();
        new MateriaController();
        new EstudianteController();
        new ProfesorController();
        new com.is1.proyecto.controllers.InscripcionController();
    }
}