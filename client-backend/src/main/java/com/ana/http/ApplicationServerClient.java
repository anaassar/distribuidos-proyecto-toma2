package com.ana.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class ApplicationServerClient {
    
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ApplicationServerClient() {
        // Por defecto, usa localhost:8081
        this.baseUrl = "http://192.168.1.73:8081";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public ApplicationServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    // ========== AUTENTICACIÓN ==========
    
    public String registerUser(String username, String email, String password) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("username", username);
        json.put("email", email);
        json.put("password", password);
        
        HttpResponse<String> response = sendPost("/api/register", json.toString());
        validateResponse(response);
        
        JsonNode responseBody = objectMapper.readTree(response.body());
        return responseBody.get("message").asText();
    }
    
    public String loginUser(String email, String password) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("email", email);
        json.put("password", password);
        
        HttpResponse<String> response = sendPost("/api/login", json.toString());
        validateResponse(response);
        
        JsonNode responseBody = objectMapper.readTree(response.body());
        return responseBody.get("token").asText();
    }
    
    // ========== GESTIÓN DE DIRECTORIOS ==========
    
    public void createDirectories(String[] paths, String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode pathsArray = json.putArray("paths");
        for (String path : paths) {
            pathsArray.add(path);
        }
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/createDirectories", json.toString());
        validateResponse(response);
    }
    
    // ========== SUBIDA DE ARCHIVOS ==========
    
    public String[] uploadFiles(String[] paths, byte[][] data, String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode pathsArray = json.putArray("paths");
        ArrayNode dataArray = json.putArray("data");
        
        for (String path : paths) {
            pathsArray.add(path);
        }
        for (byte[] fileData : data) {
            dataArray.add(Base64.getEncoder().encodeToString(fileData));
        }
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/uploadFiles", json.toString());
        validateResponse(response);
        
        JsonNode responseBody = objectMapper.readTree(response.body());
        ArrayNode fileIdsArray = (ArrayNode) responseBody.get("fileIds");
        String[] fileIds = new String[fileIdsArray.size()];
        for (int i = 0; i < fileIdsArray.size(); i++) {
            fileIds[i] = fileIdsArray.get(i).asText();
        }
        return fileIds;
    }
    
    // ========== DESCARGA DE ARCHIVOS ==========
    
    public byte[][] downloadFiles(String[] paths, String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode pathsArray = json.putArray("paths");
        for (String path : paths) {
            pathsArray.add(path);
        }
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/downloadFiles", json.toString());
        validateResponse(response);
        
        JsonNode responseBody = objectMapper.readTree(response.body());
        ArrayNode dataArray = (ArrayNode) responseBody.get("data");
        byte[][] files = new byte[dataArray.size()][];
        for (int i = 0; i < dataArray.size(); i++) {
            String base64Data = dataArray.get(i).asText();
            if (base64Data != null && !base64Data.isEmpty()) {
                files[i] = Base64.getDecoder().decode(base64Data);
            } else {
                files[i] = new byte[0];
            }
        }
        return files;
    }
    
    // ========== ELIMINACIÓN DE ARCHIVOS ==========
    
    public void deleteFiles(String[] paths, String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode pathsArray = json.putArray("paths");
        for (String path : paths) {
            pathsArray.add(path);
        }
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/deleteFiles", json.toString());
        validateResponse(response);
    }
    
    // ========== MOVER/RENOMBRAR ==========
    
    public void moveFiles(String[] oldPaths, String[] newPaths, String token) 
            throws IOException, InterruptedException {
        if (oldPaths.length != newPaths.length) {
            throw new IllegalArgumentException("El número de rutas antiguas y nuevas debe coincidir");
        }
        
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode oldPathsArray = json.putArray("oldPaths");
        ArrayNode newPathsArray = json.putArray("newPaths");
        
        for (String oldPath : oldPaths) {
            oldPathsArray.add(oldPath);
        }
        for (String newPath : newPaths) {
            newPathsArray.add(newPath);
        }
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/moveFiles", json.toString());
        validateResponse(response);
    }
    
    // ========== COMPARTIR ==========
    
    public void shareFiles(String[] paths, String sharedWithEmail, String permission, String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode pathsArray = json.putArray("paths");
        for (String path : paths) {
            pathsArray.add(path);
        }
        json.put("sharedWithEmail", sharedWithEmail);
        json.put("permission", permission);
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/shareFiles", json.toString());
        validateResponse(response);
    }
    
    // ========== REPORTES ==========
    
    public String getSpaceUsage(String token) 
            throws IOException, InterruptedException {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("token", token);
        
        HttpResponse<String> response = sendPost("/api/getSpaceUsage", json.toString());
        validateResponse(response);
        
        return response.body();
    }
    
    // ========== MÉTODOS AUXILIARES ==========
    
    private HttpResponse<String> sendPost(String endpoint, String jsonBody) 
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    private void validateResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            try {
                JsonNode errorBody = objectMapper.readTree(response.body());
                String errorMessage = errorBody.has("error") ? 
                    errorBody.get("error").asText() : 
                    "Error desconocido";
                throw new RuntimeException("HTTP " + statusCode + ": " + errorMessage);
            } catch (Exception e) {
                throw new RuntimeException("HTTP " + statusCode + ": " + response.body());
            }
        }
    }
}