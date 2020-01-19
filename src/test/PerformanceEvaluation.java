
import de.tum.i13.client.KVStore;
import de.tum.i13.client.NoServerAliveException;

import java.io.*;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * This class is a little bit like PerformanceTest.
 * This class needs to be run after mvn package since it uses its artifact to test. It is an integration test
 * and can not be made into JUnit test. It uses the WHOLE Eron dataSet to test.
 * So Please download it and store the folder "maildir" in "test_resources/eron_dataset".
 * This class runs significantly longer, because it is testing different server configurations.
 * The time and configuration will be stored in a txt file.
 * I recommend to just deal with the saved txt file then running the hole thing.
 */
public class PerformanceEvaluation {
    static final String directory = "generated/performanceEvaluation/";
    static ArrayList<File> DataSet;
    static Process ecsServer;
    static ArrayList<Process> kvServers;
    static ArrayList<ClientThread> clients;
    static File ecsLog = new File(directory + "ecs.log");
    static ArrayList<File> kvLogs;
    static ArrayList<File> clientLogs;
    static final File Evaluation = new File(directory + "evaluation.txt");
    static PrintWriter out;


    public static void main(String[] args) {
        setUp();
        String[] strategy = {"FIFO", "LFU", "LRU"};
        out.println("KvServers;Clients;CacheSize;Strategy;Time;");
        for (String string : strategy) {
            for (int servers = 5; servers <= 50; servers += servers == 25 ? 25 : 5) {
                for (int numberOfClients = 1; numberOfClients <= 100; numberOfClients += numberOfClients == 5 ? 3 : (numberOfClients >= 10 && numberOfClients < 50 ? 10 : (numberOfClients == 50 ? 50 : 2))) {
                    for (int cacheSize = 5; cacheSize <= 1000; cacheSize = cacheSize == 10 ? 25 : (cacheSize == 100 ? 250 : cacheSize * 2)) {
                        int numberOfMeasurements = 1;
                        long time = 0;
                        for (int i = 0; i < numberOfMeasurements; i++) {
                            startUpEverything(servers, numberOfClients, cacheSize, string);
                            long start = System.currentTimeMillis();
                            for (ClientThread t : clients) {
                                try {
                                    t.join();
                                } catch (InterruptedException ignore) {

                                }
                            }
                            // now every client should ne done with its work;
                            time += (System.currentTimeMillis() - start);
                            tearDown();
                        }
                        try {
                            Files.write(Evaluation.toPath(), String.format("%s;%s;%s;%s;%s;\n", servers, numberOfClients, cacheSize, string, (time / numberOfMeasurements)).getBytes(), StandardOpenOption.APPEND);
                        }catch (IOException e) {
                            //exception handling left as an exercise for the reader
                        }
                    }
                }
            }
        }
    }


