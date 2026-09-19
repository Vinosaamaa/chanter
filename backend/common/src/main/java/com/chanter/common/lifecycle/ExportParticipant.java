package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import java.time.Clock;

/** Atomic source capture/cancellation and receipt, using the existing consumer and outbox. */
public final class ExportParticipant {
    private final String source;
    private final ExportSnapshotStore snapshots;
    private final AccountExportProjection projection;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final AccountExportProtocol protocol;
    private final Clock clock;

    public ExportParticipant(String source, ExportSnapshotStore snapshots, AccountExportProjection projection,
            DurableConsumer consumer, DurableOutbox outbox, AccountExportProtocol protocol, Clock clock) {
        if (!AccountExportProtocol.SOURCES.contains(source)) throw new IllegalArgumentException("Unknown export source");
        this.source = source; this.snapshots = snapshots; this.projection = projection;
        this.consumer = consumer; this.outbox = outbox; this.protocol = protocol; this.clock = clock;
    }

    public void accept(DurableEvent event) {
        var request = protocol.request(event);
        request.validate(clock.instant());
        boolean cancelled = AccountExportProtocol.CANCELLED.equals(event.kind());
        consumer.apply(event, cancelled, () -> {
            String fingerprint = null;
            if (cancelled) snapshots.cancelJob(request);
            else fingerprint = snapshots.capture(request, output -> projection.capture(request.accountId(), output)).fingerprint();
            var receipt = new AccountExportProtocol.Receipt(request.jobId(), request.accountId(), source, cancelled ? "CANCELLED" : "READY", fingerprint);
            outbox.append("lifecycle-auth", AccountExportProtocol.RECEIPT, AccountExportProtocol.key(request.jobId()), protocol.encode(receipt));
        });
    }
}
