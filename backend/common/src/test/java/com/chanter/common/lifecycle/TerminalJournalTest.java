package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerminalJournalTest {
    @Test void versionTwoDigestBindsRequiredPreservationPolicyAndRejectsLegacyOrMissingDisposition() throws Exception {
        var event = UUID.fromString("11111111-1111-4111-8111-111111111111");
        var target = UUID.fromString("22222222-2222-4222-8222-222222222222");
        var time = Instant.parse("2026-09-19T06:00:00.120Z");
        String digest = TerminalJournal.digest(1,event,"ACCOUNT",target,time,TerminalJournal.GENESIS);
        assertThat(digest).isEqualTo("b0d23bf4b8f953242aed4725ef4cf5cc1f01d6d2e957454c67e8ddec9a6a4c72");
        var entry = new TerminalJournal.Entry(1,event,"ACCOUNT",target,"DELETE",time,
                TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,digest);
        var zero = new TerminalJournal.Watermark(0,TerminalJournal.GENESIS);
        var head = new TerminalJournal.Watermark(1,digest);
        var page = new TerminalJournal.Page(2,zero,head,List.of(entry),head);
        page.validate();
        var mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.readValue(mapper.writeValueAsBytes(page),TerminalJournal.Page.class)).isEqualTo(page);
        assertThatThrownBy(() -> new TerminalJournal.Page(1,zero,head,List.of(entry),head).validate())
                .isInstanceOf(IllegalArgumentException.class);
        for (String policy : new String[] {null,"DELETE_ALL","PRESERVE_MODERATION_RECORDS_V2"}) {
            var changed = new TerminalJournal.Entry(1,event,"ACCOUNT",target,"DELETE",time,policy,TerminalJournal.GENESIS,digest);
            assertThatThrownBy(changed::validate).isInstanceOf(IllegalArgumentException.class);
        }
        var json = mapper.valueToTree(page);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.path("entries").get(0)).remove("retentionPolicy");
        var missing = mapper.treeToValue(json,TerminalJournal.Page.class);
        assertThatThrownBy(missing::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
