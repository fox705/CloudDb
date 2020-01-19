package de.tum.i13.server.threadperconnection;

import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Periodically check the queue for new elements and broadcast it to all replicas
 * The queue is like a journal which records all the received commands from user so far
 */
public class KVReplicationService implements Runnable {
    public final List<String> journal = new CopyOnWriteArrayList<>();
    private final KVServer kv;
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    /**
     * ECSCommunication class set this property to true so that we start the initializing procedure
     * because the replica metadata changed
     * <p>
     * This variable is to signal this thread that the replica data has changed.
     */
    private boolean isNew = true;
    
    public KVReplicationService(KVServer kv) {
        this.kv = kv;
    }
    
    @Override
    public void run() {
        //Establish the connection to the other KVFirst
        Collection<ActiveConnection> replicaConnectionSet = new ArrayList<>();
        do {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            if (isNew()) {
                if (kv.getReplicaData() == null) {
                    logger.info("No replica data");
                    continue; //No replica probably because ring still too few servers and we haven't received update replica yet
                }
                logger.info("Recognized replica data change");
                kv.setWriteLock(true); //prevents the client from applying any new data during replication process
                var replicaData = kv.getReplicaData().getServerData();
                //kv.getDatabase().clearAllReplica(); //We keep  old replica when main shutting down, that is the purpose of replica, right?
                journal.clear();
                replicaConnectionSet.forEach(ActiveConnection::close);
                //When replicaData only contains this server itself skip(1) already handles that case
                replicaConnectionSet = replicaData.parallelStream().skip(1).map(this::initializeConnectionFrom).
                        filter(Objects::nonNull).collect(Collectors.toList());
                setNew(false);
                kv.setWriteLock(false);
                continue;
            }
            if (journal.isEmpty()) continue;
            logger.info("Replica journal not empty, begin broadcasting to other replicas");
            for (var entry : journal) {
                //broadcast this to all replicas
                for (var replicaConnection : replicaConnectionSet) {
                    logger.info("Sending entry " + entry);
                    replicaConnection.writeln(entry);
                }
            }
            journal.clear();
        } while (kv.isRunning());
    }
    
    /**
     * Make a connection to the server s. Consume the welcome message and send all current data to it
     * One of the assumptions is that we can always connect to another KV server, provided we try enough
     *
     * @param s server s data
     * @return The connection to the server s
     */
    private ActiveConnection initializeConnectionFrom(ServerData s) {
        //We are stubborn, continually try to establish connection in case of failure
        ActiveConnection ac;
        while (true) {
            try {
                ac = new ActiveConnection(s);
                break;
            } catch (IOException e) {
                try {
                    logger.info(String.format("Unable to have a connection to %s, try again...", s));
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                //The connection might fail if the server is not anymore on the ring, we check this
                if (kv.getServersMetaData().getDataToThisServer(s.getServerIp().toString(), s.getClientPort()) == null)
                    return null;
            }
        }
        logger.info("Reading the welcome message from the replica server");
        logger.info(ac.readLine()); //skip the welcome message
        ac.writeln("replica service"); //inform the other server that this connection is for the replica service
        //Transfer all the avalailable data to the other server
        var keysCurrently = kv.getDatabase().keySet();
        var store = kv.getDatabase();
        for (String key : keysCurrently) {
            String value = null;
            try {
                value = store.get(key);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            ac.writeln(String.format("put %s %s", key, value));
        }
        logger.info("Finished transfer initial data to replicas");
        return ac;
    }
    
    /**
     * This is called by the ConnectionThread class to handle transfer request for replica
     * The template should be similar to ClientKVConnectionHandler minus a few part
     * It may look like it but this method is actually running in its own thread, forwarded by ConnectionThread
     */
    public void handleReplicaRequest(ActiveConnection ac) {
        var store = kv.getDatabase();
        assert store != null;
        logger.info("Handling replica request from " + ac);
        String line;
        while ((line = ac.readLine()) != null) {
            assert line.matches("(put|delete) (?<key>\\S+) (?<value>.*)");
            var matcher = Pattern.compile("(?<command>put|delete) (?<key>\\S+)( (?<value>.*))?").matcher(line);
            var s = matcher.matches();
            assert s;
            logger.info("Received replica request for " + matcher.group("key"));
            switch (matcher.group("command")) {
                case "put":
                    try {
                        store.putReplica(matcher.group("key"), matcher.group("value"));
                    } catch (IOException e) {
                        logger.severe("Can't replicate some string");
                    }
                    break;
                case "delete":
                    kv.getDatabase().deleteReplica(matcher.group("key"));
            }
        }
        logger.info("Closing replica request connection");
    }
    
    synchronized public boolean isNew() {
        return isNew;
    }
    
    synchronized public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
