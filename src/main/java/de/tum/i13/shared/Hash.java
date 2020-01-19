package de.tum.i13.shared;

import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public class Hash implements Comparable<Hash> {
    public BigInteger md5Value;
    private byte[] md5Hash;

    public Hash(String data) {
        try {
            md5Hash =
                    MessageDigest.getInstance("MD5").digest(data.getBytes(Constants.TELNET_ENCODING));
            md5Value = new BigInteger(1, md5Hash);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
    
    private Hash(BigInteger md5Value) {
        this.md5Value = md5Value;
    }
    
    private Hash(byte[] md5) {
        if (md5.length != 16)
            throw new RuntimeException("False hash");
        md5Value = new BigInteger(1, md5);
        md5Hash = md5;
    }

    public static Hash getMaxHash() {
        var h = new byte[16];
        Arrays.fill(h, (byte) 0xFF);
        return new Hash(h);
    }

    @Override
    public int compareTo(@NotNull Hash o) {
        return md5Value.compareTo(o.md5Value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hash)) return false;
        Hash hash = (Hash) o;
        return md5Value.equals(hash.md5Value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(md5Value);
    }

    @Override
    public String toString() {
        return md5Value.toString(16);
    }
    
    public Hash sub(int i) {
        return new Hash(md5Value.subtract(new BigInteger(i + "")));
    }
    
    public Hash add(int i) {
        return new Hash(md5Value.add(new BigInteger(i + "")));
    }
}
