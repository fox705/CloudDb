package de.tum.i13.ecs;

import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.Hash;
import de.tum.i13.shared.datastructure.ConcurrentWaitItem;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ReplicationService implements Runnable {
    public static final int replicationFactor = 2;
    /**
     * Map Server to a list of its replica. This list also includes itself
     */
    public static final Map<ServerData, ServerSet> replicaTable = new HashMap<>();
    public final ConcurrentWaitItem<ServerSet> metaDataSnapshot;
    final ECSServer ecs;
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    
    public ReplicationService(ECSServer ecs) {
        this.ecs = ecs;
        metaDataSnapshot = new ConcurrentWaitItem<>(ecs.hashing);
    }
    
    @Override
    public void run() {
        do {
            var currentRing = ecs.hashing;
            /*
             Synchronize on the hashing, preventing any server from adding or removing at this time
             */
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (currentRing) {
                /*
                The ConcurrentWaitItem will handle all the wait and notify logic. When we pop, we should get a snapshot different from the previous one
                We need this because we need to know if the ring has changed or not. If it has not changed, then we wait.
                Every time the pop() return, we are guaranteed to have a new snapshot
                */
                logger.info("Might wait for the next ring snap shot");
                var currentRingSnapshot = metaDataSnapshot.pop();
                logger.info("Current ring snapshot is " + currentRingSnapshot);
                if (currentRingSnapshot.getServerData().size() <= replicationFactor) {
                    logger.info("The ring currently has too few elements for replication");
                /*
                We still update the replica data because this might be the result of going from >repFactor server to <rep
                factor server.
                In this case the replicaData to send is the serverData itself (only one server which is itself is replica)
                */
                    for (var s : ecs.activeConnections) {
                        var replicaData = new ServerSet();
                        replicaData.addServer(s.getServerData());
                        s.writeln(ECSProtocol.updateReplicaMetadata(replicaData));
                    }
                    continue;
                }
                //recompute the entire replica structure
                replicaTable.clear();
                logger.info("Computing new replica data for each server");
                for (Hash h : currentRing.nodeMap.keySet()) {
                    var nextHash = h;
                    var mainServer = currentRing.nodeMap.get(h);
                    //Build the replica set coressponding to this server
                    var replicaSet = new ServerSet();
                    replicaSet.addServer(mainServer);
                    for (int i = 0; i < replicationFactor; i++) {
                        nextHash = currentRing.getNextServerHashFrom(nextHash.add(1)); // add 1 otherwise it just returns the exact same hash
                        logger.info(String.format("The next hash from the ring correspond to %s is: %s", h, nextHash));
                        if (nextHash == null) {
                            //This happens because the hash is at the end of the ring, we perform a wrap around
                            nextHash = currentRing.getFirstHash();
                        }
                        assert !nextHash.equals(h); //Due to the loop condition guarantee, we should never comeback to the original hash
                        replicaSet.addServer(currentRing.nodeMap.get(nextHash));
                    }
                    logger.info(String.format("Putting %s with replica set %s into replica table", mainServer, replicaSet));
                    replicaTable.put(mainServer, replicaSet);
                }
                for (var s : ecs.activeConnections) {
                    logger.info("The server data correspond to s is: " + s.getServerData());
                    var replicaData = replicaTable.get(s.getServerData());
                    if (replicaData == null) {
                        //It means that this server just connected to the ECS but haven't finished the adding procedure, but it is still presents in activeConnections
                        logger.info("Phantom server");
                        continue;
                    }
                    s.writeln(ECSProtocol.updateReplicaMetadata(replicaData));
                }
            }
        } while (ecs.isRunning());
        logger.info("Replica service shutdown");
    }
}
