# Build Instruction

**mvn package** to build the project.  
**mvn site** to build and to recreate the documentation  
Test results located under **target/site/surefire-report.html**. Pdf Report (precompiled) is located under project root  

- The performance test should only be executed after building with mvn package, because it depends on the completed jar in the folder generated/jar.
- Test might fail if the generated folder is not empty (as in put_success or put_update server return, as we use a file system to store data)
- The cleanUp.sh cleans all the generated logs and delete the generated folder

# Assumptions

- Values don't not contain "\n", as it messes with the readLine() function.
- KV connection to others are always successful if they are all on the ring from the ECS perspective.
- You use our KVStore class to build your own client, if it is the case
- You only start the KVServers **after** the ECS server is online
- You dont close the the ECS while the KVs are online
- You need to give the client a base server to work with (via connect)
- You dont close the base server without letting the client have a chance to send keyrange to it (by sending put,get,delete)
to this server
- The replica factor is always 2. This is trivially configurable in the source code, as we don't see any command line argument
for it in the assignment

# Database structure

Files based. Keys are file name that end with .txt. Values are file content. Replica keys are like normal except they end with
.txt.replica

# Consistent hashing

We use a line structure instead of ring for easier implementation of comparision between hashes (to determine hash range).
The semantic does not change much. The wrap around behavior of searching for next replica servers are the same (just check if we are at the last line or not).

# Replica service

- The ECS is the one who decides what is a replica of what. It decides once (it means that once it decides that B and C are replicas of A,
and B and C are still alive on the ring, it assumes A,B and C can communicate with each other and does not look for the next servers).
- ECS send `replica update [serverReplicaData]` to each server. This happens anytime there is a change in the ring structure.
- The KV server handles the transfer of data solely between themselves. The replica data on replica server does not delete if the main server die.

# ECS-Server Protocol description

**A new server connects to ECS**
1. KV Server A connects to ECS
2. ECS reply with `ECS regconized you \n`
3. KV Server A sends ECS `add [server A port to client] \n`
4. ECS reply with `invoke recieve from [B serverData] \n`, where B is the server that stores the old data
5. KV Server A waits to recieve data from the other server
6. ECS at the same time sends `invoke transfer to [A serverData] \n` to server B
7. Server B begins to transfer data to server A, and at the same time locks write.
8. ECS waits for the confirm message (`confirm transfer\n`) from both servers
9. ECS sends `update [string representation of metadata] \n` to all servers (inlcuding A and B) after recieving the confirm message
 
**Server A wants to be removed from the ECS**
1. KV server A sends `remove [port] \n` to ECS (ECS knows the address of server from the socket)
2. ECS reply with `invoke transfer to [B serverData] \n`, where B is the server to send the data to
3. A sends data to B and sends back ECS a confirm message ("confirm transfer\n)
4. ECS sends A the message `invoke recieve from [A serverData]`
5. A recieves data from B and sends back the confirm message
6. ECS broadcasts the update to all servers  `update [string representation of metadata \n` after recieving the 2 confirm messages
7. ECS send `shutdown` message to A, A then begins the shutdown procedure

# KV-KV Protocol description

The server who receives `invoke receive from` from ECS is the one who connects to the other server.

The server who receives `invoke transfer to` from ECS is the one who listens for the other server (on the same port it uses to listen for client)

KV always sends a welcome message so both the other KV server and client needs to handle this message first. Suppose A
wants to send data to B as the ECS demands it:

1. A connects to B
2. B sends confirmation message `this is KV...`
3. A reads it, sends back the same welcome message
4. B reads it, and then begin transfering data (By repeatedly sending `put key value\n`) like a client
5. A reads it line by line
6. B sends `confirm end transfer\n`
7. A sends `confirm end transfer\n` (So we have 2 way handshake)
8. Both now closes connection

# Client usage instruction

Client needs a base server to first receive metadata from. This is achieved via the command

`connect ipAddress port`

Any other action before that results in error. Sometimes the metadata becomes stale with no actual server
alive, you have to type connect again

Behind it, the client does not actually connect to the server but just adds it to the metadata (changed now with the newest
version, it actually connects to the server to execute the keyrange and keyrange_read commands, but the get,put, delete logic works the same). The actually connection
only happens with put, get or delete

Value of get is not displayed explicitly but u can see it from the log

By typing:

`disconnect`

you reset your metadata to null and have to type connect again (to set base server)

#Known issues

- Log from client may appear out of line with KVClient> . This is concurrency issue with log threads
of Java we are unable to fix because we are forced to use no library

# Miscellaneous

- We use shutdown hook to make KVServer sends shutdown message to ECS, but this only works when
the process is not killed with SIGKILL on Linux. There is no work around.

- If you use IntelliJ, use the exit button in the Run windows, not the Stop button, as it will send SIGKILL

- Java default log manager can't work with Shutdown hook probably, we dont use that in crucial highly
concurrent code but use System.Print instead for debugging.

# Notice to teammember


In between these steps a KV server can recieve `update <Metadata>` from other new servers connecting / disconnecting at anytime.
