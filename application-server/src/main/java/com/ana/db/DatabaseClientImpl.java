package com.ana.db;

import com.ana.model.FileMetadata;
import com.ana.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseClientImpl implements DatabaseClient {

    private String getUrl() {
        return "jdbc:sqlserver://localhost:1433;databaseName=PROYECTO_DISTRIBUIDOS;encrypt=false;";
    }

    private Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "1234");
        return props;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getUrl(), getProperties());
    }

    @Override
    public void saveFileMetadata(FileMetadata metadata) {
        String checkUserSql = "SELECT COUNT(*) FROM users WHERE id = ?";
        String insertSql = "INSERT INTO files (name, path, size_bytes, owner_id, directory_id, created_at, updated_at) "
                +
                "VALUES (?, ?, ?, ?, ?, GETDATE(), GETDATE())";

        try (Connection conn = getConnection(); // ← ¡Obtiene conexión nueva!
                PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {

            checkStmt.setInt(1, metadata.getOwnerId());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    throw new RuntimeException("Usuario no encontrado con ID: " + metadata.getOwnerId());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando usuario", e);
        }

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, metadata.getName());
            stmt.setString(2, metadata.getPath());
            stmt.setLong(3, metadata.getSizeBytes());
            stmt.setInt(4, metadata.getOwnerId());
            stmt.setObject(5, metadata.getDirectoryId());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    metadata.setId(String.valueOf(rs.getInt(1))); // ← ID numérico como String
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving file metadata", e);
        }
    }

    @Override
    public FileMetadata getFileMetadata(String path) {
        String sql = "SELECT id, name, path, size_bytes, owner_id, directory_id, created_at, updated_at " +
                "FROM files WHERE path = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, path);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileMetadata meta = new FileMetadata();
                    meta.setId(String.valueOf(rs.getInt("id")));
                    meta.setName(rs.getString("name"));
                    meta.setPath(rs.getString("path"));
                    meta.setSizeBytes(rs.getLong("size_bytes"));
                    meta.setOwnerId(rs.getInt("owner_id"));
                    meta.setDirectoryId(rs.getInt("directory_id"));
                    return meta;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching file metadata", e);
        }
        return null;
    }

    @Override
    public void saveReplicas(String fileId, List<String> nodeIds) {
        String sql = "INSERT INTO file_replicas (file_id, node_id, stored_at, is_healthy) VALUES (?, ?, GETDATE(), 1)";

        try (Connection conn = getConnection(); // ← ¡Conexión nueva!
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (String nodeId : nodeIds) {
                stmt.setInt(1, Integer.parseInt(fileId)); // ← ¡Convierte a INT!
                stmt.setString(2, nodeId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving replicas", e);
        }
    }

    @Override
    public List<String> getReplicaNodeIds(String fileId) {
        String sql = "SELECT node_id FROM file_replicas WHERE file_id = ? AND is_healthy = 1";
        List<String> nodeIds = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Integer.parseInt(fileId)); // ← ¡Convierte a INT!
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    nodeIds.add(rs.getString("node_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching replica nodes", e);
        }
        return nodeIds;
    }

    @Override
    public void createDirectory(String path, int ownerId) {
        if (path == null || path.isEmpty() || !path.startsWith("/")) {
            throw new IllegalArgumentException("Ruta inválida: debe empezar con '/'");
        }

        // Validar que la ruta sea de este usuario: /user1/...
        String expectedPrefix = "/user" + ownerId + "/";
        if (!path.equals("/user" + ownerId) && !path.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Ruta debe pertenecer al usuario: " + expectedPrefix);
        }

        // Obtener todos los segmentos de la ruta
        String[] segments = path.split("/");
        List<String> currentPathParts = new ArrayList<>();
        Integer parentId = null; // ← ¡CAMBIADO A Integer!

        for (String segment : segments) {
            if (segment.isEmpty())
                continue;
            currentPathParts.add(segment);
            String currentPath = "/" + String.join("/", currentPathParts);

            // Verificar si ya existe
            Integer existingId = getDirectoryIdByPath(currentPath, ownerId);
            if (existingId != null) {
                parentId = existingId;
                continue;
            }

            // Insertar nuevo directorio
            String sql = "INSERT INTO directories (path, parent_id, owner_id) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, currentPath);
                stmt.setObject(2, parentId); // ← setObject maneja null correctamente
                stmt.setInt(3, ownerId);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        parentId = rs.getInt(1); // ← rs.getInt() devuelve int, pero lo asignamos a Integer
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error creando directorio: " + currentPath, e);
            }
        }
    }

    private Integer getDirectoryIdByPath(String path, int ownerId) {
        String sql = "SELECT id FROM directories WHERE path = ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando directorio", e);
        }
        return null;
    }

    @Override
    public FileMetadata getFileMetadata(String path, int ownerId) {
        String sql = "SELECT id, name, path, size_bytes, owner_id, directory_id " +
                "FROM files WHERE path = ? AND owner_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, path);
            stmt.setInt(2, ownerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileMetadata meta = new FileMetadata();
                    meta.setId(String.valueOf(rs.getInt("id")));
                    meta.setName(rs.getString("name"));
                    meta.setPath(rs.getString("path"));
                    meta.setSizeBytes(rs.getLong("size_bytes"));
                    meta.setOwnerId(rs.getInt("owner_id"));
                    meta.setDirectoryId(rs.getInt("directory_id"));
                    return meta;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching file metadata", e);
        }
        return null;
    }

    @Override
    public void registerUser(String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, passwordHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error registrando usuario", e);
        }
    }

    public User getUserByEmail(String email) {
        String sql = "SELECT id, username, email, password_hash FROM users WHERE email = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario", e);
        }
        return null;
    }

    @Override
    public void createSession(String token, int userId, LocalDateTime expiresAt) {
        String sql = "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.setInt(2, userId);
            stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creando sesión", e);
        }
    }

    @Override
    public User getUserByToken(String token) {
        String sql = "SELECT u.id, u.username, u.email " +
                "FROM users u " +
                "INNER JOIN sessions s ON u.id = s.user_id " +
                "WHERE s.token = ? AND s.expires_at > GETDATE()";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error validando token", e);
        }
        return null;
    }

    @Override
    public void shareFile(int fileId, int sharedWithUserId, String permissionLevel) {
        if (!"read".equals(permissionLevel) && !"write".equals(permissionLevel)) {
            throw new IllegalArgumentException("Nivel de permiso inválido. Use 'read' o 'write'");
        }

        String sql = "INSERT INTO file_shares (file_id, shared_with_user_id, permission_level) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            stmt.setInt(2, sharedWithUserId);
            stmt.setString(3, permissionLevel);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                throw new RuntimeException("El archivo ya está compartido con este usuario", e);
            }
            throw new RuntimeException("Error compartiendo archivo", e);
        }
    }

    @Override
    public void moveFile(String oldPath, String newPath, int ownerId) {

        // Verificar que sea un archivo (no directorio)
        if (directoryExists(oldPath, ownerId)) {
            throw new IllegalArgumentException("La ruta es un directorio, no un archivo");
        }
        // Validar rutas
        if (oldPath == null || newPath == null || oldPath.equals(newPath)) {
            throw new IllegalArgumentException("Rutas inválidas");
        }

        // Verificar que el archivo/directorio exista y pertenezca al owner
        String checkSql = "SELECT id FROM files WHERE path = ? AND owner_id = ?";
        Integer fileId = null;
        try (Connection conn = getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, oldPath);
            checkStmt.setInt(2, ownerId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    fileId = rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando archivo", e);
        }

        if (fileId == null) {
            throw new IllegalArgumentException("Archivo no encontrado o no tienes permisos");
        }

        // Actualizar la ruta
        String updateSql = "UPDATE files SET path = ? WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setString(1, newPath);
            updateStmt.setInt(2, fileId);
            int rows = updateStmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("No se pudo actualizar el archivo");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error moviendo archivo", e);
        }
    }

    @Override
    public boolean directoryExists(String path, int ownerId) {
        String sql = "SELECT 1 FROM directories WHERE path = ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando directorio", e);
        }
    }

    @Override
    public void moveDirectory(String oldPath, String newPath, int ownerId) {
        if (oldPath == null || newPath == null || oldPath.equals(newPath)) {
            throw new IllegalArgumentException("Rutas inválidas");
        }

        // Verificar que el directorio origen exista
        if (!directoryExists(oldPath, ownerId)) {
            throw new IllegalArgumentException("Directorio origen no encontrado: " + oldPath);
        }

        // Verificar que el directorio destino no exista
        if (directoryExists(newPath, ownerId)) {
            throw new IllegalArgumentException("El directorio destino ya existe: " + newPath);
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Actualizar subdirectorios
                String updateDirsSql = "UPDATE directories SET path = REPLACE(path, ?, ?) WHERE path LIKE ? AND owner_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateDirsSql)) {
                    stmt.setString(1, oldPath);
                    stmt.setString(2, newPath);
                    stmt.setString(3, oldPath + "/%");
                    stmt.setInt(4, ownerId);
                    stmt.executeUpdate();
                }

                // Actualizar el directorio raíz
                String updateRootDirSql = "UPDATE directories SET path = ? WHERE path = ? AND owner_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateRootDirSql)) {
                    stmt.setString(1, newPath);
                    stmt.setString(2, oldPath);
                    stmt.setInt(3, ownerId);
                    stmt.executeUpdate();
                }

                // Actualizar archivos
                String updateFilesSql = "UPDATE files SET path = REPLACE(path, ?, ?) WHERE path LIKE ? AND owner_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateFilesSql)) {
                    stmt.setString(1, oldPath);
                    stmt.setString(2, newPath);
                    stmt.setString(3, oldPath + "/%");
                    stmt.setInt(4, ownerId);
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error moviendo directorio", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en transacción", e);
        }
    }

    @Override
    public List<Integer> getFilesInDirectory(String directoryPath, int ownerId) {
        List<Integer> fileIds = new ArrayList<>();

        // Obtener todos los archivos cuya ruta empieza con el directorio
        String sql = "SELECT id FROM files WHERE path LIKE ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, directoryPath + "/%");
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    fileIds.add(rs.getInt("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error obteniendo archivos del directorio", e);
        }
        return fileIds;
    }

    @Override
    public boolean isFile(String path, int ownerId) {
        String sql = "SELECT 1 FROM files WHERE path = ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando archivo", e);
        }
    }

    @Override
    public boolean isDirectory(String path, int ownerId) {
        String sql = "SELECT 1 FROM directories WHERE path = ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando directorio", e);
        }
    }

    @Override
    public List<Integer> getFileIdsInDirectory(String directoryPath, int ownerId) {
        List<Integer> fileIds = new ArrayList<>();
        String sql = "SELECT id FROM files WHERE path LIKE ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, directoryPath + "/%");
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    fileIds.add(rs.getInt("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error obteniendo archivos del directorio", e);
        }
        return fileIds;
    }

    @Override
    public void deleteFile(int fileId) {
        // Primero eliminar réplicas
        String deleteReplicasSql = "DELETE FROM file_replicas WHERE file_id = ?";
        // Luego eliminar archivo
        String deleteFileSql = "DELETE FROM files WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(deleteReplicasSql)) {
                    stmt.setInt(1, fileId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(deleteFileSql)) {
                    stmt.setInt(1, fileId);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error eliminando archivo", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en transacción", e);
        }
    }

    @Override
    public void deleteFilesInDirectory(String directoryPath, int ownerId) {
        List<Integer> fileIds = getFileIdsInDirectory(directoryPath, ownerId);
        for (Integer fileId : fileIds) {
            deleteFile(fileId);
        }
    }

    @Override
    public void deleteDirectory(String directoryPath, int ownerId) {
        // Eliminar subdirectorios recursivamente (de más profundo a menos)
        String deleteSubDirsSql = "DELETE FROM directories WHERE path LIKE ? AND owner_id = ? AND path != ?";
        // Eliminar directorio raíz
        String deleteRootDirSql = "DELETE FROM directories WHERE path = ? AND owner_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Eliminar subdirectorios
                try (PreparedStatement stmt = conn.prepareStatement(deleteSubDirsSql)) {
                    stmt.setString(1, directoryPath + "/%");
                    stmt.setInt(2, ownerId);
                    stmt.setString(3, directoryPath);
                    stmt.executeUpdate();
                }
                // Eliminar directorio raíz
                try (PreparedStatement stmt = conn.prepareStatement(deleteRootDirSql)) {
                    stmt.setString(1, directoryPath);
                    stmt.setInt(2, ownerId);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error eliminando directorio", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en transacción", e);
        }
    }

    @Override
    public boolean hasReadAccess(int userId, int fileId) {
        String sql = "SELECT 1 FROM files f WHERE f.id = ? AND f.owner_id = ? " +
                "UNION " +
                "SELECT 1 FROM file_shares fs WHERE fs.file_id = ? AND fs.shared_with_user_id = ? " +
                "AND fs.permission_level IN ('read', 'write')";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            stmt.setInt(3, fileId);
            stmt.setInt(4, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Si hay al menos 1 fila, tiene acceso
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verificando permisos", e);
        }
    }

    @Override
    public FileMetadata getFileByPath(String path) {
        String sql = "SELECT id, name, path, size_bytes, owner_id, directory_id " +
                "FROM files WHERE path = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileMetadata meta = new FileMetadata();
                    meta.setId(String.valueOf(rs.getInt("id")));
                    meta.setName(rs.getString("name"));
                    meta.setPath(rs.getString("path"));
                    meta.setSizeBytes(rs.getLong("size_bytes"));
                    meta.setOwnerId(rs.getInt("owner_id"));
                    meta.setDirectoryId(rs.getInt("directory_id"));
                    return meta;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando archivo por path", e);
        }
        return null;
    }

    @Override
    public FileMetadata getFileByPathAndOwner(String path, int ownerId) {
        String sql = "SELECT id, name, path, size_bytes, owner_id, directory_id " +
                "FROM files WHERE path = ? AND owner_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, path);
            stmt.setInt(2, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileMetadata meta = new FileMetadata();
                    meta.setId(String.valueOf(rs.getInt("id")));
                    meta.setName(rs.getString("name"));
                    meta.setPath(rs.getString("path"));
                    meta.setSizeBytes(rs.getLong("size_bytes"));
                    meta.setOwnerId(rs.getInt("owner_id"));
                    meta.setDirectoryId(rs.getInt("directory_id"));
                    return meta;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando archivo", e);
        }
        return null;
    }

}