package de.tum.i13.shared.datastructure;

import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class represents a collection of ServerData and various related methods
 */
public class ServerSet implements Serializable {
    private List<ServerData> serverData;
    
    public ServerSet() {
        serverData = new ArrayList<>();
    }
    
    public void addServer(ServerData server) {
        this.serverData.add(server);
    }
    
    public ServerSet(ConsistentHashing h) {
        serverData = new ArrayList<>(h.nodeMap.values());
    }
    
    public List<ServerData> getServerData() {
        return serverData;
    }
    
    public void setServerData(List<ServerData> serverData) {
        this.serverData = serverData;
    }
    
    public ServerData getDataToThisServer (String ipAddress, int port){
        for (ServerData serverData : serverData) {
            if (serverData.getServerIp().getHostAddress().equals(ipAddress) && serverData.getClientPort() == port) {
                return serverData;
            }
        }
        return null;
    }
    
    public static ServerSet parseFromString(String s) throws IllegalArgumentException, UnknownHostException {
        String[] parts = s.split(";");
        ArrayList<ServerData> serverData = new ArrayList<>();
        for (String data : parts) {
            if (data.equals(""))
                continue; //Handle the case of one element
            serverData.add(ServerData.parseFromString(data));
        }
        ServerSet serversMetaData = new ServerSet();
        serversMetaData.setServerData(serverData);
        return serversMetaData;
    }
    
    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (ServerData s : serverData) {
            ret.append(s);
            ret.append(";");
        }
        return ret.toString();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serverData);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerSet that = (ServerSet) o;
        return serverData.equals(that.serverData);
    }
}
