package de.tum.i13.server.threadperconnection;

import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

class ConnectionThread implements Runnable {
    final KVServer kv;
    private final Socket clientSocket;
    
    public ConnectionThread(KVServer kv, Socket clientSocket) {
        this.kv = kv;
        this.clientSocket = clientSocket;
    }
    
    /**
     * distributor to appropriate handler
     */
    @Override
    public void run() {
        var logger = KVServer.logger;
        logger.info("One connection established: " + clientSocket.getRemoteSocketAddress());
        ActiveConnection ac;
        try {
            ac = new ActiveConnection(clientSocket); //clientSocket is not null so this should return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (kv.getServerData() == null) {
            logger.info("kv server data is null");
            return;
        }
        ac.writeln("Connection to Key-Value PUT/GET server established: " + kv.getServerData().getServerIp().toString() + ":" + kv.getServerData().getClientPort());
        String firstLine = ac.readLine();
        if (firstLine == null) {
            ac.close();
            logger.severe("The other server/client closed connection");
            return;
        }
        if (firstLine.startsWith("this is")) {
            //this is kv contacting for data transfer
            String[] parts = firstLine.split(" ");
            if (parts.length != 3) {
                logger.info("Unauthorised connection!");
                ac.writeln("Unauthorised!");
                ac.close();
            }
            var kvHandler = new KVKVConnectionHandler(kv, ac);
            kvHandler.handle(parts[2]);
        } else if (firstLine.startsWith("replica service")) {
            boolean isServerOnRing = false;
            List<ServerData> servers = kv.getServersMetaData().getServerData();
            for (ServerData s : servers) {
                if (s.getServerIp().getHostAddress().equals(ac.getAddress().getHostAddress()))
                    isServerOnRing = true;
            }
            if (isServerOnRing) {
                kv.kvRepService.handleReplicaRequest(ac);
            } else {
                logger.info("Unauthorised connection!");
                ac.writeln("Unauthorised!");
                ac.close();
            }
        } else {
            //this is client connection
            var clientHandler = new ClientKVConnectionHandler(this, firstLine, ac);
            clientHandler.handle();
        }
    }
}
