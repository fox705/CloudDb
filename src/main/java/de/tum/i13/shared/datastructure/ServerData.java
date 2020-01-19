package de.tum.i13.shared.datastructure;

import de.tum.i13.shared.Hash;

import java.io.Serializable;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;


public class ServerData implements Serializable {
    
    private final InetAddress serverIp;
    private final int clientPort;
    private int ecsPort = -1;
    private BigInteger firstHash;
    private BigInteger lastHash;

    public ServerData() {
        this.serverIp = InetAddress.getLoopbackAddress();
        this.clientPort = 69;
        this.firstHash = new BigInteger("0");
        this.lastHash = Hash.getMaxHash().md5Value;
    }
    
    public ServerData(InetAddress serverIp, int clientPort, int ecsPort) {
        this.serverIp = serverIp;
        this.clientPort = clientPort;
        this.ecsPort = ecsPort;
        this.firstHash = new BigInteger("0");
        this.lastHash = Hash.getMaxHash().md5Value;
    }
    
    public ServerData(InetAddress serverIp, int clientPort, BigInteger firstHash,
                      BigInteger lastHash) {
        this.serverIp = serverIp;
        this.clientPort = clientPort;
        this.firstHash = firstHash;
        this.lastHash = lastHash;
    }
    
    public ServerData(InetAddress serverIp, int clientPort) {
        this.serverIp = serverIp;
        this.clientPort = clientPort;
        this.firstHash = new BigInteger("0");
        this.lastHash = Hash.getMaxHash().md5Value;
    }

    public static ServerData parseFromString(String s) throws IllegalArgumentException, UnknownHostException {
        String[] parts = s.split(",");
        if (parts.length != 3)
            throw new IllegalArgumentException("wrong number of arguments: " + parts.length + "\n, should be 3.");
        else {
            String[] address = parts[2].split(":");
            if (address.length != 2)
                throw new IllegalArgumentException("wrong number of arguments: " + address.length + "\n, should be 2.");
            else {
                return new ServerData(InetAddress.getByName(address[0]), Integer.parseInt(address[1]), new BigInteger(parts[0]), new BigInteger(parts[1]));
            }
        }
    }

    public BigInteger getFirstHash() {
        return firstHash;
    }

    public void setFirstHash(BigInteger firstHash) {
        this.firstHash = firstHash;
    }

    public BigInteger getLastHash() {
        return lastHash;
    }

    public void setLastHash(BigInteger lastHash) {
        this.lastHash = lastHash;
    }

    public InetAddress getServerIp() {
        return serverIp;
    }
    
    public int getClientPort() {
        return clientPort;
    }
    
    
    public int getEcsPort() {
        return ecsPort;
    }
    
    @Override
    public String toString() {
        return firstHash + "," + lastHash + "," + serverIp.getHostAddress() + ":" + clientPort;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serverIp, clientPort);
    }
    
    //We need equal method for the purpose of the replication service
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerData)) return false;
        ServerData that = (ServerData) o;
        return clientPort == that.clientPort &&
                serverIp.equals(that.serverIp);
    }
    
    public boolean isResponsible(String key) {
        Hash hash = new Hash(key);
        var lowerBound = getFirstHash();
        var upperBound = getLastHash();
        return hash.md5Value.compareTo(lowerBound) >= 0 && hash.md5Value.compareTo(upperBound) <= 0;
    }
}
