package de.tum.i13.server.threadperconnection;

import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;
import de.tum.i13.shared.datastructure.SubscriptionInformation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/*
 * We use logger.info instead of log in this class Log does not work with shutdownhook,
 * sadly
 * There is no work around
 */

public class ECSCommunicationThread extends Thread {

    private final KVServer kvServer;
    // of other server shutting down or because of me connecting to ECS
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public ECSCommunicationThread(KVServer kvServer) {
        this.kvServer = kvServer;
    }

    @Override
    public void run() {
        try {
            var connection = kvServer.ecsConnection;
            logger.info("Connecting to ECS: " + kvServer.ecsServer.getAddress().toString() + ":" + kvServer.ecsServer.getPort());
            var kvIpAddress = kvServer.getServerData().getServerIp().getHostAddress();
            var kvPort = kvServer.getServerData().getClientPort();

            //If KV receives any unexpected input, simply move to the next message
            while (kvServer.getRunning()) {
                //TODO handle ECS subscription error, success, etc messages and forward to client
                //therefore we need all active connections, and the ipAddress and port of subscriber must be included in message from ecs
                String ret = connection.readLine();
                logger.info("received: " + ret);
                //update metadata logic
                if (ret.equals("shutdown")) {
                    kvServer.setRunning(false);
                } else if (ret.startsWith("ECS recognized you")) {
                    connection.writeln(ECSProtocol.addServer(kvPort));
                    logger.info("sent back 'add' to the ECS");
                } else if (ret.equals("try again")) {
                    connection.writeln(ECSProtocol.addServer(kvPort));
                    logger.info("sent back 'add' to the ECS");
                } else if (ret.startsWith("update")) {
                    String[] parts = ret.split(" ");
                    assert parts.length == 2;
                    try {
                        synchronized (kvServer) {
                            kvServer.setServersMetaData(ServerSet.parseFromString(parts[1]));
                            if (kvServer.getServersMetaData().getDataToThisServer(kvIpAddress, kvPort) == null) {
                                logger.info("Server data async");
                                /*
                                This server is probably waiting for invoke message from ECS, but another server connected to ECS first
                                and immediately added and its data broadcasted, that's why this async happens
                                
                                The solution is to ignore this update and continue to wait
                                 */
                                kvServer.setServersMetaData(null);
                                continue;
                            }
                            kvServer.setServerData(kvServer.getServersMetaData().getDataToThisServer(kvIpAddress, kvPort));
                            logger.info("Releasing KVServer lock");
                        }
                        logger.info("update metadata successfully");
                    } catch (IllegalArgumentException | UnknownHostException e) {
                        logger.info("Exception while parsing new MetaData: \n" + e.getMessage() + Arrays.toString(e.getStackTrace()));
                    }
                } else if (ret.startsWith("invoke receive from")) {
                    String[] parts = ret.split(" from ");
                    assert parts.length == 2;
                    receiveFrom(connection, ServerData.parseFromString(parts[1]));
                } else if (ret.contains("invoke transfer to")) {
                    String[] parts = ret.split(" to ");
                    assert parts.length == 2;
                    transferTo(ServerData.parseFromString(parts[1]));
                } else if (ret.startsWith("replica update")) {
                    var matcher = Pattern.compile("replica update (.*)").matcher(ret);
                    var isMatched = matcher.matches();
                    assert isMatched;
                    try {
                        var old = kvServer.getReplicaData();
                        kvServer.setReplicaData(ServerSet.parseFromString(matcher.group(1)));
                        // if the new replicaData is the same as the old, we don't have to do anything
                        kvServer.kvRepService.setNew(old == null || !old.equals(kvServer.getReplicaData()));
                        logger.info("update replica metadata successfully");
                    } catch (IllegalArgumentException | UnknownHostException e) {
                        logger.info("Exception while parsing new replica MetaData: \n" + e.getMessage() + Arrays.toString(e.getStackTrace()));
                    }
                } else if (ret.startsWith("update subscriptions")) {
                    String[] parts = ret.split(" subscriptions ", 2);
                    handleSubscriptionUpdate(parts[1]);
                } else {
                    throw new RuntimeException("Unrecognized message from ECS");
                }
            }
            logger.info("KVServerRunning is set to false, killing the ECSConnection...");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            kvServer.ecsConnection.close();
        }
    }

