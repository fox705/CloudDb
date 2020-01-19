package de.tum.i13.server.kv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

class Node {
    final String key;
    final int frequency;

    public Node(String key, int frequency) {
        this.key = key;
        this.frequency = frequency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    // 2 Node are equals if key is equal
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return key.equals(node.key);
    }
}

class LFUCache extends Cache {
    
    private final PriorityBlockingQueue<Node> frequencyHeap = new PriorityBlockingQueue<>(size,
            Comparator.comparingInt(node -> node.frequency));
    private final Map<String, Integer> frequencyDict = new ConcurrentHashMap<>(size); //used for
    // fast look up
    // frequency
    
    LFUCache(int size, Database disk) {
        super(size, disk);
    }

    @Override
    String get(String key) throws FileNotFoundException {
        if (theCache.containsKey(key)) {
            //increase frequency
            int oldFrequency = frequencyDict.get(key);
            //reconstruct the tree
            //noinspection SuspiciousMethodCalls
            frequencyHeap.remove(key); //O(log n)
            frequencyHeap.add(new Node(key, oldFrequency + 1)); //O(log n)
            frequencyDict.replace(key, oldFrequency + 1);
            return theCache.get(key);
        } else {
            //cache miss
            String valueFromDisk = disk.getFromDisk(key);
            while (theCache.size() >= size)
                resize();
            theCache.put(key, valueFromDisk);
            frequencyHeap.add(new Node(key, 1));
            frequencyDict.put(key, 1);
            return valueFromDisk;
        }
    }
    
    @Override
    KeyStatus put(String key, String value) throws IOException {
        if (theCache.containsKey(key)) {
            theCache.put(key, value);
            //increase frequency
            int oldFrequency = frequencyDict.get(key);
            //reconstruct the tree
            //noinspection SuspiciousMethodCalls
            frequencyHeap.remove(key); //O(log n)
            frequencyHeap.add(new Node(key, oldFrequency + 1)); //O(log n)
            frequencyDict.replace(key, oldFrequency + 1);
        } else {
            //cache miss
            while (theCache.size() >= size)
                resize();
            theCache.put(key, value);
            frequencyHeap.add(new Node(key, 1));
            frequencyDict.put(key, 1);
        }
        return disk.putToDisk(key, value);
    }
    
    @Override
    boolean delete(String key) {
        theCache.remove(key);
        frequencyHeap.remove(new Node(key, 1));
        frequencyDict.remove(key);
        return disk.deleteFromDisk(key);
    }

    private void resize() {
        var keyToRemove = frequencyHeap.remove().key;
        frequencyDict.remove(keyToRemove);
        theCache.remove(keyToRemove);
    }
}
