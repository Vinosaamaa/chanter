package com.chanter.community.domain;

import java.util.UUID;

public record StudyServerChannel(UUID id, UUID studyServerId, String name, ChannelKind kind, int position) {

    public StudyServerChannel(UUID id, String name, ChannelKind kind, int position) {
        this(id, null, name, kind, position);
    }
}