    /**
     * @param ecsConnection connection to the ECS server
     * @param serverData    serverData of the transfering server
     */
    private synchronized void receiveFrom(ActiveConnection ecsConnection, ServerData serverData) {
        ActiveConnection connection; //connection to the other KV-Server
        try {
            connection = new ActiveConnection(serverData);
        } catch (IOException e) {
            logger.info("Unable to establish a connection to the other server, trying again");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            try {
                connection = new ActiveConnection(serverData);
            } catch (IOException ex) {
                logger.info("Second try failed, abort receive data");
                return;
            }
        }
        connection.readLine();
        String ret;
        connection.writeln("this is " + kvServer.getServerData().getServerIp().getHostAddress()
                + ":" + kvServer.getServerData().getClientPort());
        while ((ret = connection.readLine()) != null) {
            logger.info("received: " + ret);
            if (ret.equals("confirm end transfer")) {
                logger.info("transfer success");
                //Write to the other server so they can close the socket
                connection.writeln("confirm end transfer");
                //do not close this socket prematurely, keep it alive until the other end closes
                continue;
            }
            String[] put = ret.split(" ");
            if (put.length > 2 && put[0].equals("put")) {
                try {
                    var valueString = String.join(" ",
                            Stream.of(put).skip(2).toArray(String[]::new));
                    kvServer.getDatabase().put(put[1], valueString);
                } catch (IOException e) {
                    logger.info("Unable to put the value: " + ret);
                }
            } else {
                logger.info("received wrong put format: " + ret);
            }
        }
        logger.info("Finished receiving data protocol");
        connection.close();

        logger.info("sending confirm transfer to the ecs");
        ecsConnection.writeln("confirm transfer");//message to the ECS,
        logger.info("receive data executed");
    }

    /**
     * @param serverData serverData of the receiving server
     */
    private synchronized void transferTo(ServerData serverData) {
        //on ECS, we ensure that there is only one transfer at the same time so no need to worry about this
        logger.info("Begin transfer data process to server " + serverData);
        kvServer.transferQueue.drainTo(new ArrayList<>()); //drain all elements to prepare for new one
        kvServer.transferQueue.add(serverData); //KVKVConnectionHandler class will handle next
    }

    public synchronized void remove() {
        kvServer.ecsConnection.writeln(ECSProtocol.removeServer(kvServer.getServerData().getClientPort()));
    }

    private void handleSubscriptionUpdate(String command) {
        String[] commandArr = command.split(" ");
        logger.info("processing subscribe request");
        SubscriptionInformation info = kvServer.getInfo(commandArr[1]);
        InetAddress ip = InetAddress.getLoopbackAddress();
        if ((commandArr[0].equals("register") || commandArr[0].equals("update")) && commandArr.length == 4) {
            try {
                ip = InetAddress.getByName(commandArr[2]);
            } catch (UnknownHostException e) {
                logger.info("malformed update: " + command);
            }
        }
        switch (commandArr[0]) {
            case "login":
                info.setOnline(true);
                break;
            case "logout":
                info.setOnline(false);
                break;
            case "register":
                if (commandArr.length == 4)
                    kvServer.addSubscription(commandArr[1], new SubscriptionInformation(null, ip, Integer.parseInt(commandArr[3]), new HashSet<>(), true));
                break;
            case "update":
                if (commandArr.length == 4) {
                    info.setIpAddress(ip);
                    info.setPort(Integer.parseInt(commandArr[6]));
                    info.setOnline(true);
                }
                break;
            case "subscribe":
                if (commandArr.length == 3)
                    info.addKey(commandArr[2]);
                break;
            case "unsubscribe":
                if (commandArr.length == 3)
                    info.removeKey(commandArr[2]);

                break;
            default:
                logger.severe(String.format("Malformed message from server: %s", command));
                break;
        }
    }

    public synchronized void forwardToECS(String line) {
        kvServer.ecsConnection.writeln(line + "\n");
    }
}
