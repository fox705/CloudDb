package de.tum.i13.client;

import de.tum.i13.shared.Config;
import de.tum.i13.shared.LogSetup;
import de.tum.i13.shared.LogeLevelChange;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 *Class which contains methods library for Client-side application
 */
class ClientManager {

    private static final Logger logger = Logger.getLogger(ClientManager.class.getName());
    private KVStore kvStore;

    public ClientManager(Config cfg) {
        if (cfg == null)
            return;
        try {
            kvStore = new KVStore();
            kvStore.setBaseServer(cfg.listenaddr, cfg.port);
        } catch (UnknownHostException e) {
            logger.severe("False ip argument");
        }
    }

    /**
     * changes the LogLoevel
     *
     * @param command
     */
    void changeLogLevel(String[] command) {
        if (command.length != 2) {
            retUnknownCommand();
            return;
        }
        try {
            Level level = Level.parse(command[1]);
            LogeLevelChange logeLevelChange = LogSetup.changeLoglevel(level);
            logger.info(String.format("loglevel changed from: %s to: %s",
                    logeLevelChange.getPreviousLevel(), logeLevelChange.getNewLevel()));

        } catch (IllegalArgumentException ex) {
            printConsoleLine("Unknown loglevel");
        }

    }

    /**
     * print help for User
     */
    void printHelp() {
        System.out.println("Available commands:");
        System.out.println("connect <address> <port> - Tries to establish a TCP- connection to the echo server based on the given server address and the port number of the echo service.");
        System.out.println("disconnect - Tries to disconnect from the connected server.");
        System.out.println("put <key> <value> - Sends a put request to the connected server according to the communication protocol.");
        System.out.println("delete <key>  - Sends a delete request to the connected server according to the communication protocol.");
        System.out.println("                    When properly used it stores the <value> on the server bind to <key>. This may update old data. If <value> ist empty the key will be deleted.");
        System.out.println("get <key> - Sends a get request to the connected server according to the communication protocol.");
        System.out.println("                    When properly used the stored value bind to <key> is going to be displayed.");
        System.out.println(String.format("loglevel <level> - Sets the logger to the specified log level (%s FINE | INFO | WARN | ERROR | OFF)", Level.ALL.getName()));
        System.out.println("help - Display this help");
        System.out.println("quit - Tears down the active connection to the server and exits the program execution.");
    }

    void retUnknownCommand() {
        printConsoleLine("Unknown command. Try 'help' to show commands.");
    }

    /**
     * prints KVClient> in front of the line
     *
     * @param msg
     */
    void printConsoleLine(String msg) {
        System.out.println("KVClient> " + msg);
    }

    /**
     * process String to IP:Port
     * connect to Server
     *
     * @param line
     */
    void connect(String line) {
        var command = line.split(" ");
        try {
            var address = InetAddress.getByName(command[1]);
            var port = Integer.parseInt(command[2]);
            var rep = kvStore.connect(address.toString().substring(1), port); //substring 1 because of the string form of inetaddress has / at the start
            printConsoleLine(rep);
        } catch (UnknownHostException e) {
            printConsoleLine("Unknown host, try connect again");
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.severe("Malformed command");
        } catch (IOException e) {
            printConsoleLine("Could not connect to the server");
        }
    }

    public void register(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length != 3) {
            printConsoleLine("Wrong number of arguments");
        } else {
            try {
                kvStore.register(command[1], command[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * login user to the application
     *
     * @param line line syntax: login <userName> <password>
     */
    void login(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length != 3) {
            printConsoleLine("Wrong number of arguments");
        } else {
            try {
                kvStore.login(command[1], command[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * put value on Server
     *
     * @param line syntax: logout
     */
    void logout(String line) {
        var command = line.split(" ");
        if (command.length == 1) {
            try {
                kvStore.logout();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands.");
        }
    }

    void disconnect() {
        try {
            kvStore.disconnect();
        } catch (DisconnectException e) {
            printConsoleLine(e.toString());
        }
    }

    /**
     * put value on Server
     *
     * @param line
     */
    void put(String line) {
        var command = line.split(" ", 3);
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length < 2) {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        } else if (command[1].getBytes().length > 20) {
            printConsoleLine("Error! To long key String. key_MAX: 20 Bytes");
        } else if (command.length == 2) {
            delete(line);
        } else if (command[2].getBytes().length > 120000) {
            printConsoleLine("Error! To long value String. value_MAX: 120 KBytes");
        } else {
            try {
                var response = kvStore.put(command[1], command[2]);
                // printConsoleLine("Value string to be put is :" + valueString);
                printConsoleLine("server response: " + response);
            } catch (IOException e) {
                logger.severe("Exception while putting");
            } catch (NoServerAliveException e) {
                printConsoleLine("All server data is stale. You need to type disconnect then " +
                        "connect again to one of server you know");
            }
        }

    }

    /**
     * delete from Server
     *
     * @param line
     */
    void delete(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length < 2) {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        } else if (command[1].getBytes().length > 20) {
            printConsoleLine("Error! To long key String. key_MAX: 20 Bytes");
        } else {
            try {
                var response = kvStore.delete(command[1]);
                printConsoleLine("server response : " + response);
            } catch (IOException e) {
                logger.severe("Exception while putting");
            } catch (NoServerAliveException e) {
                printConsoleLine("All server data is stale. You need to type disconnect then " +
                        "connect again to one of server you know");
            }
        }

    }

    /**
     * get value from Server
     *
     * @param line
     */
    void get(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length < 2) {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        } else if (command[1].getBytes().length > 20) {
            printConsoleLine("Error! To long key String. key_MAX: 20 Bytes");
        } else {
            try {
                var response = kvStore.get(command[1]);
                if (response != null) printConsoleLine(response);
            } catch (IOException e) {
                logger.severe("Exception while getting");
            } catch (NoServerAliveException e) {
                printConsoleLine("All server data is stale. You need to type disconnect then " +
                        "connect again to one of server you know");
            }
        }
    }

    /**
     * subscribe key from Server
     *
     * @param line line syntax: subscribe <key>
     */
    void subscribe(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length != 2) {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        } else {
            try {
                kvStore.subscribe(command[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void unsubscribe(String line) {
        var command = line.split(" ");
        if (kvStore == null) {
            printConsoleLine("Must know at least one KVServer first. Type: connect <serverIp> " +
                    "<port> to initialize");
        } else if (command.length != 2) {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        } else {
            try {
                kvStore.unsubscribe(command[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    void showSubscriptions(String line) {
        var command = line.split(" ");
        if (command[0].equals("showsubscriptions")) {
            try {
                kvStore.showSubscriptions();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            printConsoleLine("Error! Wrong input. Type 'help' to see commands");
        }

    }

    /**
     * print HashKeyRange for current server
     */
    void keyrange() {
        printConsoleLine("Server response: " + kvStore.keyrange());
    }

    void keyrange_read() {
        printConsoleLine("Server response: " + kvStore.keyrange_read());
    }
}
