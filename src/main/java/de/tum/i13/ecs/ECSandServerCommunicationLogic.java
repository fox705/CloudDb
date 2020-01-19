package de.tum.i13.ecs;

import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.datastructure.ConsistentHashing;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;
import de.tum.i13.shared.datastructure.SubscriptionInformation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

class ECSandServerCommunicationLogic extends Thread {
    private static final Logger logger =
            Logger.getLogger(ECSandServerCommunicationLogic.class.getName());
    private final ServerActiveConnection ecsConnection;
    private final ConsistentHashing hashing;
    private final List<ServerActiveConnection> ecsActiveConnections;
    private final PingService pingService;
    private final ECSServer ecs;

    public ECSandServerCommunicationLogic(ECSServer ecs, ServerActiveConnection ecsConnection, PingService pingService) {
        this.ecsConnection = ecsConnection;
        this.hashing = ecs.hashing;
        this.ecsActiveConnections = ecs.activeConnections;
        this.pingService = pingService;
        this.ecs = ecs;
    }

    @Override
    public void run() {
        InetAddress hostAddress = ecsConnection.getAddress();
        int port; //will be replaced later in the add command
        ecsConnection.writeln("ECS recognized you");
        ServerData thisServer = null;
        String currentLine = ecsConnection.readLine();
        boolean isAddSent = false;
        while (currentLine != null) {
            // because of readLine() key or value may never contain \r\n
            logger.info(String.format("message from %s received: %s",
                    ecsConnection.socket.getRemoteSocketAddress(), currentLine));
            //process the message from server
            var commandArr = currentLine.split(" ");
            if (currentLine.startsWith("add") && !isAddSent) {
                synchronized (hashing) {
                    //I want to make sure that there is only one add/remove at a time
                    //The situation when server A is transfering, and at the same time receiving, might be possible, but potentially buggy
                    port = Integer.parseInt(commandArr[1]);
                    ecsConnection.setClientPort(port);
                    thisServer = new ServerData(hostAddress, port,
                            ecsConnection.socket.getPort());
                    var changedServer = hashing.addNode(thisServer);
                    pingService.addConnection(ecsConnection);

                    if (changedServer != null) {
                        logger.info(String.format("The new server is: %s", thisServer));
                        logger.info(String.format("The changed server is: %s", changedServer));
                        if (invokeDataTransfer(changedServer, thisServer, false)) {
                            //transfer successfully
                            notifyDataChange();
                        } else {
                            //Transfer failed, revoke every changes so far
                            hashing.removeNode(thisServer);
                            logger.info("Server " + thisServer + " telling it to try again");
                            ecsConnection.writeln("try again");
                            continue;
                        }
                    } else {
                        //When it's the first server, notify immediately
                        notifyDataChange();
                    }
                    isAddSent = true;
                }
            } else if (currentLine.startsWith("confirm transfer") && isAddSent) {
                //set the flag in the object to true so the other thread can realize that the
                // transfer is successful from both server
                logger.info("Recognized confirm transfer message from: " + ecsConnection.socket.getRemoteSocketAddress());
                ecsConnection.setTransferDone(true);
            } else if (currentLine.startsWith("remove") && isAddSent) {
                synchronized (hashing) {
                    //I want to make sure that there is only one add/remove at a time
                    //The situation when server A is transfering, and at the same time receiving, might be possible, but potentially buggy
                    logger.info("Initiating shutdown procedure for server");
                    var changedServer = hashing.removeNode(thisServer);
                    if (changedServer != null) {
                        //The data is considered lost if the only server left is shutting down
                        logger.info("Invoking data transfer");
                        invokeDataTransfer(thisServer, changedServer, true);
                    }
                    var serverConnection = (ServerActiveConnection) ecsConnection;
                    serverConnection.writeln("shutdown");
                    serverConnection.setShutdownMessageSent();
                    notifyDataChange();
                    logger.info("removed server with connection :" + ecsConnection.socket.getRemoteSocketAddress());
                    break;
                }
            } else if (currentLine.startsWith("subscription service") && commandArr.length > 5) {
                logger.info("processing subscribe request");
                SubscriptionInformation info = ecs.getInfo(commandArr[3]);
                //register unknown user
                if (info == null) {
                    if (commandArr[2].equals("update")) {
                        commandArr[2] = "register";
                        info = new SubscriptionInformation();
                    } else {
                        continue;
                    }
                }
                if (!commandArr[2].equals("register") && !info.getPassword().equals(commandArr[4])) {
                    logger.info("Authentication failed: " + Arrays.toString(commandArr));
                    ecsConnection.writeln(ECSProtocol.unauthorised());
                    continue;
                }
                InetAddress ip = InetAddress.getLoopbackAddress();
                if ((commandArr[2].equals("register") || commandArr[2].equals("update")) && commandArr.length == 7) {
                    try {
                        ip = InetAddress.getByName(commandArr[5]);
                    } catch (UnknownHostException e) {
                        logger.info("malformed update: " + currentLine);
                        ecsConnection.writeln(ECSProtocol.error());
                        continue;
                    }
                }
                //sending command without password -> authentication only on ecs
                String command = commandArr[2] + " " + commandArr[3];
                switch (commandArr[2]) {
                    case "login":
                        info.setOnline(true);
                        notifySubscriptionChange(ECSProtocol.updateSubscriptions(command));
                        break;
                    case "logout":
                        info.setOnline(false);
                        notifySubscriptionChange(ECSProtocol.updateSubscriptions(command));
                        break;
                    case "register":
                        ecs.addSubscription(commandArr[3], new SubscriptionInformation(commandArr[4], ip, Integer.parseInt(commandArr[6]), new HashSet<>(), true));
                        notifySubscriptionChange(ECSProtocol.updateSubscriptions(command + " " + commandArr[5] + " " + commandArr[6]));
                        break;
                    case "update":
                        info.setIpAddress(ip);
                        info.setPort(Integer.parseInt(commandArr[6]));
                        info.setOnline(true);
                        notifySubscriptionChange(ECSProtocol.updateSubscriptions(command + " " + commandArr[5] + " " + commandArr[6]));
                        break;
                    case "subscribe":
                        if (commandArr.length == 6) {
                            info.addKey(commandArr[5]);
                            notifySubscriptionChange(ECSProtocol.updateSubscriptions(command + " " + commandArr[5]));
                        }
                        break;
                    case "unsubscribe":
                        if (commandArr.length == 6) {
                            info.removeKey(commandArr[5]);
                            notifySubscriptionChange(ECSProtocol.updateSubscriptions(command + " " + commandArr[5]));
                        }
                        break;
                    default:
                        logger.severe(String.format("Malformed message from server: %s", currentLine));
                        ecsConnection.writeln(ECSProtocol.error());
                        break;
                }
            } else {
                logger.severe(String.format("Malformed message from server: %s", currentLine));
            }
            logger.info("Waiting for next message");
            currentLine = ecsConnection.readLine();
        }
        logger.info("Closing connection to server " + ecsConnection.getInfo());
        var serverConnection = (ServerActiveConnection) ecsConnection;
        if (!serverConnection.isShutdownMessageSent()) {
            logger.severe("Server shutdown abnormally");
            hashing.removeNode(thisServer);
            notifyDataChange();
        }
        serverConnection.close();
    }

