graph TD
    Client[Navegador Web / Cliente] -->|Peticiones HTTP| App[App.java<br/>Filtros y Enrutamiento Spark]

    subgraph Capa de Presentación y Lógica Web
        App -->|Rutas seguras| Controllers[Controladores<br/>AuthController, ProfesorController, etc.]
        Controllers -->|Datos inyectados| Templates[Vistas Mustache<br/>/resources/templates/]
        Templates -.->|HTML Renderizado| Client
    end

    subgraph Capa de Dominio y Datos
        Controllers -->|Llamadas a métodos ORM| Models[Modelos ActiveJDBC<br/>User, Persona, Profesor, etc.]
        Models <-->|Mapeo Objeto-Relacional| ActiveJDBC[ActiveJDBC Core]
    end

    subgraph Persistencia
        ActiveJDBC <-->|JDBC| DB[(Base de Datos Relacional)]
    end

    %% Estilos opcionales para mayor claridad
    classDef client fill:#e1f5fe,stroke:#0288d1,stroke-width:2px,color:#000
    classDef web fill:#f3e5f5,stroke:#8e24aa,stroke-width:2px,color:#000
    classDef domain fill:#e8f5e9,stroke:#388e3c,stroke-width:2px,color:#000
    classDef storage fill:#fff3e0,stroke:#f57c00,stroke-width:2px,color:#000

    class Client client
    class App,Controllers,Templates web
    class Models,ActiveJDBC domain
    class DB storage
