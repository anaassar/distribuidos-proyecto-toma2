package com.ana;

import com.ana.soap.FileServiceImpl;
import jakarta.xml.ws.Endpoint;

public class ClientAppServer {
    public static void main(String[] args) {
        try {
            String url = "http://localhost:8080/dfs/fileservice";
            Endpoint.publish(url, new FileServiceImpl());
            
            System.out.println("✅ DFS Client Backend SOAP listo en: " + url);
            System.out.println("🔌 Conectado a Application Server en: http://localhost:8081");
            System.out.println("⏹️  Presiona Enter para detener...");
            System.in.read();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}