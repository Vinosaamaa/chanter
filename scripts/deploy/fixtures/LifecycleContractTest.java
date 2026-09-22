import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class LifecycleContractTest {
    public static void main(String[] args) throws Exception {
        var export = Lifecycle.operation(new String[]{"export", "0", "-"});
        if (!export.path().equals("/api/v1/internal/lifecycle/journal?after=0&limit=500") || export.inputLimit() != 0)
            throw new AssertionError();
        for (String[] invalid : new String[][]{{"http://private.example"}, {"receipt", "extra"}, {"export", "1&host=x", "-"},
                {"export", "9007199254740992", "-"}, {"export", "-1", "-"}}) {
            try { Lifecycle.operation(invalid); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        }
        byte[] bytes = "fixture".getBytes(StandardCharsets.UTF_8);
        if (Lifecycle.bounded(new ByteArrayInputStream(bytes), bytes.length).length != bytes.length) throw new AssertionError();
        try { Lifecycle.bounded(new ByteArrayInputStream(bytes), bytes.length - 1); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        if (Lifecycle.operation(new String[]{"reapply"}).inputLimit() != 256 * 1024
                || Lifecycle.operation(new String[]{"invalidate"}).inputLimit() != 2048) throw new AssertionError();
        System.out.println("Lifecycle fixed-route and byte-bound contract passed");
    }
}
