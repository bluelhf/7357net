package blue.lhf.testnet;

public class ConnectionError extends Error {
    public ConnectionError() {
        super();
    }

    public ConnectionError(String message) {
        super(message);
    }

    public ConnectionError(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionError(Throwable cause) {
        super(cause);
    }
}
