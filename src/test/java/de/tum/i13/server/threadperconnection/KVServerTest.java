package de.tum.i13.server.threadperconnection;

import de.tum.i13.ecs.ECSServer;
import de.tum.i13.shared.ConnectionBuilder;
import de.tum.i13.shared.Hash;
import de.tum.i13.shared.TestParameter;
import de.tum.i13.shared.datastructure.ActiveConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class KVServerTest {
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeAll
    static void clean() {
        var dir = new File("generated/multi/");
        if (dir.listFiles() != null) {
            for (File file : Objects.requireNonNull(dir.listFiles()))
                if (!file.isDirectory())
                    file.delete();
        }
        dir = new File("generated/shutdown/");
        if (dir.listFiles() != null) {
            for (File file : Objects.requireNonNull(dir.listFiles()))
                if (!file.isDirectory())
                    file.delete();
        }
        dir = new File("generated/transfer/");
        if (dir.listFiles() != null) {
            for (File file : Objects.requireNonNull(dir.listFiles()))
                if (!file.isDirectory())
                    file.delete();
        }
        dir = new File("generated/transfer2/");
        if (dir.listFiles() != null) {
            for (File file : Objects.requireNonNull(dir.listFiles()))
                if (!file.isDirectory())
                    file.delete();
        }
    }

    @Test
    void ShutDownAndStartUpTest() {
        try {
            final ECSServer ecsServer = new ECSServer();
            var ecsThread = new ECSThread(ecsServer, TestParameter.ECS_CLIArgument_THREE);
            ecsThread.start();
            try {
                //wait for ECS to start
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Server server = new Server("generated/shutdown", 5150, "generated/KVServerDataTest" +
                    "/shutdown1" +
                    ".log", "127.0.0" +
                    ".1:5159");
            server.start();
            while (server.checkIfRunning()) {
                if (!server.isAlive()) {
                    fail("server failed initialization");
                }
                Thread.sleep(100); //to wait for server to be able to answer
            }
            ConnectionBuilder connectionBuilder = new ConnectionBuilder("127.0.0.1", 5150);
            ActiveConnection activeConnection = connectionBuilder.connect();
            assertEquals("Connection to Key-Value PUT/GET server established: /127.0.0.1:5150",
                    activeConnection.readLine());
            activeConnection.writeln("put key value");
            var ret = activeConnection.readLine();
            // to wait for server to finish balancing phase. Otherwise server would just reply server_stopped
            while (ret.equals("server_stopped")) {
                Thread.sleep(100);
                activeConnection.writeln("put key value");
                ret = activeConnection.readLine();
            }
            //on Windows there might be fight left over from the previous test due to file delete
            // permission
            assertTrue(ret.equals("put_success key") || ret.equals("put_update key"));
            activeConnection.writeln("put key newValue");
            assertEquals("put_update key", activeConnection.readLine());
            server.kill();
            Thread.sleep(500); // wait for server to shut down
            server = new Server("generated/shutdown", 5156, "generated/KVServerDataTest/shutdown2" +
                    ".log", "127.0.0.1:5159");
            server.start();
            while (server.checkIfRunning()) {
                if (!server.isAlive()) {
                    fail("server failed initialization");
                }
                Thread.sleep(100); //to wait for server to be able to answer
            }
            connectionBuilder = new ConnectionBuilder("127.0.0.1", 5156);
            activeConnection = connectionBuilder.connect();
            assertEquals("Connection to Key-Value PUT/GET server established: /127.0.0.1:5156",
                    activeConnection.readLine());
            activeConnection.writeln("get key");
            ret = activeConnection.readLine();
            // to wait for server to finish balancing phase. Otherwise server would just reply server_stopped
            while (ret.equals("server_stopped")) {
                Thread.sleep(100);
                activeConnection.writeln("get key");
                ret = activeConnection.readLine();
            }
            assertEquals("get_success key newValue", ret);
            server.kill();
            ecsServer.setRunning(false);
        } catch (Exception e) {
            e.printStackTrace();
            fail("ShutDownAndStartUpTest");
        }
    }

    @Test
    void MultipleClients() {
        try {
            final ECSServer ecsServer = new ECSServer();
            var ecsThread = new ECSThread(ecsServer, TestParameter.ECS_CLIArgument_FOUR);
            ecsThread.start();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Server server = new Server("generated/multi", 5155, "generated/KVServerDataTest/multi" +
                    ".log", "127" +
                    ".0.0.1:5160");
            server.start();
            while (server.checkIfRunning())
                Thread.sleep(100);
            Thread.sleep(500); // wait to finish balancing phase
            Client client1 = new Client();
            Client client2 = new Client();
            Client client3 = new Client();
            client1.start();
            client2.start();
            client3.start();
            Thread.sleep(2000);
            client1.kill();
            client2.kill();
            client3.kill();
            server.kill();
            ecsServer.setRunning(false);
            assert !client1.failed && !client2.failed && !client3.failed;
        } catch (Exception e) {
            e.printStackTrace();
            fail("MultipleClients");
        }
    }

    @Test
    void KVServerTransfer() {
        try {
            final ECSServer ecsServer = new ECSServer();
            var ecsThread = new ECSThread(ecsServer, TestParameter.ECS_CLIArgument_FIVE);
            ecsThread.start();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Server server = new Server("generated/transfer", 5180, "generated/KVServerDataTest" +
                    "/transfer" +
                    ".log", "127.0.0" +
                    ".1:5170");
            server.start();
            while (server.checkIfRunning())
                Thread.sleep(100); //to wait for server to be able to answer
            ConnectionBuilder connectionBuilder = new ConnectionBuilder("127.0.0.1", 5180);
            ActiveConnection activeConnection = connectionBuilder.connect();
            assertEquals("Connection to Key-Value PUT/GET server established: /127.0.0.1:5180",
                    activeConnection.readLine());
            activeConnection.writeln("put key some value to be stored");
            var ret = activeConnection.readLine();
            // to wait for server to finish balancing phase. Otherwise server would just reply server_stopped
            while (ret.equals("server_stopped")) {
                Thread.sleep(100);
                activeConnection.writeln("put key some value to be stored");
                ret = activeConnection.readLine();
            }
            assertEquals("put_success key", ret); //because hash of "key" is in his range
            activeConnection.close();
            Server server2 = new Server("generated/transfer2", 5181, "generated/KVServerDataTest" +
                    "/transfer2.log",
                    "127.0.0.1:5170");
            server2.start();
            while (server2.checkIfRunning())
                Thread.sleep(100);
            connectionBuilder = new ConnectionBuilder("127.0.0.1", 5181);
            activeConnection = connectionBuilder.connect();
            assertEquals("Connection to Key-Value PUT/GET server established: /127.0.0.1:5181",
                    activeConnection.readLine());
            activeConnection.writeln("get key");
            ret = activeConnection.readLine();
            // to wait for server to finish balancing phase. Otherwise server would just reply server_stopped
            while (ret.equals("server_stopped")) {
                Thread.sleep(100);
                activeConnection.writeln("get key");
                ret = activeConnection.readLine();
            }
            //Two servers online
            assertEquals("server_not_responsible", ret); //because hash of "key" is never in his range
            server.kill();
            Thread.sleep(500); // wait for server to shut down and transfer data
            activeConnection.writeln("get key");
            //One server online
            assertEquals("get_success key some value to be stored", activeConnection.readLine());
            server2.kill();
            ecsServer.setRunning(false);
        } catch (Exception e) {
            e.printStackTrace();
            fail("KVServerTransfer");
        }
    }
    
    private static class ECSThread extends Thread {
        final ECSServer ecsServer;
        final String paramters;
        
        public ECSThread(ECSServer ecsServer, String parameters) {
            this.ecsServer = ecsServer;
            this.paramters = parameters;
        }
        
        public void run() {
            try {
                ecsServer.run(paramters.split(" "));
            } catch (SocketException se) {
                System.out.println("ECS closed, test done.");
            } catch (IOException e) {
                e.printStackTrace();
                fail("ECSThread");
            }
        }
    }
    
    private static class Server extends Thread {
        
        final KVServer kvserver;
        final String dir;
        final int port;
        final String log;
        final String boot;
        
        public Server(String dir, int port, String log, String boot) {
            kvserver = new KVServer();
            this.dir = dir;
            this.port = port;
            this.log = log;
            this.boot = boot;
        }
        
        public void run() {
            String[] args = {"-l", log, "-ll", "SEVERE", "-d", dir, "-p", Integer.toString(port), "-b", boot};
            try {
                kvserver.run(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        public void kill() {
            kvserver.ecsCommunicationThread.remove();
            try {
                kvserver.ecsCommunicationThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        public boolean checkIfRunning() {
            return !kvserver.getRunning();
        }
    }
    
    private static class Client extends Thread {
        
        final String[] keys = {"TheKey", "key", "someKey"};
        final String[] values = {"TheValue", "value", "someValue"};
        ActiveConnection activeConnection;
        boolean running;
        boolean failed;
        
        public Client() {
            failed = false;
            try {
                ConnectionBuilder connectionBuilder = new ConnectionBuilder("127.0.0.1", 5155);
                activeConnection = connectionBuilder.connect();
                String confirmation = activeConnection.readLine();
                failed = !confirmation.equals("Connection to Key-Value PUT/GET server established: /127.0.0.1:5155");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        public void run() {
            running = true;
            while (running) {
                int key = (int) (Math.random() * 3);
                try {
                    String ret = put(keys[key]);
                    if (!(ret.contains("put_update") || ret.contains("put_success"))) {
                        failed = true;
                    }
                    Thread.sleep((int) (Math.random() * 100));
                    ret = get(keys[key]);
                    if (!ret.contains("get_success")) {
                        failed = true;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        private String put(String key) {
            activeConnection.writeln("put " + key + " " + values[(int) (Math.random() * 3)]);
            return activeConnection.readLine();
        }
        
        private String get(String key) {
            activeConnection.writeln("get " + key);
            return activeConnection.readLine();
        }
        
        public void kill() {
            running = false;
        }
        
    }
}
