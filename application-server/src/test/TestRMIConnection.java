import java.com.ana.node.StorageNode;
import java.rmi.Naming;

public class TestRMIConnection {
    public static void main(String[] args) {
        try {
            System.out.println(" Conectando a rmi://192.168.1.5:1099/node1");
            StorageNode node = (StorageNode) Naming.lookup("rmi://192.168.1.5:1099/node1");
            System.out.println(" Conexión exitosa!");
            System.out.println("ID: " + node.getNodeId());
            System.out.println("Espacio libre: " + node.getFreeSpace());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}