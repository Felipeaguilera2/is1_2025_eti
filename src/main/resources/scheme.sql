-- Elimina las tablas si ya existen para asegurar un inicio limpioselec
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS profesor; 
DROP TABLE IF EXISTS person;
DROP TABLE IF EXISTS planes_estudio;
DROP TABLE IF EXISTS carrera;
DROP TABLE IF EXISTS materia;
DROP TABLE IF EXISTS correlativas;
DROP TABLE IF EXISTS estudiante;
DROP TABLE IF EXISTS examen_final;
DROP TABLE IF EXISTS cursadas;



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

-- Crea la tabla 'materia'
CREATE TABLE materia (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo_materia INTEGER NOT NULL UNIQUE,
    nombre TEXT NOT NULL,
    plan_materia TEXT NOT NULL,
    es_obligatoria INTEGER NOT NULL DEFAULT 1,
    carrera_id INTEGER,
    FOREIGN KEY (carrera_id) REFERENCES carrera(id) ON DELETE SET NULL
);

-- Crea la tabla intermedia para las relaciones correlativas (Recursiva)
CREATE TABLE correlativas (
    materia_id INTEGER NOT NULL,
    correlativa_id INTEGER NOT NULL,
    PRIMARY KEY (materia_id, correlativa_id),
    FOREIGN KEY (materia_id) REFERENCES materia(id) ON DELETE CASCADE,
    FOREIGN KEY (correlativa_id) REFERENCES materia(id) ON DELETE CASCADE
);

CREATE TABLE carrera (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo VARCHAR(50) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL
);

CREATE TABLE planes_estudio (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    codigo TEXT UNIQUE NOT NULL,
    carrera_id INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (carrera_id) REFERENCES carrera(id) ON DELETE CASCADE
);


-- Crea la tabla 'estudiante' (entidad hija de person)
CREATE TABLE estudiante (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dni INTEGER NOT NULL UNIQUE,
    cod_estudiante INTEGER NOT NULL UNIQUE,
    carrera_id INTEGER,
    plan_estudio_id INTEGER,
    FOREIGN KEY (dni) REFERENCES person(dni) ON DELETE CASCADE,
    FOREIGN KEY (carrera_id) REFERENCES carrera(id) ON DELETE SET NULL,
    FOREIGN KEY (plan_estudio_id) REFERENCES planes_estudio(id) ON DELETE SET NULL
);

-- Crea la tabla de asociación para registrar los exámenes finales
CREATE TABLE examen_final (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    estudiante_id INTEGER NOT NULL,
    materia_id INTEGER NOT NULL,
    profesor_id INTEGER NOT NULL,
    nota REAL NOT NULL,
    fecha TEXT NOT NULL, -- Almacena la fecha en formato YYYY-MM-DD
    FOREIGN KEY (estudiante_id) REFERENCES estudiante(id) ON DELETE CASCADE,
    FOREIGN KEY (materia_id) REFERENCES materia(id) ON DELETE CASCADE,
    FOREIGN KEY (profesor_id) REFERENCES profesor(id) ON DELETE CASCADE
);

-- Tabla de asociación para la Inscripción a Materias (Cursadas)
CREATE TABLE cursadas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    estudiante_id INTEGER NOT NULL,
    materia_id INTEGER NOT NULL,
    periodo TEXT NOT NULL,
    FOREIGN KEY (estudiante_id) REFERENCES estudiante(id) ON DELETE CASCADE,
    FOREIGN KEY (materia_id) REFERENCES materia(id) ON DELETE CASCADE,
    UNIQUE(estudiante_id, materia_id, periodo)
);