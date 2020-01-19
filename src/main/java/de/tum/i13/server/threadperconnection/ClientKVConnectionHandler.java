package de.tum.i13.server.threadperconnection;

import de.tum.i13.shared.datastructure.ActiveConnection;

class ClientKVConnectionHandler {
    private final ConnectionThread connectionThread;
    private final ActiveConnection ac;
    private String line;
    
    public ClientKVConnectionHandler(ConnectionThread connectionThread, String line,
                                     ActiveConnection ac) {
        this.connectionThread = connectionThread;
        this.line = line;
        this.ac = ac;
    }
    
    void handle() {
        var logger = KVServer.logger;
        var cp = connectionThread.kv.getLogic();
        var kv = connectionThread.kv;
        do {
            if (line.length() <= 20) {
                logger.info("message from " + ac.getInfo() + " received (abbreviated): " + line);
            } else {
                logger.info("message from " + ac.getInfo() + " received (abbreviated): " + line.substring(0, 20));
            }
            String res;
            if (connectionThread.kv.getServersMetaData() == null) {
                //Server just connected to ECS and in balancing period
                res = "server_stopped";
            } else if (line.startsWith("put") && kv.isWriteLock()) {
                res = "server_write_lock";
            } else {
                //main processing happens here
                res = cp.process(line);
            }
            logger.info("sending message to " + ac.getInfo() + " : " + res);
            ac.writeln(res);
            line = ac.readLine();
        } while (line != null);
        logger.info("closed one client connection");
        ac.close();
    }
}
