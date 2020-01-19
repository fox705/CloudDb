package de.tum.i13.client;

public class DisconnectException extends Exception {
    @Override
    public String toString() {
        return "You have to type connect first!";
    }
}
