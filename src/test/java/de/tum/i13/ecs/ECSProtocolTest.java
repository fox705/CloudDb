package de.tum.i13.ecs;

import de.tum.i13.shared.ECSProtocol;
import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ECSProtocolTest {
    
    @Test
    void updateServer() {
        var metaData = new ServerSet();
        var server = new ServerData(InetAddress.getLoopbackAddress(), 20, new BigInteger("1"),
                new BigInteger("10"));
        var server2 = new ServerData(InetAddress.getLoopbackAddress(), 40, new BigInteger("11"),
                new BigInteger("20"));
        var serverList = new ArrayList<ServerData>();
        serverList.add(server);
        serverList.add(server2);
        metaData.setServerData(serverList);
        assertEquals("update 1,10,127.0.0.1:20;11,20,127.0.0.1:40;",
                (ECSProtocol.updateMetadata(metaData)));
    }
}
