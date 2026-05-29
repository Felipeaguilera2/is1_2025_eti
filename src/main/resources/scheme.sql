-- Elimina las tablas si ya existen para asegurar un inicio limpioselec
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS profesor; 
DROP TABLE IF EXISTS person;
DROP TABLE IF EXISTS carrera;


-- Crea la tabla 'person' (entidad padre)
CREATE TABLE person 
(
    dni INTEGER NOT NULL UNIQUE PRIMARY KEY,
    nombre TEXT NOT NULL,
    apellido TEXT NOT NULL,
    correo TEXT NOT NULL
);

-- Crea la tabla 'profesor' (entidad hija)
CREATE TABLE profesor
(
    dni INTEGER NOT NULL,
    nro_legajo INTEGER NOT NULL UNIQUE,
    PRIMARY KEY (dni),
    FOREIGN KEY (dni) references person(dni)
);

-- Crea la tabla 'users' para la autenticación
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Clave primaria autoincremental para SQLite
    name TEXT NOT NULL UNIQUE,          -- Nombre de usuario (TEXT es el tipo de cadena recomendado para SQLite), con restricción UNIQUE
    password TEXT NOT NULL           -- Contraseña hasheada (TEXT es el tipo de cadena recomendado para SQLite)
);

CREATE TABLE carrera (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo VARCHAR(50) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL
);

