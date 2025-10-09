package com.hmdp.utils;

public final class VectorUtils {
    private VectorUtils(){}

    public static byte[] toFloat32LE(float[] v) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 * v.length)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (float f : v) buf.putFloat(f);
        return buf.array();
    }

    public static float[] avg(java.util.List<float[]> arr) {
        if (arr == null || arr.isEmpty()) return new float[0];
        int d = arr.get(0).length;
        float[] out = new float[d];
        for (float[] v : arr) for (int i=0;i<d;i++) out[i]+=v[i];
        for (int i=0;i<d;i++) out[i]/=arr.size();
        return out;
    }
}
