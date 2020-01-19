package de.tum.i13.shared;

import de.tum.i13.shared.datastructure.ServerData;
import de.tum.i13.shared.datastructure.ServerSet;

public class ECSProtocol {
    //Communication of server to ECS
    public static String addServer(int port) {
        return String.format("add %d", port);
    }

    public static String removeServer(int port) {
        return String.format("remove %d", port);
    }

    //Communication of ECS to server
    public static String updateMetadata(ServerSet data) {
        return String.format("update %s", data.toString());
    }

    public static String invokeTransferTo(ServerData s) {
        return String.format("invoke transfer to %s", s.toString());
    }

    public static String invokeReceiveFrom(ServerData s) {
        return String.format("invoke receive from %s", s.toString());
    }

    //Communication of ECS to server regarding the replicas
    public static String updateReplicaMetadata(ServerSet data) {
        return String.format("replica update %s", data.toString());
    }

    //Communication between server and ECS regarding the subscription service
    public static String login(String user, String password) {
        return String.format("login %s %s", user, password);
    }

    public static String logout(String user, String password) {
        return String.format("logout %s %s", user, password);
    }

    public static String register(String user, String password, String ipAddress, int port) {
        return String.format("register %s %s %s %s", user, password, ipAddress, port);
    }

    public static String update(String user, String password, String ipAddress, int port) {
        return String.format("login %s %s %s %s", user, password, ipAddress, port);
    }

    public static String subscribe(String user, String password, String key) {
        return String.format("login %s %s %s", user, password, key);
    }

    public static String unsubscribe(String user, String password, String key) {
        return String.format("login %s %s %s", user, password, key);
    }

    public static String success() {
        return "subscribe success!";
    }

    public static String unauthorised() {
        return "subscribe unauthorised!";
    }

    public static String error(){
        return "subscribe error!";
    }

    public static String updateSubscriptions(String command) {
        return String.format("update subscriptions %s", command);
    }
}
