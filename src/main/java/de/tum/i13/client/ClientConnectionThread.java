package de.tum.i13.client;

import de.tum.i13.shared.datastructure.ActiveConnection;

import java.io.IOException;
import java.net.Socket;

public class ClientConnectionThread implements Runnable {

    Socket socket;

    public ClientConnectionThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try {
            ActiveConnection ac = new ActiveConnection(socket);
            var curr = ac.readLine();
            while (curr != null) {
                System.out.println("KVClient> " + curr);
                curr = ac.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
