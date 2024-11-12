package com.app.facerecognizer.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FaceEmbeddingExtractor {

    private Context mContext;
    private Interpreter interpreter;

    public FaceEmbeddingExtractor(Context context) throws IOException {
        mContext = context;
        // 初始化 TensorFlow Lite 解释器
        this.interpreter = new Interpreter(loadModelFile());
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(mContext.getAssets().openFd("mobile_face_net.tflite").getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = mContext.getAssets().openFd("mobile_face_net.tflite").getStartOffset();
        long declaredLength = mContext.getAssets().openFd("mobile_face_net.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[] getFaceEmbedding(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e("FaceEmbeddingExtractor", "Bitmap is null");
            return new float[192]; // 返回默认值或抛出异常
        }
        ByteBuffer input = preprocessBitmap(bitmap);
        float[][] output = new float[1][192]; // MobileFaceNet 输出的嵌入向量长度是 192
        interpreter.run(input, output);
        return output[0];
    }

    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true); // MobileFaceNet 输入尺寸
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 112 * 112 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[112 * 112];
        resizedBitmap.getPixels(intValues, 0, 112, 0, 0, 112, 112);

        for (int pixelValue : intValues) {
            float r = ((pixelValue >> 16) & 0xFF) / 255.0f;
            float g = ((pixelValue >> 8) & 0xFF) / 255.0f;
            float b = (pixelValue & 0xFF) / 255.0f;

            byteBuffer.putFloat(r);
            byteBuffer.putFloat(g);
            byteBuffer.putFloat(b);
        }
        return byteBuffer;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close(); // 释放 TensorFlow Lite 资源
        }
    }
}
