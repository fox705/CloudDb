package de.tum.i13.client;

import de.tum.i13.shared.Config;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ClientManagerTest {
    
    static final ByteArrayOutputStream out = new ByteArrayOutputStream();
    static final PrintStream orgOut = System.out;
    
    final ClientManager cm = new ClientManager(new Config());
    
    static void setup() {
        System.setOut(new PrintStream(out));
    }
    
    static void restoreStream() {
        out.reset();
        System.setOut(orgOut);
    }


    @Test
    void toBigKey() {
        try {
            String com = "put Vay_Too_Big_Key_(_over_20_Bytes_) testValue";
            setup();
            cm.put(com);
            assertTrue(out.toString().contains("KVClient> Error! To long key String. key_MAX: 20 Bytes"));
        } catch (Exception e) {
            e.printStackTrace();
            fail("toBigKey fail");
        } finally {
            restoreStream();
        }
    }
    
    @Test
    void toBigData() {
        try {
            var value = new StringBuilder();
            var sc = new Scanner(new File("test_resources/files/Over_120_KByte"));
            value.append(sc.nextLine());
            while (sc.hasNextLine()) {
                value.append("\n").append(sc.nextLine());
            }
            sc.close();
            String com = "put TheKey " + value.toString();
            setup();
            cm.put(com);
            assertTrue(out.toString().contains("KVClient> Error! To long value String. value_MAX: 120 KBytes")); //for Windows
        } catch (Exception e) {
            e.printStackTrace();
            fail("toBigData fail");
        } finally {
            restoreStream();
        }
    }

}
