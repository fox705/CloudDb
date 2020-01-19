package de.tum.i13.server.kv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;

class LRUCache extends Cache {
    
    private final LinkedList<String> keys = new LinkedList<>();
    
    LRUCache(int size, Database store) {
        super(size, store);
    }

    @Override
    String get(String key) throws FileNotFoundException {
        if (theCache.containsKey(key)) {
            keys.remove(key);
            keys.addFirst(key);
            return theCache.get(key);
        } else {
            //cache miss
            String valueFromDisk = disk.getFromDisk(key);
            while (theCache.size() >= size)
                resize();
            theCache.put(key, valueFromDisk);
            keys.addFirst(key);
            return valueFromDisk;
        }
    }
    
    @Override
    KeyStatus put(String key, String value) throws IOException {
        if (theCache.containsKey(key)) {
            keys.remove(key);
            keys.addFirst(key);
            theCache.put(key, value);
        } else {
            //cache miss
            while (theCache.size() >= size)
                resize();
            theCache.put(key, value);
            keys.addFirst(key);
        }
        return disk.putToDisk(key, value);
    }
    
    @Override
    boolean delete(String key) {
        theCache.remove(key);
        keys.remove(key);
        return disk.deleteFromDisk(key);
    }

    private void resize() {
        String keyToRemove = keys.removeLast();
        theCache.remove(keyToRemove);
    }
}
