**Departamento de Computación**
**Asignatura: Ingeniería de Software II (Cod. 3387)**
**Año 2026**

# Proyecto integrador: Especificación, Gestión y planificación

## 1. (Requirements) Describir su proyecto
- **Problema que se quiere resolver**
	La Oficina de Alumnos de la Universidad necesita modernizar y optimizar la gestión de información académica. El sistema que se utiliza hasta el momento son planillas y sistemas no interconectados. La falta de centralización provoca inconsistencias en los datos y dificulta la comunicación entre docentes y estudiantes. El sistema busca centralizar los datos personales y académicos, así también como datos de las carreras, materias dictadas y docentes que las dictan. Se cuenta además con el desafío de migrar los datos del sistema viejo e incorporar perfiles de usuario.
- **Usuarios del sistema**
	- Personal administrativo: Encargado de la gestión académica general. Uso: Cargar y actualizar datos de docentes, estudiantes, carreras y materias. Asignar docentes a materias; entre otros usos.
	
	- Alumnos: Consultas de información personal y académica, inscribirse/darse de baja de materias, consultar plan de estudio.
	
	- Profesores: Carga de notas, consultar listado de alumnos.
- **Funcionalidades principales
	- **Gestión de usuarios** (Administradores, alumnos y profesores) con autenticación por loggin.
	- **Gestión de docentes**: ABM, gestión de roles, periodo, asignación a materias.
	- **Gestión de materias**: ABM, asignación a plan de estudio, consultas (alumnos que la cursan, docentes que la dictan) registro de examen final.
	- **Gestión de carreras**: (ABM, inscripción de alumnos, plan estudio activo)
	- **Gestión del plan de estudio:** (ABM, materias correspondientes, materias correlativas, carrera correspondiente, modificación del plan).
- **Restricciones técnicas** 
	- Java 11 (Implicito, se requiere para la App)
- **Tamaño del equipo**
	El equipo de desarrollo consta de 4 integrante.
- **Tecnologías elegidas**
	Stack tecnológico que incluye Java, Spark Java para el desarrollo de una API REST, Mustache para las vistas, ActiveJDBC para la interacción con la base de datos y JUnit para las pruebas unitarias.
- **Plazo estimado**
	Se cuenta con el tiempo hasta el final del cuatrimestre, donde culmina la materia. 
- **Cambios de alcance ocurridos**
- **Problemas encontrados**
	Falta de conocimiento y herramientas presentadas para el desarrollo del software. La investigación para poder hacer uso y entendimiento de las mismas demoro el proceso.
- **Forma de organización del equipo**
	Modalidad SCRUM adaptada a los tiempos facultativos, con reuniones tanto presenciales como virtuales según la necesidad. Uso de git para gestión de versiones.

--- 
## 2. (Auditoría) Análisis de riesgos con IA

| Tipo de riesgo | Descripción                                     | Probabilidad | Impacto | Identificado por |
| -------------- | ----------------------------------------------- | ------------ | ------- | ---------------- |
| Técnico        | Falta de conocimiento de las herramientas dadas | Media        | Alto    | Equipo           |
| Técnico        | Defectos de arquitectura                        | Media        | Alto    | LLM              |
| Técnico        | Fallo en equipos tecnológicos del grupo         | Baja         | Medio   | Equipo           |
| Técnico        | Conflictos de concurrencia                      | Media        | Alto    | LLM              |
| Planificación  | Subestimación del tiempo de entrega             | Alta         | Alto    | Equipo           |
| Planificación  | Superposición de otras materias                 | Media        | Medio   | Equipo           |
| Planificación  | Desviación del alcance                          | Baja         | Alta    | LLM              |
| Organizacional | Subestimación de la complejidad del proyecto    | Media        | Alta    | Equipo           |
| Organizacional | Control de versiones                            | Alta         | Medio   | LLM              |
| Humano         | Reducción del equipo de trabajo                 | Baja         | Alto    | LLM              |
| Humano         | Silos de conocimiento                           | Alta         | Alto    | LLM              |
