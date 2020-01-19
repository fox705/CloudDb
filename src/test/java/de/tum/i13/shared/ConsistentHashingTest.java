package de.tum.i13.shared;

import de.tum.i13.shared.datastructure.ConsistentHashing;
import de.tum.i13.shared.datastructure.ServerData;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsistentHashingTest {
    private final ServerData serverA = new ServerData(InetAddress.getLoopbackAddress(), 20);
    private final ServerData serverB = new ServerData(InetAddress.getLoopbackAddress(), 40);
    private final ServerData serverC = new ServerData(InetAddress.getLoopbackAddress(), 60);
    
    private final Hash a =
            new Hash(serverA.getServerIp().getHostAddress() + ":" + serverA.getClientPort());
    private final Hash b =
            new Hash(serverB.getServerIp().getHostAddress() + ":" + serverB.getClientPort());
    // --Commented out by Inspection (25. Nov.. 2019 07:29):Hash c = new Hash(serverC.getServerIp
    // ().getHostAddress() + ":" + serverC.getClientPort());
    // c<a<b
    
    @Test
    void testHash(){
        assertEquals("127.0.0.1:20",
                serverA.getServerIp().getHostAddress() + ":" + serverA.getClientPort());
        assertEquals(a.toString(),"39c43044d882b1e29485be46a9d916b6");
        assertEquals(b.toString(), "b01c33cbc9bb1bd2e89ec30099039dfe");
    }

    @Test
    void addNode() {
        var hashing = new ConsistentHashing();
        hashing.addNode(serverA);
        assertEquals(new BigInteger("0"), serverA.getFirstHash());
        assertEquals("ffffffffffffffffffffffffffffffff", serverA.getLastHash().toString(16));
        assertEquals(serverA,hashing.addNode(serverB));
        assertEquals(new BigInteger("0"),serverA.getFirstHash());
        assertEquals(b.md5Value.subtract(new BigInteger("1")), serverA.getLastHash());
        assertEquals(b.md5Value, serverB.getFirstHash());
        assertEquals(Hash.getMaxHash().md5Value, serverB.getLastHash());
    }

    @Test
    void removeNode() {
        var hashing = new ConsistentHashing();
        assertNull(hashing.addNode(serverB));
        assertEquals(serverB, hashing.addNode(serverC));
        assertEquals(serverC, hashing.addNode(serverA));
        assertEquals(serverA, hashing.removeNode(serverB));
        assertEquals(serverC, hashing.removeNode(serverA));
        assertNull(hashing.removeNode(serverC));
    }
}
