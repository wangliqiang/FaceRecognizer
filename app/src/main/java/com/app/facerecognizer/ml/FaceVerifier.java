package com.app.facerecognizer.ml;

import android.graphics.Bitmap;
import android.util.Pair;

import com.app.facerecognizer.db.entities.FaceImageInfo;

import java.util.List;
import java.util.Map;

public class FaceVerifier {

    private FaceEmbeddingExtractor embeddingExtractor;
    private Map<String, float[]> embeddingCache;

    public FaceVerifier(FaceEmbeddingExtractor embeddingExtractor, Map<String, float[]> embeddingCache) {
        this.embeddingExtractor = embeddingExtractor;
        this.embeddingCache = embeddingCache;
    }

    public Pair<String, Float> verifyFace(Bitmap compareBitmap, List<FaceImageInfo> faceImageList) {
        if (compareBitmap == null) return new Pair<>("", 0f);

        // 获取图像的特征值
        String imagePath = compareBitmap.toString();
        float[] currentEmbedding = embeddingCache.get(imagePath);

        if (currentEmbedding == null) {
            currentEmbedding = embeddingExtractor.getFaceEmbedding(compareBitmap);
            // 缓存特征值
            embeddingCache.put(imagePath, currentEmbedding);
        }

        // 查找最相似的人脸
        return findMostSimilarFace(currentEmbedding, faceImageList);
    }

    private Pair<String, Float> findMostSimilarFace(float[] currentEmbedding, List<FaceImageInfo> list) {
        String mostSimilarImagePath = "";
        float highestSimilarity = -1f;

        for (FaceImageInfo storedEmbedding : list) {
            float similarity = cosineSimilarity(currentEmbedding, storedEmbedding.getFeature());
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                mostSimilarImagePath = storedEmbedding.getPath();
            }
        }

        return new Pair<>(mostSimilarImagePath, highestSimilarity);
    }

    private float cosineSimilarity(float[] vec1, float[] vec2) {
        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            normA += vec1[i] * vec1[i];
            normB += vec2[i] * vec2[i];
        }
        return dotProduct / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
    }

    public void close() {
        if (embeddingExtractor != null) {
            embeddingExtractor.close(); // 释放 TensorFlow Lite 资源
        }
    }
}

