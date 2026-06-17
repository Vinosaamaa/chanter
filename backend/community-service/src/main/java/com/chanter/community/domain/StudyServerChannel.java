package com.chanter.community.domain;

import java.util.UUID;

public record StudyServerChannel(UUID id, String name, ChannelKind kind, int position) {
}
