package de.tum.i13.server.threadperconnection;

import de.tum.i13.server.kv.Database;
import de.tum.i13.server.kv.KVCommandProcessor;
import de.tum.i13.shared.Config;
import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;
import de.tum.i13.shared.datastructure.SubscriptionInformation;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import static de.tum.i13.shared.Config.parseCommandlineArgs;
import static de.tum.i13.shared.LogSetup.setupLogging;


public class KVServer {

    public static final Logger logger = Logger.getLogger(KVServer.class.getName());
    /**
     * Used as message passing for the threads. Normally there is only 1 element in the thread. We
     * set to 10 just to be safe
     */
    public final BlockingQueue<ServerData> transferQueue = new ArrayBlockingQueue<>(10);
    ActiveConnection ecsConnection;
    ECSCommunicationThread ecsCommunicationThread;
    InetSocketAddress ecsServer;
    private volatile boolean running = false;
    private ServerSet serversMetaData;
    private ServerData thisServer;
    public final KVReplicationService kvRepService = new KVReplicationService(this);
    private KVCommandProcessor logic;
    private Database database;
    private boolean writeLock = false;
    private ServerSet replicaData;
    private Map<String, SubscriptionInformation> subscriptions = new HashMap<>();
    //TODO save updates for offline subscribers
    //TODO send updates to online subscribers

    public static void main(String[] args) throws IOException {

        KVServer kvserver = new KVServer();
        kvserver.run(args);
    }

    public void run(String[] args) throws IOException {

        Config cfg = parseCommandlineArgs(args);  //Do not change this
        setupLogging(cfg.logfile, cfg.loglevel);

        if (cfg.usagehelp) {
            System.out.println("If you start up the server without any options the default values" +
                    " will be used.");
            System.out.println("Options you can use:");
            System.out.println("-p  sets the port of the server             default: 5153");
            System.out.println("-a  which address the server should listen to               " +
                    "default: 127.0.0.1");
            System.out.println("-b  bootstrap broker where clients and other brokers connect " +
                    "first to retrieve configuration, port and ip, e.g., 192.168.1.1:5153     " +
                    "default: 127.0.0.1:5152");
            System.out.println("-d  Directory for files             default: data/");
            System.out.println("-l  Logfile             default: server.log");
            System.out.println("-ll LogLevel            default: INFO");
            System.out.println("-c  Sets the cacheSize, e.g., 100 keys              default: INFO");
            System.out.println("-s  Sets the cache displacement strategy, FIFO, LRU, LFU         " +
                    "       default: FIFO");
            System.out.println("-h  displays this help-message");
            System.out.println("The server will not be started, when using the help option");
            return;
        }

        final ServerSocket serverSocket = new ServerSocket();

        logger.info("KVServer started!\n" +
                "Configuration:\n" +
                "address:               " + cfg.listenaddr + "\n" +
                "port:                  " + cfg.port + "\n" +
                "directory:             " + cfg.dataDir.toString() + "\n" +
                "bootstrap:             " + cfg.bootstrap.toString() + "\n" +
                "logfile:               " + cfg.logfile.toString() + "\n" +
                "log-Level:             " + cfg.loglevel + "\n" +
                "cache Size:            " + cfg.cachesize + "\n" +
                "cache Displacement:    " + cfg.cachedisplacement + "\n\n");

        //bind to localhost only
        try {
            serverSocket.bind(new InetSocketAddress(cfg.listenaddr, cfg.port));
        } catch (BindException e) {
            logger.severe("Socket is used");
            return;
        }

        setServerData(new ServerData(InetAddress.getByName(cfg.listenaddr), cfg.port));

        database = new Database(cfg.cachesize,
                cfg.cachedisplacement, cfg.dataDir);
        logic = new KVCommandProcessor(database, this, ecsCommunicationThread);


        ecsServer = cfg.bootstrap;
        ecsConnection = new ActiveConnection(ecsServer.getAddress(),
                ecsServer.getPort());
        ecsCommunicationThread = new ECSCommunicationThread(this);
        setRunning(true); //Should be before ECSCommunicationThread start to prevent race condition
        ecsCommunicationThread.start();

        Thread tRep = new Thread(kvRepService);
        tRep.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Initiating shutdown sequence");
            ecsCommunicationThread.remove();
            try {
                ecsCommunicationThread.join(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    serverSocket.close();
                    logger.info("KVServer main thread shut down");
                } catch (IOException ignored) {
                }
            }
            logger.info("closed");
        }));

        logger.info("entering main loop");
        while (this.running) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (SocketException s) {
                logger.info(s.getMessage());
                logger.severe(s.getMessage());
                continue;
            }
            //When we accept a connection, we start a new Thread for this connection
            if (clientSocket != null) {
                Thread th = new Thread(new ConnectionThread(this, clientSocket));
                th.start();
            }
        }
    }

    public synchronized boolean isWriteLock() {

        return writeLock;
    }

    public synchronized void setWriteLock(boolean writeLock) {

        this.writeLock = writeLock;
    }

    synchronized public boolean getRunning() {

        return this.running;
    }

    synchronized public ServerSet getServersMetaData() {

        return this.serversMetaData;
    }

    synchronized public void setServersMetaData(ServerSet serversMetaData) {

        this.serversMetaData = serversMetaData;
    }

    synchronized public ServerData getServerData() {

        return this.thisServer;
    }

    synchronized public void setServerData(ServerData serverData) {

        this.thisServer = serverData;
    }

    synchronized public ServerSet getReplicaData() {
        return replicaData;
    }

    synchronized public void setReplicaData(ServerSet serverData) {
        this.replicaData = serverData;
    }

    synchronized public Database getDatabase() {

        return database;
    }

    public KVCommandProcessor getLogic() {

        return logic;
    }

    synchronized public boolean isRunning() {
        return running;
    }

    synchronized public void setRunning(boolean running) {

        this.running = running;
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
