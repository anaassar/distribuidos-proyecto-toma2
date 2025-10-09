package com.ana.db;

import com.ana.model.FileMetadata;
import com.ana.model.User;

import java.time.LocalDateTime;
import java.util.List;

public interface DatabaseClient {

    // File metadata and replicas
    void saveFileMetadata(FileMetadata metadata);
    FileMetadata getFileMetadata(String path);
    FileMetadata getFileMetadata(String path, int ownerId);
    List<String> getReplicaNodeIds(String fileId);
    void saveReplicas(String fileId, List<String> nodeIds);

    // User and files

    User getUserByToken(String token);
    User getUserByEmail(String email);
    FileMetadata getFileByPathAndOwner(String path, int ownerId);
    FileMetadata getFileByPath(String path);
    void createSession(String token, int userId, LocalDateTime expiresAt);

    // Negocio
    void registerUser(String username, String email, String passwordHash);
    boolean hasReadAccess(int userId, int fileId);
    void createDirectory(String path, int ownerId);
    void moveDirectory(String oldPath, String newPath, int ownerId);
    boolean directoryExists(String path, int ownerId);
    List<Integer> getFilesInDirectory(String directoryPath, int ownerId);
    void shareFile(int fileId, int sharedWithUserId, String permissionLevel);
    void moveFile(String oldPath, String newPath, int ownerId);
    void deleteFile(int fileId);
    void deleteFilesInDirectory(String directoryPath, int ownerId);
    void deleteDirectory(String directoryPath, int ownerId);
    List<Integer> getFileIdsInDirectory(String directoryPath, int ownerId);
    boolean isDirectory(String path, int ownerId);
    boolean isFile(String path, int ownerId);
    // SQL
    java.sql.Connection getConnection() throws java.sql.SQLException;
}