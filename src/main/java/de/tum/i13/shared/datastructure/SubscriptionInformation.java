package de.tum.i13.shared.datastructure;

import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class SubscriptionInformation {
    private String password;
    private InetAddress ipAddress;
    private int port;
    private Set<String> keys;
    private boolean online;

    public SubscriptionInformation(String password, InetAddress ipAddress, int port, Set<String> keys, boolean online) {
        this.password = password;
        this.ipAddress = ipAddress;
        this.port = port;
        this.keys = keys;
        this.online = online;
    }

    public SubscriptionInformation(String password, InetAddress ipAddress, int port, String key, boolean online) {
        this.password = password;
        this.ipAddress = ipAddress;
        this.port = port;
        this.keys = new HashSet<>();
        this.keys.add(key);
        this.online = online;
    }

    public SubscriptionInformation() {}

    public synchronized void setPassword(String password) {
        this.password = password;
    }

    public synchronized void setKeys(Set<String> keys) {
        this.keys = keys;
    }

    public synchronized void setOnline(boolean online) {
        this.online = online;
    }

    public synchronized String getPassword() {
        return password;
    }

    public synchronized InetAddress getIpAddress() {
        return ipAddress;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public synchronized void setPort(int port) {
        this.port = port;
    }

    public synchronized Set<String> getKeys() {
        return keys;
    }

    public synchronized boolean isOnline() {
        return online;
    }

    public synchronized void addKey(String key) {
        this.keys.add(key);
    }

    public synchronized void removeKey(String key) {
        this.keys.remove(key);
    }

    public synchronized boolean containsKey(String key) {
        return this.keys.contains(key);
    }
}
