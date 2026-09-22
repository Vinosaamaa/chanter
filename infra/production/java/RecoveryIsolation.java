import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Fixed private-network probe. A failed helper execution never proves isolation. */
public final class RecoveryIsolation {
    public static void main(String[] args) {
        try {
            if (args.length != 0 || !"true".equals(System.getenv("CHANTER_RECOVERY_MODE"))) throw new IllegalStateException();
            for (String source : new String[] {"auth", "community", "message", "media", "agent", "notification", "search"}) {
                var address = InetAddress.getByName(source + "-service");
                try (var socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, 8080), 1500);
                    throw new IllegalStateException();
                } catch (ConnectException expected) {
                    // DNS resolved and TCP was actively refused. Timeout, DNS and execution failures reject.
                }
            }
            System.out.println("RECOVERY_SOURCE_LISTENERS_PRIVATE");
        } catch (Exception ignored) {
            System.err.println("Recovery isolation check failed");
            System.exit(1);
        }
    }
}
