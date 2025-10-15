
***Comandos útiles***

mvn clean package

nodos---------------
mkdir -p /tmp/node1
java -Djava.rmi.server.hostname=192.168.1.10 -jar target/storage-node-1.0-SNAPSHOT.jar node1 /tmp/node1

server---------------
java -jar target/application-server-1.0-SNAPSHOT.jar


cliente---------------
java -jar target/client-backend-1.0-SNAPSHOT.jar
