package de.tum.i13.client;

import de.tum.i13.ecs.ECSServer;
import de.tum.i13.server.threadperconnection.KVServer;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KVStoreTest {

    KVStore kvStore;
    ECSServer ecsServer;
    Server server;

    private class ECSThread extends Thread {
        ECSServer ecsServer;
        String parameters;

        public ECSThread(ECSServer ecsServer, String parameters) {
            this.ecsServer = ecsServer;
            this.parameters = parameters;
        }

        public void run() {
            try {
                ecsServer.run(parameters.split(" "));
            } catch (SocketException se) {
                System.out.println("ECS closed, test done.");
            } catch (IOException e) {
                e.printStackTrace();
                fail("ECSThread");
            }
        }
    }

    private class Server extends Thread {

        KVServer kvserver;
        String dir;

        public Server(String dir) {
            kvserver = new KVServer();
            this.dir = dir;
        }

        public void run() {
            String[] args = {"-l","generated/KVStoreTest/server.log","-ll", "SEVERE", "-d", dir, "-p", "8888", "-b", "127.0.0.1:7777"};
            try {
                kvserver.run(args);
            } catch (SocketException se) {
                System.out.println("Close Server");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void kill() {
            kvserver.setRunning(false);
        }

        public boolean checkIfRunning() {
            return kvserver.getRunning();
        }
    }

    @BeforeAll
    void setup() {
        ServerSet serverSet = new ServerSet();
        serverSet.addServer(new ServerData(InetAddress.getLoopbackAddress(), 888));
        kvStore = new KVStore();
        try {
            kvStore.setBaseServer("127.0.0.1", 8888);
        } catch (UnknownHostException e) {
            fail("wrong server");
        }
        ecsServer = new ECSServer();
        ECSThread ecsThread = new ECSThread(ecsServer, "-p 7777 -l generated/KVStoreTest/ecs.log");
        ecsThread.start();
        try {
            Thread.sleep(500);
            server = new Server("generated/KVStoreTest");
            server.start();
            while (!server.checkIfRunning())
                Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        boolean send = false;
        while (!send) {
            try {
                System.out.println("trying to send");
                kvStore.put("hell", "delete"); // to delete later and be sure server runs
                kvStore.put("helloo", "valueee");
                send = true;
            } catch (IOException | NoServerAliveException ne) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("send successfully");
    }

    @AfterAll
    void finish() {
        server.kill();
        ecsServer.setRunning(false);
        var dir = new File("generated/KVStoreTest");
        for (File file : Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory())
                file.delete();
    }

    @Test
    void put() {
        try {
            assertEquals("put_success hello", kvStore.put("hello", "world"));
        } catch (NoServerAliveException ne) {
            ne.printStackTrace();
            fail("no server online");
        } catch (IOException e) {
            e.printStackTrace();
            fail("put fail");
        }
    }

    @Test
    void delete() {
        try {
            assertEquals("delete_success hell", kvStore.delete("hell"));
        } catch (NoServerAliveException ne) {
            ne.printStackTrace();
            fail("no server online");
        } catch (IOException e) {
            e.printStackTrace();
            fail("put fail");
        }
    }

    @Test
    void get() {
        try {
            assertEquals("get_success helloo valueee", kvStore.get("helloo"));
        } catch (NoServerAliveException ne) {
            ne.printStackTrace();
            fail("no server online");
        } catch (IOException e) {
            e.printStackTrace();
            fail("put fail");
        }
    }

    @Test
    void bigData() {
        try {
            var value = new StringBuilder();
            var sc = new Scanner(new File("test_resources/files/Exactly_120_KByte"));
            value.append(sc.nextLine());
            while (sc.hasNextLine()) {
                value.append("\n" + sc.nextLine());
            }
            sc.close();
            String ret = kvStore.put("TheKey", value.toString());
            assertEquals("put_success TheKey", ret);
            ret = kvStore.get("TheKey");
            assertEquals("get_success TheKey " + value.toString(), ret);
        } catch (Exception e) {
            e.printStackTrace();
            fail("bigData fail");
        }
    }

}
