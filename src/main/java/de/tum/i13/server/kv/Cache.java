package de.tum.i13.server.kv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class Cache {
    final int size;
    final Map<String, String> theCache;
    final Database disk;
    
    Cache(int size, Database disk) {
        this.size = size;
        this.theCache = new ConcurrentHashMap<>(size); //automatically threadsafe
        this.disk = disk;
    }

    /**
     * get value to key, if not in cache it will be loaded from disk and replaced in cache
     * @param key
     * @return
     * @throws FileNotFoundException
     */
    abstract String get(String key) throws FileNotFoundException;

    /**
     * put key and value pair to cache
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    abstract KeyStatus put(String key, String value) throws IOException;

    /**
     * delete key, value pair from cache
     * @param key
     * @return
     */
    abstract boolean delete(String key);
}