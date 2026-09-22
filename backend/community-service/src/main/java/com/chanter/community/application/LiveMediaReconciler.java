package com.chanter.community.application;

import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import livekit.LivekitModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The self-hosted server cannot revoke join tokens; ingress handles rejoin, this loop stops current media. */
@Component
@Profile("!test")
public class LiveMediaReconciler {
    private static final Logger log = LoggerFactory.getLogger(LiveMediaReconciler.class);
    private final RoomServiceClient livekit;
    private final LiveMediaAccess access;
    private String afterRoom = "";
    private String afterParticipant = "";

    public LiveMediaReconciler(RoomServiceClient livekit, LiveMediaAccess access) {
        this.livekit = livekit;
        this.access = access;
    }

    @Scheduled(fixedDelay = 5000)
    public void reconcile() {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        int checked = 0;
        int inspectedRooms = 0;
        try {
            var response = livekit.listRooms().execute();
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Room inventory unavailable");
            for (var room : response.body().stream().sorted(Comparator.comparing(LivekitModels.Room::getName)).toList()) {
                String name = room.getName();
                if ((!name.startsWith("voice-") && !name.startsWith("dm-call-")) || name.compareTo(afterRoom) < 0) continue;
                if (++inspectedRooms > 32 || System.nanoTime() >= deadline) return;
                var participants = livekit.listParticipants(name).execute();
                if (!participants.isSuccessful() || participants.body() == null) throw new IOException("Participant inventory unavailable");
                for (var participant : participants.body().stream()
                        .sorted(Comparator.comparing(LivekitModels.ParticipantInfo::getIdentity)).toList()) {
                    String identity = participant.getIdentity();
                    if (name.equals(afterRoom) && identity.compareTo(afterParticipant) <= 0) continue;
                    afterRoom = name;
                    afterParticipant = identity;
                    boolean remove = false;
                    try {
                        access.requireAllowed(name, UUID.fromString(identity));
                    } catch (ResponseStatusException | IllegalArgumentException denied) {
                        // An unavailable authority cannot keep an existing media participant authorized.
                        remove = true;
                    }
                    if (remove) {
                        try {
                            var result = livekit.removeParticipant(name, identity).execute();
                            if (!result.isSuccessful() && result.code() != 404) throw new IOException("Participant removal unavailable");
                        } catch (IOException unavailable) {
                            log.warn("Media participant removal failed; the next reconciliation will retry");
                        }
                    }
                    if (++checked >= 64 || System.nanoTime() >= deadline) return;
                }
                afterRoom = name;
                afterParticipant = "\uffff";
            }
            afterRoom = "";
            afterParticipant = "";
        } catch (IOException unavailable) {
            log.warn("Media authorization reconciliation unavailable; it will retry");
        }
    }
}
