package de.tum.i13.server.kv;

import de.tum.i13.server.threadperconnection.ECSCommunicationThread;
import de.tum.i13.server.threadperconnection.KVServer;
import de.tum.i13.shared.datastructure.ServerData;
import org.junit.jupiter.api.Test;
import org.mockito.configuration.IMockitoConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KVCommandProcessorTest {
// --Commented out by Inspection START (25. Nov.. 2019 07:29):
//    KVCommandProcessor kv = new KVCommandProcessor(new Database(100, "LRU", Path.of("data/")),
//            new KVServer());
// --Commented out by Inspection STOP (25. Nov.. 2019 07:29)
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void process() {
        var kvServer = new KVServer();
        KVCommandProcessor kv = new KVCommandProcessor(new Database(100, "LRU",
                Path.of("generated/KVCommandProcessorTest")), kvServer, new ECSCommunicationThread(kvServer));
        kvServer.setServerData(new ServerData());
        //should work, because keyrange is from in to max
        System.out.println("lower: " + kvServer.getServerData().getFirstHash());
        var dir = new File("generated/KVCommandProcessorTest");
        if (dir.listFiles() != null) {
            for (File file : Objects.requireNonNull(dir.listFiles()))
                if (!file.isDirectory())
                    file.delete();
        }
        assertEquals("put_success hello", kv.process("put hello world"));
        assertEquals("put_update hello", kv.process("put hello mom"));
        assertEquals("get_success hello mom", kv.process("get hello"));
        assertEquals("get_error halfman", kv.process("get halfman"));
        assertEquals("delete_success hello", kv.process("delete hello"));
        assertEquals("get_error hello", kv.process("get hello"));
        assertEquals("delete_error fullman", kv.process("delete fullman"));
    }
}
