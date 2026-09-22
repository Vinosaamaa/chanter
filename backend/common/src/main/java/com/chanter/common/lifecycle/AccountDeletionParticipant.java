package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import java.util.UUID;

/** Source mutation, ordinary delivery cursor and return receipt commit in the same owning database transaction. */
public final class AccountDeletionParticipant {
    private final String source;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final AccountDeletionProtocol protocol;
    private final TerminalReapplyStore terminal;
    private final OwnershipPreparation ownership;
    public AccountDeletionParticipant(String source,DurableConsumer consumer,DurableOutbox outbox,
            AccountDeletionProtocol protocol,TerminalReapplyStore terminal,OwnershipPreparation ownership) {
        if(!AccountExportProtocol.SOURCES.contains(source) || "auth".equals(source)
                || "community".equals(source)!=(ownership!=null)) throw new IllegalArgumentException("Invalid deletion participant");
        this.source=source; this.consumer=consumer; this.outbox=outbox; this.protocol=protocol;
        this.terminal=java.util.Objects.requireNonNull(terminal); this.ownership=ownership;
    }
    public void validate(DurableEvent event) {
        if(AccountDeletionProtocol.TERMINAL.equals(event.kind())) protocol.terminal(event);
        else {
            if(ownership==null) throw new IllegalArgumentException("Ownership belongs to community");
            protocol.preparation(event);
        }
    }
    public void accept(DurableEvent event) {
        validate(event);
        boolean deleted=AccountDeletionProtocol.TERMINAL.equals(event.kind());
        consumer.apply(event,deleted,() -> {
            AccountDeletionProtocol.Receipt receipt;
            if(deleted) {
                var command=protocol.terminal(event); var entry=command.entry();
                terminal.applyTerminal(entry);
                receipt=new AccountDeletionProtocol.Receipt(command.jobId(),source,entry.targetKind(),entry.targetId(),
                        terminal.cleanup(entry).name(),entry.revision(),entry.digest());
            } else {
                var command=protocol.preparation(event);
                String state;
                if(AccountDeletionProtocol.PREPARE.equals(event.kind())) state=ownership.prepare(command.accountId(),command.jobId());
                else { ownership.release(command.accountId(),command.jobId()); state="RELEASED"; }
                receipt=new AccountDeletionProtocol.Receipt(command.jobId(),source,"ACCOUNT",command.accountId(),state,null,null);
            }
            receipt.validate();
            outbox.append("lifecycle-auth",AccountDeletionProtocol.RECEIPT,event.aggregateKey(),protocol.encode(receipt));
        });
    }
    public interface OwnershipPreparation {
        String prepare(UUID account,UUID job);
        void release(UUID account,UUID job);
    }
}
