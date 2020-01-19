package de.tum.i13.server.threadperconnection;

import de.tum.i13.server.kv.Database;
import de.tum.i13.shared.Hash;
import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Handle connection between 2 KV servers used for data transfer during metadata update
 */
class KVKVConnectionHandler {
    private final KVServer kvServer;
    private final ActiveConnection ac; //Connection to the receiving KV Server
    private final Logger logger = Logger.getLogger(KVKVConnectionHandler.class.getName());
    
    public KVKVConnectionHandler(KVServer kvServer,
                                 ActiveConnection ac) {
        this.kvServer = kvServer;
        this.ac = ac;
    }
    
    public void handle(String address) {
        logger.info("Initiating KVKV Connection handler");
        var queue = kvServer.transferQueue;
        ServerData serverData; //This should always return the serverData we want
        try {
            serverData = queue.take();
        } catch (InterruptedException e) {
            logger.info("Couldn't deque ServerData: " + e.getMessage() + Arrays.toString(e.getStackTrace()));
            ac.close();
            return;
        }
        if (!address.equals(serverData.getServerIp().getHostAddress()
                + ":" + serverData.getClientPort())) {
            logger.info("Unauthorised!");
            ac.writeln("Unauthorised!");
            ac.close();
            queue.add(serverData);
            return;
        }
        // because we only put one in there
        logger.info("Prepare to transfer to server: " + serverData);
        synchronized (kvServer) {
            //We synchronize this whole block because otherwise it may lead to inconsistent state of write lock if it breaks middle of the thread
            kvServer.setWriteLock(true);
            Database store = kvServer.getDatabase();
            //Get the smallest hash from the database that is larger than its first hash
            for (String key : store.keySet()) {
                var hashKey = (new Hash(key)).md5Value;
                if (hashKey.compareTo(serverData.getLastHash()) <= 0 && hashKey.compareTo(serverData.getFirstHash()) >= 0) {
                    try {
                        var value = store.get(key);
                        value = value.replaceAll("\n", "  ");
                        logger.info(String.format("Transfering key : %s", key));
                        ac.writeln(String.format("put %s %s", key, value));
                        store.delete(key);
                    } catch (FileNotFoundException e) {
                        //This should never happen because our file is from keySet() call
                        logger.info(String.format("failed to delete file: %s, possibly because" +
                                " we are " +
                                "on Windows", key));
                    }
                }
            }
            ac.writeln("confirm end transfer");
            if (!ac.readLine().equals("confirm end transfer")) {
                logger.info("The other server can't confirm transfer done successfully");
            }
            logger.info("Writing confirm transfer message to ECS");
            kvServer.ecsConnection.writeln("confirm transfer");
            kvServer.setWriteLock(false);
        }
        
        ac.close();
        logger.info("data transfer finished, closed KVKV connection");
    }
}
