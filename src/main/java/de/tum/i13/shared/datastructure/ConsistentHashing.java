package de.tum.i13.shared.datastructure;

import de.tum.i13.shared.Hash;

import java.math.BigInteger;
import java.util.*;

/**
 * Represents a line (not a ring). Hashes are distributed on this line. We choose line instead of ring
 * since it leads to easier comparision of hashes used for the sorted map.
 */
public class ConsistentHashing {
    private static final BigInteger MAX_HASH = Hash.getMaxHash().md5Value;
    private static final BigInteger MIN_HASH = new BigInteger("0");
    public final SortedMap<Hash, ServerData> nodeMap = new TreeMap<>();
    private final NavigableSet<Hash> line = new TreeSet<>();
    
    /*return the server to be set writelock*/
    synchronized public ServerData addNode(ServerData s) {
        var serverHash = new Hash(s.getServerIp().getHostAddress() + ":" + s.getClientPort());
        ServerData nearestServer;
        if (nodeMap.isEmpty()) {
            line.add(serverHash);
            nodeMap.put(serverHash, s);
            s.setFirstHash(MIN_HASH);
            s.setLastHash(MAX_HASH);
            return null;
        }
        if (serverHash.compareTo(nodeMap.firstKey()) < 0) {
            s.setFirstHash(MIN_HASH);
            var nearestServerHash = line.ceiling(serverHash);
            nearestServer = nodeMap.get(nearestServerHash);
            s.setLastHash(Objects.requireNonNull(line.ceiling(serverHash)).sub(1).md5Value);
            //modify the range of the old server with least hash
            assert nearestServerHash != null;
            nearestServer.setFirstHash(nearestServerHash.md5Value);
        } else if (serverHash.compareTo(nodeMap.lastKey()) > 0) {
            s.setFirstHash(serverHash.md5Value);
            s.setLastHash(MAX_HASH);
            //modify the range of the old server with max hash
            var nearestServerHash = nodeMap.lastKey();
            nearestServer = nodeMap.get(nearestServerHash);
            nearestServer.setLastHash(serverHash.sub(1).md5Value);
        } else {
            s.setFirstHash(serverHash.md5Value);
            var nearestServerHash = line.floor(serverHash);
            nearestServer = nodeMap.get(nearestServerHash);
            s.setLastHash(nearestServer.getLastHash());
            //modify hash range old server
            nearestServer.setLastHash(serverHash.sub(1).md5Value);
        }
        line.add(serverHash);
        nodeMap.put(serverHash, s);
        return nearestServer;
    }
    
    //This function returns null if server to be removed is the only server left
    synchronized public ServerData removeNode(ServerData serverData) {
        if (serverData==null)
            return null;
        ServerData nearestServer;
        var serverHash =
                new Hash(serverData.getServerIp().getHostAddress() + ":" + serverData.getClientPort());
        if (!nodeMap.containsKey(serverHash)) {
            return null;
        }
        nodeMap.remove(serverHash);
        line.remove(serverHash);
        if (nodeMap.isEmpty()) {
            return null;
        } else if (serverHash.compareTo(nodeMap.firstKey())<0) {
            //serverHash is originallly the smallest node
            nearestServer = nodeMap.get(nodeMap.firstKey());
            nearestServer.setFirstHash(MIN_HASH);
        } else if (serverHash.compareTo(nodeMap.lastKey()) > 0) {
            //serverHash is originallly the largest node
            nearestServer = nodeMap.get(nodeMap.lastKey());
            nearestServer.setLastHash(MAX_HASH);
        } else {
            nearestServer = nodeMap.get(line.floor(serverHash));
            nearestServer.setLastHash(Objects.requireNonNull(line.ceiling(serverHash)).md5Value);
        }
        return nearestServer;
    }
    
    synchronized public int size() {
        assert nodeMap.size() == line.size();
        return nodeMap.size();
    }
    
    synchronized public Hash getNextServerHashFrom(Hash h) {
        return line.ceiling(h);
    }
    
    synchronized public Hash getFirstHash() {
        return line.first();
    }
    
    synchronized public Hash getLashHash() {
        return line.last();
    }
}
