package com.chanter.agent.application;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes, int expectedDimensions) {
        if (bytes == null || bytes.length != expectedDimensions * Float.BYTES) {
            throw new IllegalArgumentException("Embedding byte length does not match dimensions");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[expectedDimensions];
        for (int i = 0; i < expectedDimensions; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public static float[] l2Normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += (double) value * value;
        }
        if (sumSquares == 0.0) {
            return vector;
        }
        float norm = (float) Math.sqrt(sumSquares);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    public static double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
