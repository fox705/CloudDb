package de.tum.i13.client;

import de.tum.i13.shared.Hash;
import de.tum.i13.shared.InetSocketAddressTypeConverter;
import de.tum.i13.shared.datastructure.ActiveConnection;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/*
 *Class which handles communication logic from client to server
 */
public class KVStore {

    private final Logger logger = Logger.getLogger(KVStore.class.getName());
    private final Map<ServerData, ServerSet> replicaTable = new HashMap<>();
    private ServerSet metaData;
    private ActiveConnection ac;
    private ServerData connectingServer;
    private Optional<User> currentUser;

    public KVStore() {
        this.metaData = new ServerSet();
        currentUser = Optional.empty();
    }


    private String communicate(String key, Action a) throws IOException, NoServerAliveException {
        var isWriting = a != Action.get;
        Hash md5Key;
        if (a == Action.put) {
            md5Key = new Hash(key.split(" ")[0]);
        } else {
            md5Key = new Hash(key);
        }
        var sentString = String.format("%s %s", a, key);
        var connection = tryFindAliveServer(md5Key, isWriting);
        var backoffTime = 0;
        //let it loop maximal 10 times because of exponential back off
        for (int i = 0; i < 10; i++) {
            connection.writeln(sentString);
            var response = connection.readLine();
            logger.fine("received:" + response);
            var acceptedResponse =
                    Stream.of(new String[]{"_success", "_error", "_update"}).map(word -> a + word).filter(word -> !word.equals("get_update") | !(word.equals("delete_update"))).collect(
                            toList()
                    );
            if (response == null) {
                return null;
            }
            if (acceptedResponse.contains(response.split(" ")[0])) {
                connection.close();
                return response;
            } else if (response.startsWith("server_write_lock")) {
                connection.close();
                return response;
            } else if (response.startsWith("server_not_responsible")) {
                replicaTable.clear(); //server_not_responsible indicates a ring structure change, we reset the replicatable just to be sure
                connection.writeln("keyrange");
                response = connection.readLine();
                logger.fine("received:" + response);
                if (response.startsWith("keyrange_success")) {
                    response = response.split(" ")[1]; //remove the keyrange_success part
                    metaData = ServerSet.parseFromString(response);
                    //initialize with new data and try again
                    connection.close();
                    connection = tryFindAliveServer(md5Key, isWriting);
                } else if (response.contains("server_stopped")) {
                    backoffTime++;
                    try {
                        Thread.sleep((long) exponentialWaitWithJitter(backoffTime));
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    connection.close();
                    return null;
                }
            } else if (response.startsWith("server_stopped")) {
                backoffTime++;
                try {
                    Thread.sleep((long) exponentialWaitWithJitter(backoffTime));
                } catch (InterruptedException ignored) {
                }
            } else {
                connection.close();
                return null;
            }
        }
        return null;
    }

    /**
     * Get a server responsible for the key. If a replica is present, randomize the out put server. This method automatically reads
     * the welcome message and return the ongoing connection. If for whatever the connection fails, try all the remaining server on the
     * metadata ring.
     *
     * @param md5Key    hash of key
     * @param isWriting indicates if it is put/delete or get
     * @return ongoing connection
     * @throws NoServerAliveException
     */
    private ActiveConnection tryFindAliveServer(Hash md5Key, boolean isWriting) throws NoServerAliveException {
        var s = getServerFromHash(md5Key);
        //randomize s according to the replica table
        var replicaData = replicaTable.get(s);
        var shouldIAskForReplicaData = false;
        if (replicaData != null && !isWriting) {
            var backedList = replicaData.getServerData();
            int random = (int) (backedList.size() * Math.random());
            if (random == backedList.size() && random > 0) {
                random--;
            }
            s = backedList.get(random);
        } else if (!isWriting) {
            shouldIAskForReplicaData = true;
        }
        ActiveConnection connection;
        try {
            connection = new ActiveConnection(s); // may give error because the metadata is stale and server is already shutdown
            connection.readLine(); //Skip the welcome message
            //At this point the connection should be successful, we may ask the replicadata here
            if (shouldIAskForReplicaData) {
                connection.writeln("keyrange_read");
                var response = connection.readLine();
                if (response != null && response.startsWith("keyrange_read_success")) {
                    try {
                        var data = response.split(" ")[1];
                        replicaTable.put(new ServerData(connection.socket.getInetAddress(), connection.socket.getPort()), ServerSet.parseFromString(data));
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                } else {
                    logger.severe("keyrange_read not successful, this maybe due to balancing period, not necessarily an error");
                }
            }
            return connection;
        } catch (IOException e) {
            metaData.getServerData().remove(s);
            //try all remaining servers to get the new metadata
            Optional<ServerData> server;
            while ((server = metaData.getServerData().stream().findAny()).isPresent()) {
                try {
                    connection = new ActiveConnection(server.get());
                    connection.readLine(); //remove welcome message
                    return connection;
                } catch (IOException m) {
                    System.out.println(m.getMessage());
                    metaData.getServerData().remove(server.get());
                }
            }
        }
        throw new NoServerAliveException();
    }

    private double exponentialWaitWithJitter(int backoffTime) {
        var baseTime = 1000;
        var multiplier = 1.25;
        var jitter = Math.random() * 400;
        return baseTime * Math.pow(multiplier, backoffTime) + (jitter - 200);
    }

    private ServerData getServerFromHash(Hash hash) throws NoServerAliveException {
        if (metaData == null)
            throw new NoServerAliveException();
        var hashInteger = hash.md5Value;
        for (ServerData s : metaData.getServerData()) {
            if (hashInteger.compareTo(s.getFirstHash()) >= 0 && hashInteger.compareTo(s.getLastHash()) <= 0)
                return s;
        }
        //This should never happen
        throw new NoServerAliveException();
    }

    public String connect(String ipAddress, int port) throws IOException {
        setBaseServer(ipAddress, port);
        var socketString = String.format("%s:%d", ipAddress, port);
        var converter = new InetSocketAddressTypeConverter();
        var socketAddr = converter.convert(socketString);
        ac = new ActiveConnection(socketAddr.getAddress(), socketAddr.getPort());
        connectingServer = new ServerData(socketAddr.getAddress(), socketAddr.getPort());
        return ac.readLine();
    }

    //This method is used so other custom client can use it to set the base server
    public void setBaseServer(String ipAddress, int port) throws UnknownHostException {
        var s = new ServerData(InetAddress.getByName(ipAddress), port);
        if (!metaData.getServerData().contains(s)) {
            metaData.addServer(s);
        }
    }

    public void disconnect() throws DisconnectException {
        if (ac == null)
            throw new DisconnectException();
        ac.close();
        ac = null;
        metaData = new ServerSet(); //reset the metadata
        connectingServer = null;
    }

    public String put(String key, String value) throws IOException, NoServerAliveException {
        return communicate(key + " " + value, Action.put);
    }

    public String get(String key) throws IOException, NoServerAliveException {
        return communicate(key, Action.get);
    }

    public void subscribe(String key) throws IOException {
        // check if there is connection
        var myServer = metaData.getServerData().stream().findAny();
        if (isLoggedIn()) {
            if (!myServer.isEmpty()) {
                var myConnection = new ActiveConnection(myServer.get());
                myConnection.readLine();
                var command = String.format("subscribe %s %s %s", currentUser.get().userName, currentUser.get().password, key);
                myConnection.writeln(command);
                var message = myConnection.readLine();
                logger.info(message);
            } else {
                logger.info("No avaiblable server!");
            }
        } else {
            logger.info("User not logged in");
        }

        //if there is send the subscribe
        //if not ask for connection.
        // subscribe userid  user password keyto subscribe
    }


    public void unsubscribe(String key) throws IOException {
        var myServer = metaData.getServerData().stream().findAny();
        if (isLoggedIn()) {
            if (!myServer.isEmpty()) {
                var myUser = currentUser.get();
                var myConnection = new ActiveConnection(myServer.get());
                myConnection.readLine();
                var command = String.format("unsubscribe %s %s %s", myUser.userName, myUser.password, key);
                myConnection.writeln(command);
                var message = myConnection.readLine();
                logger.info(message);
            } else {
                logger.info("No avaible server!");
            }
        } else {
            logger.info("User not logged in");
        }
    }

    public void register(String username, String password) throws IOException {
        var myServer = metaData.getServerData().stream().findAny();
        if (!myServer.isEmpty()) {
            var myConnection = new ActiveConnection(myServer.get());
            myConnection.readLine();
            myConnection.writeln(String.format("register %s %s", username, password));
            var message = myConnection.readLine();
            logger.info(message);
        } else {
            logger.info("No available server!");
        }
    }

    public void login(String username, String password) throws IOException {
        //Find a server in your  metadata
        //Connect to it
        //send the login data to this server and read its response
        //If the response is successful
        //you do this
        var listener = new ServerSocket(0);
        var myServer = metaData.getServerData().stream().findAny();
        if (!myServer.isEmpty()) {
            var myConnection = new ActiveConnection(myServer.get());
            myConnection.readLine();
            myConnection.writeln(String.format("login %s %s %d", username, password, listener.getLocalPort()));
            var message = myConnection.readLine();
            if (message.contains("login successful")) {
                this.currentUser = Optional.of(new User(username, password, listener.getLocalPort()));
                var listenerThread = new Thread(new ClientListenerThread(this, listener));
                listenerThread.start();
            } else {
                logger.info("login not successful");
            }
        } else {
            logger.info("No available server!");
        }

    }

    public void logout() throws IOException {
        if (currentUser.isEmpty()) {
            logger.info("User already logged out");
        } else {
            var myServer = metaData.getServerData().stream().findAny();
            if (!myServer.isEmpty()) {
                var myConnection = new ActiveConnection(myServer.get());
                myConnection.readLine();
                var message = myConnection.readLine();
                myConnection.writeln(String.format("logout %s %s", currentUser.get().userName, currentUser.get().password));
                logger.info(message);
                currentUser = Optional.empty();
            } else {
                logger.info("No available server!");
            }
        }
    }

    public void showSubscriptions() throws IOException {
        var myServer = metaData.getServerData().stream().findAny();
        if (isLoggedIn()) {
            if (!myServer.isEmpty()) {
                var myUser = currentUser.get();
                var myConnection = new ActiveConnection(myServer.get());
                myConnection.readLine();
                var command = String.format("showsubscriptions %s %s", myUser.userName, myUser.password);
                myConnection.writeln(command);
                var message = myConnection.readLine();
                logger.info(message);
            } else {
                logger.info("No avaible server!");
            }
        } else {
            logger.info("User not logged in");
        }
    }

    public String delete(String key) throws IOException, NoServerAliveException {
        return communicate(key, Action.delete);
    }

    public boolean isLoggedIn() {
        return currentUser.isPresent();
    }

    public String keyrange() {
        if (ac != null) {
            ac.writeln("keyrange");
            return ac.readLine();
        }
        return null;
    }

    public String keyrange_read() {
        if (ac != null) {
            ac.writeln("keyrange_read");
            var response = ac.readLine();
            if (response != null && response.startsWith("keyrange_read_success")) {
                try {
                    replicaTable.put(connectingServer, ServerSet.parseFromString(response.split(" ")[1]));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            } else {
                logger.severe("keyrange_read not successful");
            }
            return response;
        }
        return null;
    }

    private enum Action {
        delete, put, get, login, subscribe, logout, subscriptions
    }
}
