package de.tum.i13.client;

import java.io.IOException;
import java.net.ServerSocket;

public class ClientListenerThread implements Runnable {

    private int port;
    private KVStore kvStore;
    private ServerSocket listener;

    public ClientListenerThread(KVStore kvStore, ServerSocket listener){
        this.kvStore = kvStore;
        this.listener = listener;
        this.port = listener.getLocalPort();
    }


    @Override
    public void run() {
        try {
            while (kvStore.isLoggedIn()) {
                var connectionSocket=listener.accept();
                var th = new Thread(new ClientConnectionThread(connectionSocket));
                th.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        //when u are done

    }
}
