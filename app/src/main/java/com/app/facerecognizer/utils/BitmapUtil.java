package com.app.facerecognizer.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;

import java.nio.ByteBuffer;

public class BitmapUtil {
    public static Bitmap toBitmap(InputImage inputImage) {
        // 如果 InputImage 本身已经是一个 Bitmap
        if (inputImage.getBitmapInternal() != null) {
            return inputImage.getBitmapInternal();
        } else if (inputImage.getMediaImage() != null) {
            // 处理 mediaImage 转换为 Bitmap
            android.media.Image mediaImage = inputImage.getMediaImage();
            if (mediaImage != null) {
                Bitmap bitmap = mediaImageToBitmap(mediaImage);
                return rotateBitmap(bitmap, inputImage.getRotationDegrees());
            } else {
                Log.e("toBitmap", "MediaImage is null");
            }
        } else {
            Log.e("toBitmap", "InputImage does not contain a Bitmap or MediaImage");
        }
        return null; // 如果没有有效的图片，返回 null
    }

    private static Bitmap mediaImageToBitmap(android.media.Image mediaImage) {
        ByteBuffer buffer = mediaImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return bitmap;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

}
