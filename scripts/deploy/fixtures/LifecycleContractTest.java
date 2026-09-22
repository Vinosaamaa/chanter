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
        String id = "22222222-2222-4222-8222-222222222222";
        String selector = id + "\n1\n11111111-1111-4111-8111-111111111111\n" + "a".repeat(64)
                + "\nCOURSE\n00000000-0000-0000-0000-000000000000\n";
        var scope = Lifecycle.prepare(new String[]{"scope-read"}, selector.getBytes(StandardCharsets.UTF_8));
        if (!scope.operation().path().equals("/api/v1/internal/lifecycle/deleted-study-servers/" + id
                + "/scope?revision=1&eventId=11111111-1111-4111-8111-111111111111&digest=" + "a".repeat(64)
                + "&kind=COURSE&after=00000000-0000-0000-0000-000000000000&limit=256") || scope.body().length != 0)
            throw new AssertionError();
        var imported = Lifecycle.prepare(new String[]{"scope-import"}, (id + "\n{}").getBytes(StandardCharsets.UTF_8));
        if (!imported.operation().path().endsWith("/scope/import") || !new String(imported.body(), StandardCharsets.UTF_8).equals("{}"))
            throw new AssertionError();
        for (String invalid : new String[]{selector.replace("COURSE", "COURSE&host=evil"), selector.replace(id, "../bad"),
                selector + "extra", selector.replace("\n1\n", "\n0\n")}) {
            try { Lifecycle.prepare(new String[]{"scope-read"}, invalid.getBytes(StandardCharsets.UTF_8)); throw new AssertionError(); }
            catch (IllegalArgumentException expected) {}
        }
        System.out.println("Lifecycle fixed-route and byte-bound contract passed");
    }
}
