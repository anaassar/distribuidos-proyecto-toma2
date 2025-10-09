package com.ana.coordinator;

import com.ana.db.DatabaseClient;
import com.ana.model.FileMetadata;
import com.ana.model.User;
import com.ana.node.StorageNode;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator {
    
    private final DatabaseClient dbClient;
    private final Map<String, StorageNode> nodeMap = new ConcurrentHashMap<>();
    private final String registryHost;
    private final int registryPort;
    private final int replicaCount;

    public Coordinator(DatabaseClient dbClient, String registryHost, int registryPort, int replicaCount) {
        this.dbClient = dbClient;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        this.replicaCount = replicaCount;
        registerKnownNodes();
    }

    private void registerKnownNodes() {
        String[] nodeIds = {"node1", "node2", "node3"};
        for (String nodeId : nodeIds) {
            try {
                StorageNode node = (StorageNode) Naming.lookup(
                    "rmi://" + registryHost + ":" + registryPort + "/" + nodeId);
                if (node.isHealthy()) {
                    nodeMap.put(nodeId, node);
                    System.out.println("✅ Nodo " + nodeId + " conectado y saludable.");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Nodo " + nodeId + " no disponible: " + e.getMessage());
            }
        }
    }

    // ========== AUTENTICACIÓN ==========
    
    public String handleRegisterUser(String username, String email, String password) {
        if (username == null || email == null || password == null) {
            throw new IllegalArgumentException("Datos incompletos");
        }
        
        User existing = null;
        try {
            existing = dbClient.getUserByEmail(email);
        } catch (Exception e) {
            System.err.println("Error al verificar email: " + e.getMessage());
            e.printStackTrace();
        }
        if (existing != null) {
            throw new IllegalArgumentException("Email ya registrado");
        }
        
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
        dbClient.registerUser(username, email, passwordHash);
        return "Usuario registrado exitosamente";
    }
    
    public String handleLoginUser(String email, String password) {
        if (email == null || password == null) {
            throw new IllegalArgumentException("Credenciales incompletas");
        }
        
        User user = dbClient.getUserByEmail(email);
        if (user == null || !org.mindrot.jbcrypt.BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }
        
        String token = java.util.UUID.randomUUID().toString();
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusHours(24);
        dbClient.createSession(token, user.getId(), expiresAt);
        return token;
    }

    // ========== GESTIÓN DE DIRECTORIOS ==========
    
    public void handleCreateDirectories(String[] paths, int ownerId) {
        for (String path : paths) {
            if (path == null || path.isEmpty() || !path.startsWith("/")) {
                throw new IllegalArgumentException("Ruta inválida: " + path);
            }
            
            String expectedPrefix = "/user" + ownerId + "/";
            if (!path.equals("/user" + ownerId) && !path.startsWith(expectedPrefix)) {
                throw new IllegalArgumentException("Ruta debe pertenecer al usuario: " + expectedPrefix);
            }
            
            dbClient.createDirectory(path, ownerId);
        }
    }

    // ========== SUBIDA DE ARCHIVOS ==========
    
    public String[] handleUploadFiles(String[] paths, byte[][] data, int ownerId) {
        if (paths.length != data.length) {
            throw new IllegalArgumentException("El número de rutas y datos debe coincidir");
        }
        
        String[] fileIds = new String[paths.length];
        
        for (int i = 0; i < paths.length; i++) {
            try {
                // Validar ruta
                String path = paths[i];
                if (path == null || path.isEmpty() || !path.startsWith("/")) {
                    throw new IllegalArgumentException("Ruta inválida: " + path);
                }
                
                String expectedPrefix = "/user" + ownerId + "/";
                if (!path.startsWith(expectedPrefix)) {
                    throw new IllegalArgumentException("Ruta debe pertenecer al usuario: " + expectedPrefix);
                }
                
                // Crear metadatos
                FileMetadata metadata = new FileMetadata(path, data[i].length, ownerId);
                dbClient.saveFileMetadata(metadata);
                String fileId = metadata.getId();
                
                // Seleccionar nodos
                List<StorageNode> nodes = getAvailableNodes(replicaCount);
                if (nodes.size() < 2) {
                    throw new IllegalStateException("No hay suficientes nodos disponibles para redundancia");
                }
                
                // Almacenar en nodos
                List<String> successfulNodes = new ArrayList<>();
                for (StorageNode node : nodes) {
                    try {
                        node.storeFile(fileId, data[i]);
                        successfulNodes.add(node.getNodeId());
                    } catch (RemoteException e) {
                        System.err.println("⚠️ Error almacenando en nodo: " + e.getMessage());
                        continue;
                    }
                }
                
                if (successfulNodes.isEmpty()) {
                    throw new RuntimeException("Ningún nodo aceptó el archivo");
                }
                
                // Registrar réplicas
                dbClient.saveReplicas(fileId, successfulNodes);
                fileIds[i] = fileId;
                
            } catch (Exception e) {
                System.err.println("❌ Error subiendo archivo " + paths[i] + ": " + e.getMessage());
                fileIds[i] = null;
            }
        }
        
        return fileIds;
    }

    // ========== DESCARGA DE ARCHIVOS ==========
    
    public byte[][] handleDownloadFiles(String[] paths, int userId) {
        byte[][] files = new byte[paths.length][];
        
        for (int i = 0; i < paths.length; i++) {
            try {
                String path = paths[i];
                if (path == null || path.isEmpty()) {
                    throw new IllegalArgumentException("Ruta inválida");
                }
                
                // Buscar archivo
                FileMetadata meta = dbClient.getFileByPath(path);
                if (meta == null) {
                    throw new IllegalArgumentException("Archivo no encontrado: " + path);
                }
                
                // Verificar permisos
                if (!dbClient.hasReadAccess(userId, Integer.parseInt(meta.getId()))) {
                    throw new IllegalArgumentException("Acceso denegado para: " + path);
                }
                
                // Obtener nodos
                List<String> nodeIds = dbClient.getReplicaNodeIds(meta.getId());
                if (nodeIds.isEmpty()) {
                    throw new IllegalStateException("No hay réplicas disponibles para: " + path);
                }
                
                // Intentar descargar
                boolean downloaded = false;
                for (String nodeId : nodeIds) {
                    StorageNode node = nodeMap.get(nodeId);
                    if (node != null) {
                        try {
                            if (node.isHealthy()) {
                                files[i] = node.readFile(meta.getId());
                                downloaded = true;
                                break;
                            }
                        } catch (RemoteException e) {
                            System.err.println("⚠️ Error descargando de nodo " + nodeId + ": " + e.getMessage());
                            continue;
                        }
                    }
                }
                
                if (!downloaded) {
                    throw new IllegalStateException("No se pudo descargar el archivo de ningún nodo: " + path);
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error descargando archivo " + paths[i] + ": " + e.getMessage());
                files[i] = null;
            }
        }
        
        return files;
    }

    // ========== ELIMINACIÓN DE ARCHIVOS Y DIRECTORIOS ==========
    
    public void handleDeleteFiles(String[] paths, int userId) {
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Ruta inválida");
            }
            
            // Verificar que exista y pertenezca al usuario
            if (!dbClient.isFile(path, userId) && !dbClient.isDirectory(path, userId)) {
                throw new IllegalArgumentException("Ruta no encontrada o acceso denegado: " + path);
            }
            
            if (dbClient.isFile(path, userId)) {
                // Eliminar archivo
                FileMetadata file = dbClient.getFileByPathAndOwner(path, userId);
                if (file == null) continue;
                
                // Eliminar de nodos
                List<String> nodeIds = dbClient.getReplicaNodeIds(file.getId());
                for (String nodeId : nodeIds) {
                    StorageNode node = nodeMap.get(nodeId);
                    if (node != null) {
                        try {
                            node.deleteFile(file.getId());
                            System.out.println("🗑️ Archivo " + file.getId() + " eliminado del nodo " + nodeId);
                        } catch (RemoteException e) {
                            System.err.println("⚠️ Error eliminando de nodo " + nodeId + ": " + e.getMessage());
                        }
                    }
                }
                
                // Eliminar metadatos
                dbClient.deleteFile(Integer.parseInt(file.getId()));
                
            } else if (dbClient.isDirectory(path, userId)) {
                // Eliminar directorio recursivamente
                List<Integer> fileIds = dbClient.getFileIdsInDirectory(path, userId);
                for (Integer fileId : fileIds) {
                    String fileIdStr = String.valueOf(fileId);
                    List<String> nodeIds = dbClient.getReplicaNodeIds(fileIdStr);
                    for (String nodeId : nodeIds) {
                        StorageNode node = nodeMap.get(nodeId);
                        if (node != null) {
                            try {
                                node.deleteFile(fileIdStr);
                                System.out.println("🗑️ Archivo " + fileIdStr + " eliminado del nodo " + nodeId);
                            } catch (RemoteException e) {
                                System.err.println("⚠️ Error eliminando de nodo " + nodeId + ": " + e.getMessage());
                            }
                        }
                    }
                }
                
                // Eliminar metadatos
                dbClient.deleteFilesInDirectory(path, userId);
                dbClient.deleteDirectory(path, userId);
            }
        }
    }

    // ========== MOVER/RENOMBRAR ==========
    
    public void handleMoveFiles(String[] oldPaths, String[] newPaths, int userId) {
        if (oldPaths.length != newPaths.length) {
            throw new IllegalArgumentException("El número de rutas antiguas y nuevas debe coincidir");
        }
        
        for (int i = 0; i < oldPaths.length; i++) {
            String oldPath = oldPaths[i];
            String newPath = newPaths[i];
            
            if (oldPath == null || newPath == null || oldPath.equals(newPath)) {
                throw new IllegalArgumentException("Rutas inválidas");
            }
            
            // Validar que las rutas pertenezcan al usuario
            String expectedPrefix = "/user" + userId + "/";
            if (!oldPath.startsWith(expectedPrefix) || !newPath.startsWith(expectedPrefix)) {
                throw new IllegalArgumentException("Solo puedes mover archivos en tu espacio");
            }
            
            // Verificar si es archivo o directorio
            boolean isFile = dbClient.isFile(oldPath, userId);
            boolean isDirectory = dbClient.isDirectory(oldPath, userId);
            
            if (isFile && !isDirectory) {
                // Mover archivo
                FileMetadata file = dbClient.getFileByPathAndOwner(oldPath, userId);
                if (file == null) {
                    throw new IllegalArgumentException("Archivo no encontrado: " + oldPath);
                }
                dbClient.moveFile(oldPath, newPath, userId);
                
            } else if (isDirectory && !isFile) {
                // Mover directorio
                if (!dbClient.isDirectory(oldPath, userId)) {
                    throw new IllegalArgumentException("Directorio origen no encontrado: " + oldPath);
                }
                if (dbClient.isDirectory(newPath, userId)) {
                    throw new IllegalArgumentException("El directorio destino ya existe: " + newPath);
                }
                dbClient.moveDirectory(oldPath, newPath, userId);
                
            } else {
                throw new IllegalArgumentException("Ruta no encontrada o conflicto: " + oldPath);
            }
        }
    }

    // ========== COMPARTIR ==========
    
    public void handleShareFiles(String[] paths, String sharedWithEmail, String permission, int ownerId) {
        if (sharedWithEmail == null || permission == null) {
            throw new IllegalArgumentException("Datos de compartición incompletos");
        }
        
        if (!"read".equals(permission) && !"write".equals(permission)) {
            throw new IllegalArgumentException("Nivel de permiso inválido. Use 'read' o 'write'");
        }
        
        // Buscar usuario destinatario
        User sharedUser = dbClient.getUserByEmail(sharedWithEmail);
        if (sharedUser == null) {
            throw new IllegalArgumentException("Usuario destinatario no encontrado: " + sharedWithEmail);
        }
        if (sharedUser.getId() == ownerId) {
            throw new IllegalArgumentException("No puedes compartir contigo mismo");
        }
        
        for (String path : paths) {
            if (path == null) continue;
            
            // Verificar si es archivo o directorio
            boolean isFile = dbClient.isFile(path, ownerId);
            boolean isDirectory = dbClient.isDirectory(path, ownerId);
            
            if (isFile && !isDirectory) {
                // Compartir archivo
                FileMetadata file = dbClient.getFileByPathAndOwner(path, ownerId);
                if (file == null) {
                    throw new IllegalArgumentException("Archivo no encontrado: " + path);
                }
                dbClient.shareFile(Integer.parseInt(file.getId()), sharedUser.getId(), permission);
                
            } else if (isDirectory && !isFile) {
                // Compartir directorio (todos los archivos)
                List<Integer> fileIds = dbClient.getFileIdsInDirectory(path, ownerId);
                if (fileIds.isEmpty()) {
                    throw new IllegalArgumentException("El directorio está vacío: " + path);
                }
                for (Integer fileId : fileIds) {
                    dbClient.shareFile(fileId, sharedUser.getId(), permission);
                }
                
            } else {
                throw new IllegalArgumentException("Ruta no encontrada: " + path);
            }
        }
    }

    // ========== MÉTODOS AUXILIARES ==========
    
    private List<StorageNode> getAvailableNodes(int count) {
        List<StorageNode> available = new ArrayList<>();
        for (StorageNode node : nodeMap.values()) {
            try {
                if (node.isHealthy()) {
                    available.add(node);
                    if (available.size() >= count) break;
                }
            } catch (RemoteException e) {
                System.err.println("❌ Nodo no saludable: " + e.getMessage());
            }
        }
        return available;
    }
}