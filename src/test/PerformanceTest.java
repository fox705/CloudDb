import de.tum.i13.client.KVStore;
import de.tum.i13.client.NoServerAliveException;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * This class needs to be run after mvn package since it uses its artifact to test. It is an integration test
 * and can not be made into JUnit test. It uses Eron emails to test
 */
public class PerformanceTest {
    static Process ecsServer;
    static Process kv1;
    static Process kv2;
    static Process kv3;
    static KVStore client;
    static final File ecsLog = new File("ecs.log");
    static final File kv1Log = new File("kv1.log");
    static final File kv2Log = new File("kv2.log");
    static final File kv3Log = new File("kv3.log");
    
    public static void main(String[] args) {
        setUp();
        Runtime.getRuntime().addShutdownHook(new Thread(PerformanceTest::tearDown));
        testEronData();
    }
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void setUp() {
        try {
            var ecsLogError = new File("ecsError.log");
            ecsLogError.createNewFile();
            ecsLog.createNewFile();
            kv1Log.createNewFile();
            kv2Log.createNewFile();
            kv3Log.createNewFile();
            var ecsCommandString = "java -jar target/ecs-server.jar -l generated/de.tum.i13.ecs.PerformanceTest/ecs6.log -ll ALL -p 6003 -pp 55551".split(" ");
            ecsServer = (new ProcessBuilder(ecsCommandString)).
                    redirectOutput(ecsLog).redirectError(ecsLogError).start();
            kv1 = (new ProcessBuilder(("java -jar target/kv-server.jar -l generated/de.tum.i13.ecs.PerformanceTest/kvstore1.log -ll ALL -d generated/de.tum.i13.ecs.PerformanceTest/kvstore1/ -p 6000" +
                    " -b 127.0.0.1:6003 -c 10 -s " +
                    "LRU").split(" "))).redirectError(kv1Log).start();
            kv2 = (new ProcessBuilder(("java -jar target/kv-server.jar -l generated/de.tum.i13.ecs.PerformanceTest/kvstore2.log -ll ALL -d generated/de.tum.i13.ecs.PerformanceTest/kvstore2/ -p 6001" +
                    " -b 127.0.0.1:6003 -c 10 -s " +
                    "LRU").split(" "))).redirectError(kv2Log).start();
            kv3 = (new ProcessBuilder(("java -jar target/kv-server.jar -l generated/de.tum.i13.ecs.PerformanceTest/kvstore3.log -ll ALL -d generated/de.tum.i13.ecs.PerformanceTest/kvstore3/ -p 6002" +
                    " -b 127.0.0.1:6003 -c 10 -s " +
                    "LRU").split(" "))).redirectError(kv3Log).start();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        client = new KVStore();
        try {
            client.setBaseServer("127.0.0.1", 6000);
        } catch (UnknownHostException ignored) {
        }
    }
    
    static void tearDown() {
        ecsServer.destroy();
        kv1.destroy();
        kv2.destroy();
        kv3.destroy();
        try {
            System.out.println("Waiting 5 sec to destroy thread");
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }
        if (ecsServer.isAlive() || kv1.isAlive() || kv2.isAlive() || kv3.isAlive()) {
            kv1.destroyForcibly();
            System.out.println("We have to destroy processes forcibly");
        }
    }
    
    static void testEronData() {
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        var resourceFolder = new File("test_resources/eron_dataset");
        if (!resourceFolder.exists() || resourceFolder.isFile()) {
            fail("necessary resource not fould");
        }
        //Wait for server to initialize
        try {
            System.out.println("Sleeping for 5 sec");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        for (var file : Objects.requireNonNull(resourceFolder.listFiles())) {
            System.out.println("Transferring " + file.getName());
            try {
                String content = Files.readString(file.toPath());
                content = content.replaceAll("\n", "    ").replaceAll("\r", " ");
                var response = client.put(file.getName(), content);
                System.out.println(response);
                if (response == null)
                    continue;
                while (response.startsWith("server_write_lock")) {
                    System.out.println(response);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    response = client.put(file.getName(), content);
                }
            } catch (IOException e) {
                System.out.println("Put file failed");
            } catch (NoServerAliveException e) {
                System.out.println("ALL SERVER DOWN");
                break;
            }
        }
    }
}
