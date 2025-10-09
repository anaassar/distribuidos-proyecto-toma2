package com.ana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ana.coordinator.Coordinator;
import com.ana.db.DatabaseClientImpl;
import java.util.Base64;
import spark.Spark;

public class ApplicationServer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Coordinator coordinator;
    
    public static void main(String[] args) {
        try {
            // Inicializar base de datos
            DatabaseClientImpl dbClient = new DatabaseClientImpl();
            
            // Inicializar coordinador
            coordinator = new Coordinator(
                dbClient,
                "localhost",  // registryHost
                1099,         // registryPort
                3             // replicaCount
            );
            
            // Configurar Spark
            Spark.port(8081);
            Spark.staticFiles.location("/public");
            
            // Configurar CORS para desarrollo
            Spark.before((req, res) -> {
                res.header("Access-Control-Allow-Origin", "*");
                res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            });
            
            // Manejar preflight requests
            Spark.options("/*", (req, res) -> {
                return "OK";
            });
            
            // ========== AUTENTICACIÓN ==========
            
            Spark.post("/api/register", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    String username = body.get("username").asText();
                    String email = body.get("email").asText();
                    String password = body.get("password").asText();
                    
                    String result = coordinator.handleRegisterUser(username, email, password);
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("message", result);
                    return response;
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            Spark.post("/api/login", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    String email = body.get("email").asText();
                    String password = body.get("password").asText();
                    
                    String token = coordinator.handleLoginUser(email, password);
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("token", token);
                    return response;
                } catch (Exception e) {
                    res.status(401);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== GESTIÓN DE DIRECTORIOS ==========
            
            Spark.post("/api/createDirectories", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode pathsNode = (ArrayNode) body.get("paths");
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir paths a array de strings
                    String[] paths = new String[pathsNode.size()];
                    for (int i = 0; i < pathsNode.size(); i++) {
                        paths[i] = pathsNode.get(i).asText();
                    }
                    
                    coordinator.handleCreateDirectories(paths, user.getId());
                    return objectMapper.createObjectNode().put("message", "Directorios creados exitosamente");
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== SUBIDA DE ARCHIVOS ==========
            
            Spark.post("/api/uploadFiles", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode pathsNode = (ArrayNode) body.get("paths");
                    ArrayNode dataNode = (ArrayNode) body.get("data");
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir paths a array de strings
                    String[] paths = new String[pathsNode.size()];
                    for (int i = 0; i < pathsNode.size(); i++) {
                        paths[i] = pathsNode.get(i).asText();
                    }
                    
                    // Convertir data de Base64 a byte[][]
                    byte[][] data = new byte[dataNode.size()][];
                    for (int i = 0; i < dataNode.size(); i++) {
                        String base64Data = dataNode.get(i).asText();
                        data[i] = Base64.getDecoder().decode(base64Data);
                    }
                    
                    String[] fileIds = coordinator.handleUploadFiles(paths, data, user.getId());
                    
                    // Crear respuesta
                    ObjectNode response = objectMapper.createObjectNode();
                    ArrayNode idsArray = response.putArray("fileIds");
                    for (String fileId : fileIds) {
                        idsArray.add(fileId != null ? fileId : "");
                    }
                    return response;
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== DESCARGA DE ARCHIVOS ==========
            
            Spark.post("/api/downloadFiles", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode pathsNode = (ArrayNode) body.get("paths");
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir paths a array de strings
                    String[] paths = new String[pathsNode.size()];
                    for (int i = 0; i < pathsNode.size(); i++) {
                        paths[i] = pathsNode.get(i).asText();
                    }
                    
                    byte[][] files = coordinator.handleDownloadFiles(paths, user.getId());
                    
                    // Convertir a Base64 y crear respuesta
                    ObjectNode response = objectMapper.createObjectNode();
                    ArrayNode dataArray = response.putArray("data");
                    for (byte[] file : files) {
                        if (file != null) {
                            dataArray.add(Base64.getEncoder().encodeToString(file));
                        } else {
                            dataArray.add("");
                        }
                    }
                    return response;
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== ELIMINACIÓN DE ARCHIVOS ==========
            
            Spark.post("/api/deleteFiles", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode pathsNode = (ArrayNode) body.get("paths");
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir paths a array de strings
                    String[] paths = new String[pathsNode.size()];
                    for (int i = 0; i < pathsNode.size(); i++) {
                        paths[i] = pathsNode.get(i).asText();
                    }
                    
                    coordinator.handleDeleteFiles(paths, user.getId());
                    return objectMapper.createObjectNode().put("message", "Archivos eliminados exitosamente");
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== MOVER/RENOMBRAR ==========
            
            Spark.post("/api/moveFiles", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode oldPathsNode = (ArrayNode) body.get("oldPaths");
                    ArrayNode newPathsNode = (ArrayNode) body.get("newPaths");
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir arrays
                    String[] oldPaths = new String[oldPathsNode.size()];
                    String[] newPaths = new String[newPathsNode.size()];
                    for (int i = 0; i < oldPathsNode.size(); i++) {
                        oldPaths[i] = oldPathsNode.get(i).asText();
                        newPaths[i] = newPathsNode.get(i).asText();
                    }
                    
                    coordinator.handleMoveFiles(oldPaths, newPaths, user.getId());
                    return objectMapper.createObjectNode().put("message", "Archivos movidos exitosamente");
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            // ========== COMPARTIR ==========
            
            Spark.post("/api/shareFiles", (req, res) -> {
                try {
                    JsonNode body = objectMapper.readTree(req.body());
                    ArrayNode pathsNode = (ArrayNode) body.get("paths");
                    String sharedWithEmail = body.get("sharedWithEmail").asText();
                    String permission = body.get("permission").asText();
                    String token = body.get("token").asText();
                    
                    // Validar token
                    com.ana.model.User user = validateToken(token);
                    if (user == null) {
                        res.status(401);
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("error", "Token inválido o expirado");
                        return error;
                    }
                    
                    // Convertir paths a array de strings
                    String[] paths = new String[pathsNode.size()];
                    for (int i = 0; i < pathsNode.size(); i++) {
                        paths[i] = pathsNode.get(i).asText();
                    }
                    
                    coordinator.handleShareFiles(paths, sharedWithEmail, permission, user.getId());
                    return objectMapper.createObjectNode().put("message", "Archivos compartidos exitosamente");
                } catch (Exception e) {
                    res.status(400);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("error", e.getMessage());
                    return error;
                }
            });
            
            
            
            System.out.println("✅ Application Server listo en http://localhost:8081");
            System.out.println("📝 Endpoints disponibles:");
            System.out.println("   POST /api/register");
            System.out.println("   POST /api/login");
            System.out.println("   POST /api/createDirectories");
            System.out.println("   POST /api/uploadFiles");
            System.out.println("   POST /api/downloadFiles");
            System.out.println("   POST /api/deleteFiles");
            System.out.println("   POST /api/moveFiles");
            System.out.println("   POST /api/shareFiles");
            System.out.println("   POST /api/getSpaceUsage");
            
        } catch (Exception e) {
            System.err.println("❌ Error iniciando Application Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Método auxiliar para validar tokens
     */
    private static com.ana.model.User validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            // Necesitamos acceder a la base de datos directamente
            // Para esto, creamos una instancia temporal o accedemos al coordinator
            DatabaseClientImpl dbClient = new DatabaseClientImpl();
            return dbClient.getUserByToken(token);
        } catch (Exception e) {
            System.err.println("Error validando token: " + e.getMessage());
            return null;
        }
    }
}