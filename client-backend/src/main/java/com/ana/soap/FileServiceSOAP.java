package com.ana.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface FileServiceSOAP {

    @WebMethod String registerUser(String username, String email, String password);
    @WebMethod String loginUser(String email, String password);
    
    @WebMethod void createDirectories(String[] paths, String token);
    @WebMethod String[] uploadFiles(String[] paths, byte[][] data, String token);
    @WebMethod byte[][] downloadFiles(String[] paths, String token);
    @WebMethod void moveFiles(String[] oldPaths, String[] newPaths, String token);
    @WebMethod void deleteFiles(String[] paths, String token);
    @WebMethod void shareFiles(String[] paths, String sharedWithEmail, String permission, String token);
    
    @WebMethod String getSpaceUsage(String token);
}