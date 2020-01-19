package de.tum.i13.server.kv;

import de.tum.i13.server.threadperconnection.ECSCommunicationThread;
import de.tum.i13.server.threadperconnection.KVServer;
import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.Hash;

import java.io.FileNotFoundException;
import java.util.logging.Logger;

public class KVCommandProcessor {
    private static final Logger logger = Logger.getLogger(KVCommandProcessor.class.getName());
    private final Database database;
    private final KVServer kvServer;
    private final ECSCommunicationThread ecsCommunicationThread;
    
    public KVCommandProcessor(Database database, KVServer kvServer, ECSCommunicationThread ecsCommunicationThread) {
        this.database = database;
        this.kvServer = kvServer;
        this.ecsCommunicationThread = ecsCommunicationThread;
    }
    
    public String process(String command) {

        String[] args = command.split(" ", 3);
    
        String ret;

        switch (args[0]) {
            case "put":
                ret = handlePut(args);
                break;
            case "get":
                ret = handleGet(args);
                break;
            case "delete":
                ret = handleDelete(args);
                break;
            case "keyrange":
                ret = handleKeyrange();
                break;
            case "keyrange_read":
                ret = handleKeyrangeRead();
                break;
            case "login":
                if (kvServer.getInfo(args[1]) == null) {
                    // command = ECSProtocol.register(args[1],args[2],,);
                    // TODO register user
                }
            case "logout":
            case "register":
                // TODO add ip and port
            case "update":
                // TODO add ip and port
            case "subscribe":
            case "unsubscribe":
                ecsCommunicationThread.forwardToECS(command);
            default:
                ret = "error wrong command!";
        }
    
        return ret;
    }
    
    /**
     * handle put command
     *
     * @param args
     * @return server reponse
     */
    private String handlePut(String[] args) {
        if (args.length < 2)
            return "error too few arguments for put";
        else {
            String key = args[1];
            if (!kvServer.getServerData().isResponsible(key)) {
                logger.info(String.format("key: %s not in range of %s to %s", (new Hash(key)), kvServer.getServerData().getFirstHash().toString(16),
                        kvServer.getServerData().getLastHash().toString(16)));
                return "server_not_responsible";
            }
            String value = args[2];
            KeyStatus updated;
            try {
                updated = database.put(key, value);
                kvServer.kvRepService.journal.add(String.format("put %s %s", key, value));
            } catch (Exception e) {
                logger.severe("Error while putting value: " + value + " in key: " + key + "!\n");
                return "put_error " + key + " " + value;
            }
            switch (updated) {
                case Created:
                    return "put_success " + key;
                case Updated:
                    return "put_update " + key;
                default:
                    return "put_error " + key + " " + value;
            }
        }
    }
    
    /**
     * handle get command
     * This function differs from the other command a bit, in that if it receives a key that it's not responsible for and
     * try to search it in database (because it might be a replica). If it can find the key, then it returns the key. Otherwise
     * it return server_not_responsible as normal
     *
     * @param args command splitted in white space character
     * @return server response
     */
    private String handleGet(String[] args) {
        if (args.length < 2)
            return "error too few arguments for get";
        else if (args.length > 2)
            return "error too many arguments for get";
        else {
            String key = args[1];
            String value;
            try {
                value = database.get(key);
                value = value.replaceAll("\n", "  "); //we cant send values with new line
            } catch (FileNotFoundException e) {
                if (!kvServer.getServerData().isResponsible(key)) {
                    var replicaData = kvServer.getReplicaData();
                    if (replicaData == null) {
                        return "server_not_responsible";
                    } else if (replicaData.getServerData().stream().skip(1).anyMatch(
                            serverData -> serverData.isResponsible(key)
                    )) {
                        //Find out if its one of the replica keys
                        return "get_error " + key;
                    }
                    return "server_not_responsible";
                }
                logger.severe("Error while getting the value of key: " + key + "!\n");
                return "get_error " + key;
            }
            return "get_success " + key + " " + value;
        }
    }
    
    private String handleKeyrange() {
        return String.format("keyrange_success %s", kvServer.getServersMetaData());
    }
    
    /**
     * handle delete command
     *
     * @param args
     * @return
     */
    private String handleDelete(String[] args) {
        if (args.length < 2)
            return "error too few arguments for delete";
        else if (args.length > 2)
            return "error too many arguments for delete";
        else {
            String key = args[1];
            if (!kvServer.getServerData().isResponsible(key)) {
                logger.info(String.format("key: %s not in range of %s to %s", (new Hash(key)), kvServer.getServerData().getFirstHash().toString(16),
                        kvServer.getServerData().getLastHash().toString(16)));
                return "server_not_responsible";
            }
            if (database.delete(key)) {
                kvServer.kvRepService.journal.add(String.format("delete %s", key));
                return "delete_success " + key;
            } else {
                return "delete_error " + key;
            }
        }
    }
    
    private String handleKeyrangeRead() {
        if (kvServer.getReplicaData() == null)
            return String.format("keyrange_read_success %s", kvServer.getServerData());
        return String.format("keyrange_read_success %s", kvServer.getReplicaData());
    }
}
