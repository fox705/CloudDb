package de.tum.i13.server.kv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    private final Database store = new Database(3, "FIFO", Path.of("generated/databaseTest"));
    private final Database storeLFU = new Database(3, "LFU", Path.of("generated/databaseTest"));
    private final Database storeLRU = new Database(3, "LRU", Path.of("generated/databaseTest"));
    private final Database repStore = new Database(0, "LRU", Path.of("generated/databaseTest"));
    
    @BeforeEach
    void prepare() {
        clean();
    }
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @AfterAll
    static void clean() {
        var dir = new File("generated/databaseTest");
        for (File file : Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory())
                file.delete();
    }
    
    @Test
    void testCacheAllStore() {
        try {
            standardTestOn(store);
            standardTestOn(storeLFU);
            standardTestOn(storeLRU);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        
    }
    
    //test if store on disk work as expected.
    @Test
    void actionOnDisk() {
        try {
            store.putToDisk("one", "1");
            assertEquals("1", store.getFromDisk("one"));
            store.putToDisk("million of things", "Please work");
            assertEquals("Please work", store.getFromDisk("million of things"));
            store.deleteFromDisk("one");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
            return;
        }
        assertThrows(FileNotFoundException.class, () -> store.getFromDisk("one"));
    }
    
    
    private void standardTestOn(Database store) throws Exception {
        store.put("apple", "pie");
        store.put("Maria Ozawa", "Bin Laden eats barbecue \n Obama comes to the rescue");
        store.put("SpecialChar@#Key", "I love you honey");
        store.put("One more to the four", "So my eye is less sore");
        
        assertEquals("pie", store.get("apple"));
        assertEquals("Bin Laden eats barbecue \n Obama comes to the rescue", store.get("Maria " +
                "Ozawa"));
        assertEquals("I love you honey", store.get("SpecialChar@#Key"));
        assertEquals("So my eye is less sore", store.get("One more to the four"));
        
        store.delete("apple");
        assertThrows(FileNotFoundException.class, () -> store.get("apple"));
        assertEquals(KeyStatus.Updated, store.put("Maria Ozawa", "Cyka Blyata"));
    }
    
    @Test
    void testReplicaFeature() {
        try {
            var s = new ArrayList<>();
            s.add("apple");
            s.add("something");
            repStore.put("apple", "pie");
            repStore.put("something", "else");
            repStore.putReplica("maria", "ozawa");
            // to Set, so order doesn't matter
            assertEquals(s.stream().collect(Collectors.toSet()), repStore.keySet().stream().collect(Collectors.toSet()));
            assertEquals("ozawa", repStore.get("maria"));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }
}
