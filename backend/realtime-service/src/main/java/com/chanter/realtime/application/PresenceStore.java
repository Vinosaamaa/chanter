package com.chanter.realtime.application;

import java.util.UUID;

public interface PresenceStore {

    void markOnline(UUID userId);

    void markOffline(UUID userId);

    boolean isOnline(UUID userId);
}
