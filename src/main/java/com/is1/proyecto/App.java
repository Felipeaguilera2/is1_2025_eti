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
        port(8080); // Configura el puerto en el que la aplicación Spark escuchará las peticiones.

        // Obtener la instancia única del singleton de configuración de la base de datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();

        // Inicialización de la base de datos.
        try {
            Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
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
            Base.close(); // Cerramos la conexión inicial
        }
        
        // --- Filtro 'before' para gestionar la conexión a la base de datos y la sesión ---
        before((req, res) -> {
            String path = req.pathInfo();

            // Evitar error de favicon
            if (path.equals("/favicon.ico")) {
                halt(404);
            }

            try {
                Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                System.out.println("Solicitud: " + req.requestMethod() + " " + req.url());
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

            // Redirigir si se intenta acceder a una ruta privada sin sesión
            if (!esRutaPublica) {
                Boolean loggedIn = req.session().attribute("loggedIn");
                
                if (loggedIn == null || !loggedIn) {
                    res.redirect("/?error=" + URLEncoder.encode("Debes iniciar sesión para acceder a esta pantalla.", StandardCharsets.UTF_8));
                    halt(); 
                }
            }
        });

        // --- Filtro 'after' para cerrar la conexión y limpiar caché ---
        after((req, res) -> {
            res.header("Cache-Control", "no-cache, no-store, must-revalidate");
            res.header("Pragma", "no-cache");
            res.header("Expires", "0");

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
