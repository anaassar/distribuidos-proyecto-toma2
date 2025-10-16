package com.ana;

import com.ana.soap.FileServiceImpl;
import jakarta.xml.ws.Endpoint;

public class ClientAppServer {
    public static void main(String[] args) {
    try {
        // Configurar el servidor HTTP para escuchar en todas las interfaces
        System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true");
        
        // En algunos entornos, necesitas esto:
        System.setProperty("com.sun.xml.ws.server.http.publish", "true");
        
        String url = "http://192.168.1.7:8080/dfs/fileservice";
        Endpoint.publish(url, new FileServiceImpl());
        
        System.out.println("✅ DFS Client Backend SOAP listo en: http://192.168.1.7:8080/dfs/fileservice");
        System.out.println("⏹️  Presiona Enter para detener...");
        System.in.read();
        
    } catch (Exception e) {
        e.printStackTrace();
    }
}
}