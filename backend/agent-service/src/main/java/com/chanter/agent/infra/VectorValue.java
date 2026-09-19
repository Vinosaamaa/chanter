package com.chanter.agent.infra;

import com.chanter.agent.application.EmbeddingCodec;

/** pgvector text wire format; JDBC sends this as a bound parameter, never SQL syntax. */
public final class VectorValue {
    private VectorValue() {}
    public static String encode(float[] values, int dimensions) {
        if(values==null || values.length!=dimensions || dimensions<8 || dimensions>2000) throw new IllegalArgumentException("Embedding dimensions do not match model");
        double norm=0;
        var text=new StringBuilder("[");
        for(int i=0;i<values.length;i++) {
            if(!Float.isFinite(values[i])) throw new IllegalArgumentException("Embedding must be finite");
            norm+=(double)values[i]*values[i];
            if(i>0) text.append(','); text.append(values[i]);
        }
        if(Math.abs(norm-1)>0.001) throw new IllegalArgumentException("Embedding must be a nonzero unit vector");
        return text.append(']').toString();
    }
    public static float[] decode(String text) {
        String[] parts=text.substring(1,text.length()-1).split(",");
        float[] result=new float[parts.length];
        for(int i=0;i<parts.length;i++) result[i]=Float.parseFloat(parts[i]);
        return result;
    }
    /** H2 fixture function only. Production SQL uses the pgvector distance operator. */
    public static double testDistance(String left,String right) {
        return 1-EmbeddingCodec.cosineSimilarity(decode(left),decode(right));
    }
}
