package com.chanter.agent.domain;

/** Immutable identity of a coordinate space. Revision includes preprocessing/normalization changes. */
public record EmbeddingModel(String id, String provider, String model, String revision, int dimensions) {
    public EmbeddingModel {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9:._/@+-]{0,159}") || provider == null || provider.isBlank() || provider.length() > 32
                || model == null || model.isBlank() || model.length() > 200 || revision == null || revision.isBlank() || revision.length() > 160
                || dimensions < 8 || dimensions > 2000) throw new IllegalArgumentException("Invalid embedding model identity");
    }
}
