
***Comandos útiles***

mvn clean package

nodos---------------

mkdir -p /tmp/node1
java -Djava.rmi.server.hostname=192.168.1.5 -jar target/storage-node-1.0-SNAPSHOT.jar node1 /tmp/node1

java -Djava.rmi.server.hostname=192.168.1.5 -jar target/storage-node-1.0-SNAPSHOT.jar node2 /tmp/node2

java -Djava.rmi.server.hostname=192.168.1.5 -jar target/storage-node-1.0-SNAPSHOT.jar node3 /tmp/node3

java -Djava.rmi.server.hostname=192.168.1.5 -jar target/storage-node-1.0-SNAPSHOT.jar node1 /tmp/node1 1100

java -Djava.rmi.server.hostname=192.168.1.6 -jar target/storage-node-1.0-SNAPSHOT.jar node2 /tmp/node2 1100

java -Djava.rmi.server.hostname=192.168.1.7 -jar target/storage-node-1.0-SNAPSHOT.jar node2 /tmp/node3 1100

server---------------

java -jar target/application-server-1.0-SNAPSHOT.jar


cliente---------------

java -jar target/client-backend-1.0-SNAPSHOT.jar
