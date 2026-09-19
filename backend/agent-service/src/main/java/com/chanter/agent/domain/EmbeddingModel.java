package com.chanter.agent.domain;

/** Immutable identity of a coordinate space. Revision includes preprocessing/normalization changes. */
public record EmbeddingModel(String id, String provider, String model, String revision, int dimensions) {
    public EmbeddingModel {
        if (id == null || id.isBlank() || id.length() > 160 || provider == null || provider.isBlank()
                || model == null || model.isBlank() || revision == null || revision.isBlank()
                || dimensions < 8 || dimensions > 2000) throw new IllegalArgumentException("Invalid embedding model identity");
    }
}
