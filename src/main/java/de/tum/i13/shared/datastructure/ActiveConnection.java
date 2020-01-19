package de.tum.i13.shared.datastructure;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Logger;

public class ActiveConnection implements AutoCloseable {
    private final Logger logger = Logger.getLogger(ActiveConnection.class.getName());
    public Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    
    public ActiveConnection(Socket socket, PrintWriter output, BufferedReader input) {
        this.socket = socket;
    
        this.output = output;
        this.input = input;
    }
    
    public ActiveConnection(ServerData s) throws IOException {
        this(s.getServerIp(), s.getClientPort());
    }
    
    public ActiveConnection(InetAddress addr, int port) throws IOException {
        try {
            this.socket = new Socket(addr, port);
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            //Connect uncessfully, we can try gain one more time in 2 sec
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            this.socket = new Socket(addr, port);
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        
    }
    
    public ActiveConnection(Socket socket) throws IOException {
        this.socket = socket;
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }
    
    
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public void writeln(String command) {
        synchronized (this.output) {
            output.write(command + "\r\n");
            output.flush();
        }
    }
    
    
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public String readLine() {
        synchronized (this.input) {
            String response;
            try {
                response = input.readLine();
            } catch (IOException e) {
                logger.severe(e.getMessage());
                return null;
            }
            return response;
        }
    }
    
    public void close() {
        try {
            socket.close(); //should also close input and output stream
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public InetAddress getAddress() {
        return socket.getInetAddress();
    }
    
    public String getInfo() {
        return this.socket.getRemoteSocketAddress().toString();
    }
    
    @Override
    public String toString() {
        return getInfo();
    }
}
