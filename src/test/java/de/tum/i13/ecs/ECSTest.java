package de.tum.i13.ecs;

import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.TestParameter;
import de.tum.i13.shared.datastructure.ActiveConnection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ECSTest {

    @Test
    void addServerTest() {
        final ECSServer ecsServer = new ECSServer();
        var ecsThread = new Thread(() -> {
            try {
                ecsServer.run(TestParameter.ECS_CLIArgument.split(" "));
            } catch (SocketException se) {
                System.out.println("ECS closed, test done.");
            } catch (IOException e) {
                e.printStackTrace();
                fail("addServerTest");
            }
        });
        ecsThread.start();
        //This is a time based test so it might be flaky
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        try {
            var connection = new ActiveConnection(ecsServer.thisServer.getServerIp(),
                    ecsServer.thisServer.getClientPort());
            String res = connection.readLine();
            assertEquals("ECS recognized you, please connect to 55555 for pingService", res);
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Tried to connect to: " + ecsServer.thisServer.getServerIp().toString() + ":" + ecsServer.thisServer.getClientPort());
            fail("addServerTest");
        }
        ecsServer.setRunning(false); //essentially kill the thread
    }

    @Test
    void addMultipleServersTest() {
        final ECSServer ecsServer = new ECSServer();
        var ecsThread = new Thread(() -> {
            try {
                ecsServer.run(TestParameter.ECS_CLIArgument_TWO.split(" "));
            } catch (SocketException se) {
                System.out.println("ECS closed, test done.");
            } catch (IOException e) {
                e.printStackTrace();
                fail("addMultipleServersTest");
            }
        });
        ecsThread.start();
        //This is a time based test so it might be flaky
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        var server1 = getNewServer(ecsServer, 345);
        var server2 = getNewServer(ecsServer, 246);
        var server3 = getNewServer(ecsServer, 563);
        server1.start();
        server2.start();
        server3.start();
        try {
            server1.join();
            server2.join();
            server3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("addMultipleServersTest");
        }
        ecsServer.setRunning(false);
    }

    private Thread getNewServer(ECSServer ecsServer, int i) {
        return new Thread(() -> {
            try {
                var connection = new ActiveConnection(ecsServer.thisServer.getServerIp(),
                        ecsServer.thisServer.getClientPort());
                String res = connection.readLine();
                assertEquals("ECS recognized you, please connect to 55554 for pingService", res);
                connection.writeln(ECSProtocol.addServer(i));
                res = connection.readLine();
                assert res.startsWith("update") || res.startsWith("invoke");
            } catch (IOException e) {
                e.printStackTrace();
                fail("addMultipleServersTest");
            }
        });
    }
}
