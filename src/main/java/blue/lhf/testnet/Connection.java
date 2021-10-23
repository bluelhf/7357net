package blue.lhf.testnet;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

public abstract class Connection extends Thread implements Closeable {
    protected ServerSocket server;
    protected Socket client;

    protected boolean closed = false;

    protected final InputStream input;
    protected final OutputStream output;

    public static Thread factory(int port, BiConsumer<ServerSocket, Socket> connector) {
        Thread thread = new Thread(() -> {
            try {
                ServerSocket server = new ServerSocket(port);
                while (!server.isClosed()) {
                    connector.accept(server, server.accept());
                }
            } catch (IOException e) {
                throw new ConnectionError(e);
            }
        });
        thread.setUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
        });
        thread.start();
        return thread;
    }

    public Connection(ServerSocket server, Socket client) {
        this.server = server;
        this.client = client;

        try {
            input = client.getInputStream();
            output = client.getOutputStream();
        } catch (IOException e) {
            throw new ConnectionError(e);
        }

        begin();
    }

    protected StringBuffer lineBuffer = new StringBuffer();
    protected final void handle(char ch) {
        if (ch == '') {
            send((byte) 0x20, (byte) 0x08);
            if (lineBuffer.length() <= 0) {
                send((byte) 0x20);
            } else lineBuffer.setLength(lineBuffer.length() - 1);
            return;
        }

        if (ch != '\n' && receive(ch)) lineBuffer.append(ch);
        if (ch == '\n') {
            receive(lineBuffer.toString());
            lineBuffer.setLength(0);
        }
    }

    protected abstract void begin();
    protected abstract void end();
    protected abstract boolean receive(char ch);
    protected abstract void receive(String line);

    protected final void send(byte... data) {
        if (closed) return;
        try {
            output.write(data);
        } catch (IOException e) {
            throw new ConnectionError(e);
        }
    }

    protected static final String NL = "\r\n";
    protected final void nl() {
        send(NL);
    }

    protected final void send(String text) {
        send(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        int read;
        try {
            while (!closed && (read = input.read()) != -1) {
                handle((char) read);
            }
        } catch (IOException ex) {
            throw new ConnectionError(ex);
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        end();
        closed = true;
        try {
            client.shutdownOutput();
            client.shutdownInput();
            client.close();
        } catch (IOException e) {
            LockSupport.parkNanos((long) 0.5E9);
            if (!client.isClosed()) close();
        }
    }
}
