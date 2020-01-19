package de.tum.i13.client;

import de.tum.i13.shared.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;


class KVClient {
    
    private static final Logger logger = Logger.getLogger(KVClient.class.getName());
    private final ClientManager clientManager;
    
    private KVClient(Config cfg) {
        clientManager = new ClientManager(cfg);
    }
    
    public static void main(String[] args) throws IOException {
        KVClient client;
        if (args.length != 0) {
            var cfg = Config.parseCommandlineArgs(args);
            client = new KVClient(cfg);
        } else {
            client = new KVClient(null);
        }
        
        client.start();
    }

    /**
     * handles communication with user
     *
     * @throws IOException
     */
    private void start() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    
        for (; ; ) {
            System.out.print("KVClient> ");
            String line = reader.readLine();
            if (!line.isEmpty()) {
                String[] command = line.split(" ", 3);
                logger.info("Executing command: " + line);
                switch (command[0]) {
                    case "connect":
                        clientManager.connect(line);
                        break;
                    case "register":
                        clientManager.register(line);
                        break;
                    case "login":
                        clientManager.login(line);
                        break;
                    case "put":
                        clientManager.put(line);
                        break;
                    case "get":
                        clientManager.get(line);
                        break;
                    case "subscribe":
                        clientManager.subscribe(line);
                        break;
                    case "showsubscriptions":
                        clientManager.showSubscriptions(line);
                        break;
                    case "unsubscribe":
                        clientManager.unsubscribe(line);
                        break;
                    case "delete":
                        clientManager.delete(line);
                        break;
                    case "disconnect":
                        clientManager.disconnect();
                        break;
                    case "loglevel":
                        clientManager.changeLogLevel(command);
                        break;
                    case "help":
                        clientManager.printHelp();
                        break;
                    case "quit":
                        clientManager.printConsoleLine("Application exit!");
                        return;
                    case "keyrange":
                        clientManager.keyrange();
                        break;
                    case "keyrange_read":
                        clientManager.keyrange_read();
                        break;
                    default:
                        clientManager.retUnknownCommand();
                }
            }
        }
    }
    // Open app: connect();
    // Register(username, password, openPort); // only first time
    //login (username, openPort);
    // Create serverSocket(openPort)
    // if I already subscribe get notification
    // subscribe(key)

    //when isLogin = true; read();


}
