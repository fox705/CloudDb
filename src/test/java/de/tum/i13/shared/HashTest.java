package de.tum.i13.shared;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HashTest {
    
    @Test
    void compare() {
        var num1 = (new Hash("294")).md5Value;
        var num2 = new BigInteger("0", 16);
        var num3 = new BigInteger("9EA8E23514F23098F701DF33805EC118", 16);
        assertFalse(num1.compareTo(num2) < 0 || num1.compareTo(num3) > 0);
    }
}