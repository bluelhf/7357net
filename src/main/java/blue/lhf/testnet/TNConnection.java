package blue.lhf.testnet;

import blue.lhf.ansi4j.AnsiPrintStream;
import blue.lhf.ansi4j.constants.Erase;

import java.awt.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static blue.lhf.ansi4j.constants.Colour.AQUA;

public class TNConnection extends Connection {

    private static final Pattern cursorResponsePattern = Pattern.compile(".*?\u001b\\[([0-9]+);([0-9]+)R.*");
    private AnsiPrintStream ansiOut;

    public TNConnection(ServerSocket server, Socket client) {
        super(server, client);
    }

    private CompletableFuture<String> cursorResponse;

    @Override
    protected void begin() {
        ansiOut = new AnsiPrintStream(output, true);
        String block = """
                 __________ ___________           __\r
                /__  /__  // ____/__  /___  ___  / /_\r
                  / / /_ </___ \\   / / __ \\/ _ \\/ __/\r
                 / /___/ /___/ /  / / / / /  __/ /_ \r
                /_//____/_____/  /_/_/ /_/\\___/\\__/ \r
                """;

        int maxLength = Arrays.stream(block.split("\n")).mapToInt(String::length).max().orElse(0);
        CompletableFuture<Void> printFuture = new CompletableFuture<>();
        cursorResponse = new CompletableFuture<String>().completeOnTimeout("\u001B[0;0R", 5, TimeUnit.SECONDS);
        ansiOut.saveCursorPosition();
        ansiOut.setCursor(999_999, 999_999);
        ansiOut.append("\u001B[6n").restoreCursorPosition().flush();

        cursorResponse.thenApplyAsync(response -> {
            printFuture.join();
            Matcher matcher = cursorResponsePattern.matcher(response);
            boolean found = matcher.find();

            assert found : "Returned cursor position did not match regular expression";

            return new Point(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        }).thenAcceptAsync(max -> {
            if (max.x > maxLength) {
                ansiOut.fg(AQUA).append(block).reset().println();
            }

            ansiOut
                    .append("Welcome to the ").fg(AQUA).append("7357net").reset().append(" telnet server!").append(NL)
                    .append("We are receiving you loud and clear on port ").fg(AQUA).append(server.getLocalPort()).reset().append(NL)
                    .append("Your current IP address is: ").fg(AQUA).append(client.getInetAddress().getHostAddress()).reset().append(NL)
                    .append(NL)
                    .append("Feel free to enter a command below.").append(NL)
                    .append("> ").flush();
            ansiOut.writeBytes(new byte[]{});
        });
        new Thread(() -> {
            ansiOut.writeBytes(new byte[]{});
            printFuture.complete(null);
        }).start();

    }

    @Override
    protected void end() {
        if (closed) return;
        ansiOut.eraseScreen(Erase.ALL).writeBytes(new byte[]{});
    }

    private StringBuilder responseBuilder = new StringBuilder();
    @Override
    protected boolean receive(char ch) {
        if (cursorResponse != null) {
            responseBuilder.append(ch);
            if (ch == 'R') {
                cursorResponse.complete(responseBuilder.toString());
                cursorResponse = null;
                responseBuilder = new StringBuilder();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void receive(String line) {
        //TODO(ilari): Write a library for handling commands and use it here
        if (line.startsWith("echo ")) send(line.substring("echo ".length()));
        if (line.startsWith("quit") || line.startsWith("exit")) {
            close();
        }
        send("\r\n> ");
    }
}
