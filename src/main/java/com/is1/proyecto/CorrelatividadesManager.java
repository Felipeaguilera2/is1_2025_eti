package com.is1.proyecto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Materia;

public class CorrelatividadesManager {
    private static CorrelatividadesManager instance;

    // Mapeo del ID de base de datos a índice de la matriz
    private Map<Integer, Integer> idToIndex;
    // Mapeo del índice de la matriz al ID de base de datos
    private Map<Integer, Integer> indexToId;

    // Matriz de adyacencia de N x N: matrix[i][j] es true si materia i requiere a j
    private boolean[][] adjacencyMatrix;
    // Matriz de clausura transitiva de N x N: transitive[i][j] es true si hay un camino (dependencia directa/indirecta) de i a j
    private boolean[][] transitiveClosure;

    private int N;

    private CorrelatividadesManager() {
        idToIndex = new HashMap<>();
        indexToId = new HashMap<>();
        N = 0;
        reload();
    }

    public static synchronized CorrelatividadesManager getInstance() {
        if (instance == null) {
            instance = new CorrelatividadesManager();
        }
        return instance;
    }

    /**
     * Carga o recarga la matriz de correlatividades desde la base de datos.
     */
    public synchronized void reload() {
        try {
            // 1. Obtener todas las materias existentes en orden consistente
            List<Materia> todas = Materia.findAll();
            this.N = todas.size();
            
            this.idToIndex = new HashMap<>();
            this.indexToId = new HashMap<>();
            
            for (int i = 0; i < N; i++) {
                int id = ((Number) todas.get(i).getId()).intValue();
                idToIndex.put(id, i);
                indexToId.put(i, id);
            }

            this.adjacencyMatrix = new boolean[N][N];
            this.transitiveClosure = new boolean[N][N];

            // 2. Cargar correlaciones directas de la base de datos
            List<Map> rows = Base.findAll("SELECT materia_id, correlativa_id FROM correlativas");
            for (Map row : rows) {
                Integer materiaId = ((Number) row.get("materia_id")).intValue();
                Integer correlativaId = ((Number) row.get("correlativa_id")).intValue();
                
                Integer idxMateria = idToIndex.get(materiaId);
                Integer idxCorrelativa = idToIndex.get(correlativaId);
                
                if (idxMateria != null && idxCorrelativa != null) {
                    adjacencyMatrix[idxMateria][idxCorrelativa] = true;
                    transitiveClosure[idxMateria][idxCorrelativa] = true;
                }
            }

            // 3. Calcular la clausura transitiva usando el algoritmo de Warshall (Floyd-Warshall)
            for (int k = 0; k < N; k++) {
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        transitiveClosure[i][j] = transitiveClosure[i][j] || 
                            (transitiveClosure[i][k] && transitiveClosure[k][j]);
                    }
                }
            }

            System.out.println(">>> CorrelatividadesManager: Matriz de " + N + "x" + N + " cargada con clausura transitiva.");
        } catch (Exception e) {
            System.err.println(">>> CorrelatividadesManager: Error al cargar correlatividades: " + e.getMessage());
        }
    }

    /**
     * Comprueba en tiempo O(1) si agregar la correlación (materiaId -> requiere prospectiveCorrelativaId) crearía un ciclo.
     */
    public boolean checkCycle(int materiaId, int prospectiveCorrelativaId) {
        if (materiaId == prospectiveCorrelativaId) {
            return true;
        }

        Integer idxMateria = idToIndex.get(materiaId);
        Integer idxProspective = idToIndex.get(prospectiveCorrelativaId);

        // Si alguna materia no está registrada en el mapeo (ej. es nueva y no se ha hecho reload)
        if (idxMateria == null || idxProspective == null) {
            return false;
        }

        // Si prospective ya depende de materiaId (es decir, hay camino desde prospective hasta materiaId),
        // agregar materiaId -> prospective crearía un ciclo.
        // Consulta O(1) usando la matriz de clausura transitiva.
        return transitiveClosure[idxProspective][idxMateria];
    }

    /**
     * Verifica si un estudiante tiene todas las correlativas de la materia aprobadas (final con nota >= 4).
     */
    public boolean puedeCursar(int estudianteId, int materiaId) {
        Integer idxMateria = idToIndex.get(materiaId);
        if (idxMateria == null) {
            return true;
        }

        // Obtener todas las materias correlativas directas requeridas
        List<Integer> requeridasIds = new ArrayList<>();
        for (int j = 0; j < N; j++) {
            if (adjacencyMatrix[idxMateria][j]) {
                requeridasIds.add(indexToId.get(j));
            }
        }

        if (requeridasIds.isEmpty()) {
            return true;
        }

        List<Map> aprobadasRows = Base.findAll(
            "SELECT materia_id FROM examen_final WHERE estudiante_id = ? AND nota >= 4.0", 
            estudianteId
        );
        
        Set<Integer> aprobadas = new HashSet<>();
        for (Map row : aprobadasRows) {
            aprobadas.add(((Number) row.get("materia_id")).intValue());
        }

        for (int req : requeridasIds) {
            if (!aprobadas.contains(req)) {
                return false;
            }
        }
        return true;
    }
}