    /**
     * @param isRemoving : signal that this invoked because a server is shutting down, in that case
     *                   we don't care if we receive the confirm message from both server or not, we change the hash
     *                   anyway.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private boolean invokeDataTransfer(ServerData from, ServerData to, boolean isRemoving) {
        var fromConnection = (ServerActiveConnection)
                ecsActiveConnections.stream().filter(connection -> connection.getAddress().equals(from.getServerIp())
                        && connection.socket.getPort() == from.getEcsPort()).findAny().get();
        var toConnection = (ServerActiveConnection)
                ecsActiveConnections.stream().filter(connection -> connection.getAddress().equals(from.getServerIp())
                        && connection.socket.getPort() == to.getEcsPort()).findAny().get();

        fromConnection.setTransferDone(false);
        toConnection.setTransferDone(false);
        //These two writes should be after set to prevent missed confirm timing
        toConnection.writeln(ECSProtocol.invokeReceiveFrom(from));
        fromConnection.writeln(ECSProtocol.invokeTransferTo(to));
        if (isRemoving) {
            logger.info("Executed invoking data transfer because of server shutdown.");
            //also need to wait here, otherwise server shuts down before data is transferred
            for (int j = 0; j < 10; j++) {
                try {
                    // because if server is removed, we are in the fromConnection thread -> everything is the other way around
                    if (!toConnection.isTransferDone()) {
                        Thread.sleep(2000);
                        logger.info("Checking transfer state, not done yet");
                        continue;
                    }
                    // toConnection is the connection of this thread so we can do the read here
                    var fromResult = fromConnection.readLine();
                    if (fromResult == null) {
                        logger.info("Server closed suddenly during transfer. Transfer failed");
                        return false;
                    } else {
                        fromConnection.setTransferDone(fromResult.equals("confirm transfer"));
                        logger.info("Received message from both server. Data transfer executed");
                        return true;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            logger.info("Can't receive confirmation message from both server, transfer failed");
            return false;
        }
        logger.info("Waiting for servers to finish their data transfer");
        for (int i = 0; i < 10; i++) {
            try {
                //because when server removed, the fromServer is the server of this thread
                if (!fromConnection.isTransferDone()) {
                    Thread.sleep(2000);
                    logger.info("Checking transfer state, not done yet");
                    continue;
                }
                // toConnection is the connection of this thread so we can do the read here
                var toResult = toConnection.readLine();
                if (toResult == null) {
                    logger.info("Server closed suddenly during transfer. Transfer failed");
                    return false;
                } else {
                    toConnection.setTransferDone(toResult.equals("confirm transfer"));
                    logger.info("Received message from both server. Data transfer executed");
                    return true;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        logger.info("Can't receive confirmation message from both server, transfer failed");
        return false;
    }

    private void notifyDataChange() {
        logger.info("invoke data update notification");
        var newMetaData = new ServerSet(hashing);
        for (var s : ecsActiveConnections) {
            s.writeln(ECSProtocol.updateMetadata(newMetaData));
        }
        ecs.replicationService.metaDataSnapshot.push(newMetaData);
    }

    private void notifySubscriptionChange(String command) {
        logger.info("invoke subscription update notification");
        for (var s : ecsActiveConnections) {
            s.writeln(command);
        }
    }
}

