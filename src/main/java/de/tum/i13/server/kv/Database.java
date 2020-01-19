package de.tum.i13.server.kv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Used to decide whether to send put_update or not
 */
enum KeyStatus {
    Updated, Created
}

public class Database {
    private final File storageFile;
    private final Cache cache;
    private final String dataPath;
    private final Collection<String> keysReplicated = new CopyOnWriteArrayList<>();
    
    public Database(int cacheSize, String strategy, Path datadir) {
        if (strategy == null)
            strategy = "FIFO";
        if (cacheSize == 0)
            cacheSize = 100;
        if (datadir == null)
            datadir = Path.of("data/");
        switch (strategy) {
            case "LFU":
                cache = new LFUCache(cacheSize, this);
                break;
            case "LRU":
                cache = new LRUCache(cacheSize, this);
                break;
            default:
                cache = new FIFOCache(cacheSize, this);
        }
        try {
            Files.createDirectories(Path.of(datadir.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        storageFile = new File(datadir.toString());
        dataPath = datadir.toString();
    }

    /**
     * delete key value pair from disk
     * @param key
     * @return
     */
    synchronized boolean deleteFromDisk(String key) {
        var f = new File( dataPath + "/" + key + ".txt");
        return f.delete();
    }

    /**
     * get value to key from the disk
     * @param key
     * @return
     * @throws FileNotFoundException
     */
    synchronized String getFromDisk(String key) throws FileNotFoundException {
        var value = new StringBuilder();
        Scanner sc;
        if (keysReplicated.contains(key)) {
            sc = new Scanner(new File(dataPath + "/" + key + ".txt.replica"));
        } else {
            sc = new Scanner(new File(dataPath + "/" + key + ".txt"));
        }
        value.append(sc.nextLine());
        while (sc.hasNextLine()) {
            value.append("\n").append(sc.nextLine());
        }
        sc.close();
        return value.toString();
    }

    /**
     * put key value pair
     *
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    synchronized public KeyStatus put(String key, String value) throws IOException {
        return cache.put(key, value);
    }
    
    /**
     * get value to key
     *
     * @param key
     * @return
     * @throws FileNotFoundException
     */
    synchronized public String get(String key) throws FileNotFoundException {
        return cache.get(key);
    }
    
    /**
     * delete key, value pair
     *
     * @param key
     * @return
     */
    synchronized public boolean delete(String key) {
        return cache.delete(key);
    }

    /**
     * put key, value pair to disk
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    synchronized KeyStatus putToDisk(String key, String value) throws IOException {
        var keyFile = new File(dataPath + "/" + key + ".txt");
        var isFileNotExist = keyFile.createNewFile();
        var fileWriter = new FileWriter(keyFile);
        fileWriter.write(value);
        fileWriter.flush();
        fileWriter.close();
        return isFileNotExist ? KeyStatus.Created : KeyStatus.Updated;
    }
    
    /**
     * Return all our actual key on the disk
     *
     * @return
     */
    synchronized public List<String> keySet() {
        //we need to remove file extension as we want only key name
        return Stream.of(Objects.requireNonNull(storageFile.listFiles())).filter(file -> file.getName().endsWith(".txt")).map(file ->
                file.getName().substring(0, file.getName().length() - 4)).collect(toList());
    }
    
    /*
     * These 2 replica methods are almost identical to the putToDisk and deleteFromDisk method, with different form of files suffix.
     * I just dont want to change those 2 crucial methods
     */
    synchronized public void putReplica(String key, String value) throws IOException {
        var keyFile = new File(dataPath + "/" + key + ".txt.replica");
        var fileWriter = new FileWriter(keyFile);
        fileWriter.write(value);
        fileWriter.flush();
        fileWriter.close();
        keysReplicated.add(key);
    }
    
    @SuppressWarnings("unused")
    synchronized public void clearAllReplica() {
        for (var key : keysReplicated) {
            deleteReplica(key);
        }
        keysReplicated.clear();
    }
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    synchronized public void deleteReplica(String key) {
        var f = new File(dataPath + "/" + key + ".txt.replica");
        f.delete();
        keysReplicated.remove(key);
    }
}





