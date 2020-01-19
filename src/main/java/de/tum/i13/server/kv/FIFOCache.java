package de.tum.i13.server.kv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class FIFOCache extends Cache {
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    
    public FIFOCache(int size, Database store) {
        super(size, store);
    }

    String get(String key) throws FileNotFoundException {
        if (theCache.containsKey(key)) {
            return theCache.get(key);
        }
        //cache miss
        String valueFromDisk = disk.getFromDisk(key);
        while (theCache.size() >= size)
            resize();
        queue.add(key);
        theCache.put(key, valueFromDisk);
        return valueFromDisk;
    }
    
    KeyStatus put(String key, String value) throws IOException {
        //noinspection IfStatementWithIdenticalBranches
        if (theCache.containsKey(key)) {
            theCache.put(key, value);
        } else {
            //cache miss
            while (theCache.size() >= size)
                resize();
            queue.add(key);
            theCache.put(key, value);
        }
        return disk.putToDisk(key, value);
    }
    
    @Override
    boolean delete(String key) {
        theCache.remove(key);
        queue.remove(key);
        return disk.deleteFromDisk(key);
        
    }

    private void resize() {
        //cache full
        var keyToRemove = queue.remove(); //guaranteed to return due to logic
        theCache.remove(keyToRemove);
    }
}
