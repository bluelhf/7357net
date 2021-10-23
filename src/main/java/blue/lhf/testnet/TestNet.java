package blue.lhf.testnet;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class TestNet {
    protected static List<TNConnection> connections = new ArrayList<>();
    protected static Map<Thread, TNConnection> threadMap = new HashMap<>();

    public static void main(String[] args) {
        AtomicReference<Thread> thread = new AtomicReference<>();
        thread.set(Connection.factory(7357, (server, socket) -> {
            threadMap.put(thread.get(), connect(server, socket));
        }));
    }

    protected static TNConnection connect(ServerSocket server, Socket socket) {
        TNConnection connection = new TNConnection(server, socket);
        connection.start();
        connections.add(connection);
        return connection;
    }
}
