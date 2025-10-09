package com.ana.node;

import java.io.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación del nodo de almacenamiento
 * Se registra automáticamente en RMI Registry
 */
public class NodeWorker extends UnicastRemoteObject implements StorageNode {

    private final String nodeId;
    private final File storageDir;
    private final Map<String, File> fileMap; // fileId -> File
    private final Object fileMapLock = new Object();

    protected NodeWorker(String nodeId, String storagePath) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.storageDir = new File(storagePath);
        this.fileMap = new ConcurrentHashMap<>();

        // Crear directorio de almacenamiento si no existe
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                throw new RemoteException("No se pudo crear el directorio de almacenamiento: " + storagePath);
            }
        }

        if (!storageDir.isDirectory() || !storageDir.canWrite()) {
            throw new RemoteException("Directorio de almacenamiento inválido o sin permisos: " + storagePath);
        }

        // Cargar archivos existentes al iniciar
        loadExistingFiles();

        // Registrar en RMI Registry
        registerWithRegistry();
    }

    /**
     * Carga los archivos existentes en el directorio de almacenamiento
     */
    private void loadExistingFiles() {
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileMap.put(file.getName(), file);
                    System.out.println("📁 Archivo existente cargado: " + file.getName());
                }
            }
        }
    }

    /**
     * Registra el nodo en el RMI Registry
     */
    private void registerWithRegistry() throws RemoteException {
        try {
            // Intentar crear el RMI Registry si no existe
            try {
                java.rmi.registry.LocateRegistry.createRegistry(1099);
                System.out.println("✅ RMI Registry iniciado en puerto 1099.");
            } catch (Exception e) {
                // Ya existe, está bien
            }

            Naming.rebind("rmi://localhost:1099/" + nodeId, this);
            System.out.println("✅ Nodo '" + nodeId + "' registrado en RMI Registry.");
        } catch (Exception e) {
            throw new RemoteException("Error registrando nodo en RMI Registry", e);
        }
    }

    @Override
    public String getNodeId() throws RemoteException {
        return nodeId;
    }

    @Override
    public boolean isHealthy() throws RemoteException {
        return storageDir.exists() && storageDir.canWrite();
    }

    @Override
    public void storeFile(String fileId, byte[] data) throws RemoteException {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new RemoteException("File ID inválido");
        }
        if (data == null) {
            throw new RemoteException("Datos nulos");
        }

        try {
            File file = new File(storageDir, fileId);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            
            synchronized (fileMapLock) {
                fileMap.put(fileId, file);
            }
            
            System.out.println("💾 Archivo '" + fileId + "' almacenado en nodo '" + nodeId + "'");
        } catch (IOException e) {
            throw new RemoteException("Error guardando archivo: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] readFile(String fileId) throws RemoteException {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new RemoteException("File ID inválido");
        }

        File file;
        synchronized (fileMapLock) {
            file = fileMap.get(fileId);
        }
        
        if (file == null || !file.exists()) {
            throw new RemoteException("Archivo no encontrado: " + fileId);
        }

        try {
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                int totalRead = 0;
                while (totalRead < data.length) {
                    int read = fis.read(data, totalRead, data.length - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                if (totalRead != data.length) {
                    throw new RemoteException("Error leyendo archivo completo");
                }
            }
            System.out.println("📥 Archivo '" + fileId + "' leído desde nodo '" + nodeId + "'");
            return data;
        } catch (IOException e) {
            throw new RemoteException("Error leyendo archivo: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileId) throws RemoteException {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new RemoteException("File ID inválido");
        }

        File file;
        synchronized (fileMapLock) {
            file = fileMap.remove(fileId);
        }
        
        if (file != null && file.exists()) {
            if (file.delete()) {
                System.out.println("🗑️ Archivo '" + fileId + "' eliminado del nodo '" + nodeId + "'");
            } else {
                System.err.println("⚠️ No se pudo eliminar el archivo físico: " + fileId);
                // Re-agregar al mapa si no se pudo eliminar
                synchronized (fileMapLock) {
                    fileMap.put(fileId, file);
                }
            }
        }
    }

    @Override
    public boolean exists(String fileId) throws RemoteException {
        if (fileId == null || fileId.trim().isEmpty()) {
            return false;
        }
        
        synchronized (fileMapLock) {
            File file = fileMap.get(fileId);
            return file != null && file.exists();
        }
    }

    @Override
    public long getFreeSpace() throws RemoteException {
        return storageDir.getFreeSpace();
    }

    /**
     * Método principal para iniciar el nodo
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java -jar storage-node.jar <nodeId> <storagePath>");
            System.out.println("Ejemplo: java -jar storage-node.jar node1 /data/node1");
            return;
        }

        String nodeId = args[0];
        String storagePath = args[1];

        try {
            NodeWorker node = new NodeWorker(nodeId, storagePath);
            System.out.println("🟢 Nodo '" + nodeId + "' listo y esperando conexiones...");
            System.out.println("📝 Directorio de almacenamiento: " + storagePath);
            System.out.println("📊 Espacio libre: " + formatBytes(node.getFreeSpace()));
            System.out.println("🔌 Registrado en: rmi://localhost:1099/" + nodeId);
            System.out.println("⏹️  Presiona Enter para detener el nodo...");
            System.in.read();
            
        } catch (Exception e) {
            System.err.println("❌ Error iniciando nodo '" + nodeId + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Formatea bytes a una representación legible
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}