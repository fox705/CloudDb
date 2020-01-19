package de.tum.i13.client;

public class User {

    String userName;
    String password;
    int port;
    Boolean isLoggin;

    public User(String userName, String password, int port) {
        this.userName = userName;
        this.password = password;
        this.port = port;
        isLoggin = true;
    }


    public void logout() {
        isLoggin = false;
        userName = null;
        password = null;
        port = 0;
    }

    @Override
    public String toString() {

        return userName + " " + password + " " + port;
    }

}
