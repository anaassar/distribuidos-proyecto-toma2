package com.ana.soap;

import com.ana.http.ApplicationServerClient;
import jakarta.jws.WebService;

import java.io.IOException;
import java.net.http.HttpResponse;

@WebService(endpointInterface = "com.ana.soap.FileServiceSOAP")
public class FileServiceImpl implements FileServiceSOAP {
    
    private final ApplicationServerClient client = new ApplicationServerClient();
    
    @Override
    public String registerUser(String username, String email, String password) {
        try {
            return client.registerUser(username, email, password);
        } catch (Exception e) {
            throw new RuntimeException("Error en registerUser: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String loginUser(String email, String password) {
        try {
            return client.loginUser(email, password);
        } catch (Exception e) {
            throw new RuntimeException("Error en loginUser: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void createDirectories(String[] paths, String token) {
        try {
            client.createDirectories(paths, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en createDirectories: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String[] uploadFiles(String[] paths, byte[][] data, String token) {
        try {
            return client.uploadFiles(paths, data, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en uploadFiles: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[][] downloadFiles(String[] paths, String token) {
        try {
            return client.downloadFiles(paths, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en downloadFiles: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void moveFiles(String[] oldPaths, String[] newPaths, String token) {
        try {
            client.moveFiles(oldPaths, newPaths, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en moveFiles: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteFiles(String[] paths, String token) {
        try {
            client.deleteFiles(paths, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en deleteFiles: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void shareFiles(String[] paths, String sharedWithEmail, String permission, String token) {
        try {
            client.shareFiles(paths, sharedWithEmail, permission, token);
        } catch (Exception e) {
            throw new RuntimeException("Error en shareFiles: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getSpaceUsage(String token) {
        try {
            return client.getSpaceUsage(token);
        } catch (Exception e) {
            throw new RuntimeException("Error en getSpaceUsage: " + e.getMessage(), e);
        }
    }
}