    static void setUp() {
        var dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("Evaluation.txt"))
                file.delete();
        DataSet = new ArrayList<>();
        kvServers = new ArrayList<>();
        clients = new ArrayList<>();
        kvLogs = new ArrayList<>();
        clientLogs = new ArrayList<>();
        listf("test_resources/eron_dataset/maildir", DataSet);
        try {
            FileWriter fw = new FileWriter(Evaluation.getPath(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            out = new PrintWriter(bw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Number of Mails: " + DataSet.size());
    }

    static void tearDown() {
        out.close();
        ecsServer.destroy();
        for (Process p : kvServers) {
            p.destroy();
        }
        try {
            System.out.println("Waiting 5 sec to destroy thread");
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }
        kvServers.add(ecsServer);
        for (Process p : kvServers) {
            if (p.isAlive()) {
                p.destroyForcibly();
                System.out.println("We have to destroy processes forcibly");
            }
        }
        ecsServer = null;
        kvServers = new ArrayList<>();
        clients = new ArrayList<>();
        kvLogs = new ArrayList<>();
        clientLogs = new ArrayList<>();
    }

    static void startUpEverything(int numberOfServers, int numberOfClients, int cacheSize, String strategy) {
        try {
            ecsLog.createNewFile();
            for (int i = 1; i <= numberOfServers; i++) {
                File tmp = new File(directory + "kvServer" + i + ".log");
                tmp.createNewFile();
                kvLogs.add(i - 1, tmp);
                kvLogs.add(tmp);
            }
            for (int i = 1; i <= numberOfClients; i++) {
                File tmp = new File(directory + "client" + i + ".log");
                tmp.createNewFile();
                clientLogs.add(tmp);
            }
            var ecsCommandString = "java -jar target/ecs-server.jar -l generated/performanceEvaluation/ecs.log -ll ALL -p 6000".split(" ");
            ecsServer = (new ProcessBuilder(ecsCommandString)).
                    redirectOutput(ecsLog).redirectError(ecsLog).start();
            for (int i = 1; i <= numberOfServers; i++) {
                Process tmp = (new ProcessBuilder(("java -jar target/kv-server.jar -l generated/performanceEvaluation/kvServer" + i + ".log -ll ALL -d generated/performanceEvaluation/KvServer" + i + "/ -p " + (6000 + i) +
                        " -b 127.0.0.1:6000 -c " + cacheSize + " -s " + strategy).split(" "))).redirectOutput(kvLogs.get(i - 1)).redirectError(kvLogs.get(i - 1)).start();
                kvServers.add(i - 1, tmp);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignore) {
            }
            int size = DataSet.size() / numberOfClients;
            for (int i = 0; i < numberOfClients; i++) {
                File[] f = new File[size];
                System.arraycopy(DataSet.toArray(), i * size, f, 0, size);
                ClientThread tmp = new ClientThread(new ArrayList<File>(Arrays.asList(f)));
                clients.add(i, tmp);
            }
            for (ClientThread t : clients) {
                t.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void listf(String directoryName, ArrayList<File> files) {
        File directory = new File(directoryName);

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if (fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    listf(file.getAbsolutePath(), files);
                }
            }
    }
}

class ClientThread extends Thread {

    boolean isRunning;
    ArrayList<File> dataSet;
    KVStore kvStore;

    public ClientThread(ArrayList<File> dataSet) {
        this.dataSet = dataSet;
        kvStore = new KVStore();
        try {
            kvStore.setBaseServer("127.0.0.1", 6001);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        isRunning = true;
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        for (File file : dataSet) {
            //biggest file in dataSet is 2 MB, so i don't check the size, shouldn't be to bad
            try {
                String content = Files.readString(file.toPath());
                content = content.replaceAll("\n", "    ").replaceAll("\r", " ");
                // to long key shouldn't be a problem with the server
                var response = kvStore.put(file.getPath().replaceAll("/", ","), content);
                if (response == null)
                    continue;
                while (response.startsWith("server_write_lock")) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    response = kvStore.put(file.getPath().replaceAll("/", ","), content);
                }
                response = kvStore.get(file.getPath().replaceAll("/", ","));
                if (response == null)
                    continue;
            } catch (IOException e) {
                System.out.println("Put file failed");
            } catch (NoServerAliveException e) {
                System.out.println("ALL SERVER DOWN");
                break;
            }
        }
        // second loop to get all key, value pairs again and then delete them
        for (File file : dataSet) {
            try {
                var response = kvStore.get(file.getPath().replaceAll("/", ","));
                if (response == null)
                    continue;
                response = kvStore.delete(file.getPath().replaceAll("/", ","));
                if (response == null)
                    continue;
                while (response.startsWith("server_write_lock")) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    response = kvStore.delete(file.getPath().replaceAll("/", ","));
                }
            } catch (IOException e) {
                System.out.println("Put file failed");
            } catch (NoServerAliveException e) {
                System.out.println("ALL SERVER DOWN");
                break;
            }
        }
    }
}

