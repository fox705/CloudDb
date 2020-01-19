package de.tum.i13.ecs;

import de.tum.i13.shared.datastructure.ConsistentHashing;
import de.tum.i13.shared.datastructure.ServerData;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Pingservice class uses the isReachable function, which uses the TCP protocol for ping messages
 */
class PingService implements Runnable {
    private static final Logger logger = Logger.getLogger(PingService.class.getName());
    private final List<ServerActiveConnection> aliveConnections;
    private final ConsistentHashing hashing;
    private boolean Running;
    private ScheduledExecutorService executor;
    private final Map<ServerActiveConnection, Future> active = new HashMap<>();
    private final Map<ServerActiveConnection, Future> inactive = new HashMap<>();
    
    public PingService(List<ServerActiveConnection> aliveConnections, ConsistentHashing hashing) {
        this.aliveConnections = aliveConnections;
        this.hashing = hashing;
    }
    
    @Override
    public void run() {
        setRunning(true);
        executor = Executors.newScheduledThreadPool(10);
        aliveConnections.stream().parallel().forEach(
                connection -> executor.scheduleAtFixedRate(
                        () -> ping(connection), 50, 1000, TimeUnit.MILLISECONDS
                )
        );
        while (isRunning()) {
            try {
                synchronized (inactive) {
                    inactive.wait();
                }
            } catch (InterruptedException e) {
                logger.severe("Error while waiting: " + e.getMessage());
            }
            for (Map.Entry<ServerActiveConnection, Future> entry : inactive.entrySet()) {
                inactive.remove(entry.getKey());
                entry.getValue().cancel(true);
            }
        }
    }
    
    private void ping(ServerActiveConnection activeConnection) {
        boolean isReachable;
        try {
            isReachable = activeConnection.getAddress().isReachable(700);
        } catch (IOException e) {
            isReachable = false;
        }
        if (!isReachable) {
            hashing.removeNode(new ServerData(activeConnection.socket.getInetAddress(),
                    activeConnection.getClientPort(), activeConnection.socket.getPort()));
            aliveConnections.remove(activeConnection);
            activeConnection.close();
            inactive.put(activeConnection, active.get(activeConnection));
            active.remove(activeConnection);
            inactive.notifyAll();
        }
    }
    
    private boolean isRunning() {
        return Running;
    }
    
    public void setRunning(boolean running) {
        Running = running;
    }
    
    public void addConnection(ServerActiveConnection activeConnection) {
        active.put(activeConnection, executor.scheduleAtFixedRate(
                () -> ping(activeConnection), 50, 1000, TimeUnit.MILLISECONDS
        ));
    }
}
