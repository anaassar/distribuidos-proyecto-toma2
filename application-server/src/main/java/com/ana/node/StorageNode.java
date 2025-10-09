package com.ana.node;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaz RMI para operaciones de almacenamiento
 */
public interface StorageNode extends Remote {
    
    /**
     * Obtiene el ID único del nodo
     */
    String getNodeId() throws RemoteException;
    
    /**
     * Verifica si el nodo está saludable
     */
    boolean isHealthy() throws RemoteException;
    
    /**
     * Almacena un archivo en el nodo
     * @param fileId ID único del archivo
     * @param data Contenido del archivo
     */
    void storeFile(String fileId, byte[] data) throws RemoteException;
    
    /**
     * Lee un archivo del nodo
     * @param fileId ID único del archivo
     * @return Contenido del archivo
     */
    byte[] readFile(String fileId) throws RemoteException;
    
    /**
     * Elimina un archivo del nodo
     * @param fileId ID único del archivo
     */
    void deleteFile(String fileId) throws RemoteException;
    
    /**
     * Verifica si un archivo existe en el nodo
     * @param fileId ID único del archivo
     * @return true si existe, false si no
     */
    boolean exists(String fileId) throws RemoteException;
    
    /**
     * Obtiene el espacio libre en el nodo
     * @return Espacio libre en bytes
     */
    long getFreeSpace() throws RemoteException;
}