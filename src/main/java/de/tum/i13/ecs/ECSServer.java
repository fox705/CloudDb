package de.tum.i13.ecs;

import de.tum.i13.shared.ECSConfig;
import de.tum.i13.shared.datastructure.ConsistentHashing;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.SubscriptionInformation;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static de.tum.i13.shared.LogSetup.setupLogging;

public class ECSServer {
    public final ConsistentHashing hashing = new ConsistentHashing();
    /**
     * Save a list of all the connection to other servers so far.
     * The type is very important. CopyOnWrite makes sure that iterator is unchanged by other threads
     */
    public final List<ServerActiveConnection> activeConnections = new CopyOnWriteArrayList<>();
    private Map<String, SubscriptionInformation> subscriptions = new HashMap<>();
    public ServerData thisServer; //This only set after receiving argument from CLI
    private boolean isRunning = false; //provide a mean for other threads to kill this
    PingService pingService;
    ReplicationService replicationService;
    
    /**
     * main method to start the server from command line
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) {
        ECSServer ecsServer = new ECSServer();
        try {
            ecsServer.run(args);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException ignored) {
        }
    }
    
    /**
     * starts the server and keeps it going
     *
     * @param args
     * @throws IOException
     */
    public void run(String[] args) throws IOException {
        ECSConfig cfg = ECSConfig.parseCommandlineArgs(args);  //Do not change this
        thisServer = new ServerData(InetAddress.getByName(cfg.listenaddr), cfg.port);
        setupLogging(cfg.logfile, cfg.loglevel);
        
        if (cfg.usagehelp) {
            System.out.println("If you start up the server without any options the default values will be used.");
            System.out.println("Options you can use:");
            System.out.println("-p  sets the port of the server             default: 5152");
            System.out.println("-a  which address the server should listen to               " +
                    "default: 127.0.0.1");
            System.out.println("-pp  port on which the ECS is pinging all other Servers" +
                    "default: 55555");
            System.out.println("-l  Logfile             default: ecs.log");
            System.out.println("-ll LogLevel            default: INFO");
            System.out.println("-h  displays this help-message");
            System.out.println();
            System.out.println("The server will not be started, when using the help option");
            
            return;
        }
    
        final ServerSocket serverSocket = new ServerSocket();
    
        System.out.println("Configuration:\n" +
                "address:               " + cfg.listenaddr + "\n" +
                "port:                  " + cfg.port + "\n" +
                "logfile:               " + cfg.logfile.toString() + "\n" +
                "log-Level:             " + cfg.loglevel + "\n");
        
        //bind to localhost only
        serverSocket.bind(new InetSocketAddress(cfg.listenaddr, cfg.port));
    
        //Setup essential service
        pingService = new PingService(activeConnections, hashing);
        Thread ping = new Thread(pingService);
        ping.start();
    
        replicationService = new ReplicationService(this);
        Thread repService = new Thread(replicationService);
        repService.start();
    
        isRunning = true;
        Logger logger = Logger.getLogger(ECSServer.class.getName());
        logger.info("Server started, entering main loop");
    
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down ECS");
            logger.info("Closing ECSServer");
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }));
        while (isRunning()) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (SocketException s) {
                break;
            }
            var connection = new ServerActiveConnection(clientSocket);
            activeConnections.add(connection);
            var logic = new ECSandServerCommunicationLogic(this, connection, pingService);
            logic.start();
        }
    
        pingService.setRunning(false);
    }
    
    boolean isRunning() {
        return isRunning;
    }
    
    public void setRunning(boolean running) {
        isRunning = running;
    }

    public synchronized void addSubscription(String user, SubscriptionInformation subscriptionInformation) {
            if (!subscriptions.containsKey(user)) {
                subscriptions.put(user, subscriptionInformation);
            }
    }

    public synchronized SubscriptionInformation getInfo(String user) {
            return subscriptions.get(user);
    }
}
