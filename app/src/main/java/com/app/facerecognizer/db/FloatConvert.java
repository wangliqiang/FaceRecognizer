package com.app.facerecognizer.db;

import androidx.room.TypeConverter;

public class FloatConvert {
    @TypeConverter
    public String fromFloatArray(float[] value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            sb.append(value[i]);
            if (i < value.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    @TypeConverter
    public float[] toFloatArray(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        float[] floatArray = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            floatArray[i] = Float.parseFloat(parts[i]);
        }
        return floatArray;
    }
}
