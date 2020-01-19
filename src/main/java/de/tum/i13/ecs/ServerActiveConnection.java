package de.tum.i13.ecs;

import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;

import java.io.IOException;
import java.net.Socket;


class ServerActiveConnection extends ActiveConnection {
    /**
     * isTransferdone:  For 2 different ECSCommunicationThread to communicate about the state of the transfer
     */
    private volatile boolean isTransferDone = true;
    private volatile boolean isShutdownMessageSent = false;
    
    /**
     * The port server listens to to connected to the client
     */
    private int clientPort;
    
    ServerActiveConnection(Socket socket) throws IOException {
        super(socket);
    }
    
    boolean isTransferDone() {
        return isTransferDone;
    }
    
    void setTransferDone(boolean transferDone) {
        isTransferDone = transferDone;
    }
    
    boolean isShutdownMessageSent() {
        return isShutdownMessageSent;
    }
    
    void setShutdownMessageSent() {
        isShutdownMessageSent = true;
    }
    
    public int getClientPort() {
        return clientPort;
    }
    
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }
    
    public ServerData getServerData() {
        return new ServerData(socket.getInetAddress(), clientPort);
    }
}
