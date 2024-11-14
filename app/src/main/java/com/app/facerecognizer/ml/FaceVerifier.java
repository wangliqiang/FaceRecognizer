package com.app.facerecognizer.ml;

import android.graphics.Bitmap;

import com.app.facerecognizer.db.entities.FaceImageInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceVerifier {

    private FaceEmbeddingExtractor embeddingExtractor;
    private Map<String, float[]> embeddingCache;
    private ExecutorService executorService;

    public FaceVerifier(FaceEmbeddingExtractor embeddingExtractor, Map<String, float[]> embeddingCache) {
        this.embeddingExtractor = embeddingExtractor;
        this.embeddingCache = embeddingCache;
        int numThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(numThreads);
    }

    public SimilarInfoBean verifyFace(Bitmap compareBitmap, List<FaceImageInfo> faceImageList) {
        if (compareBitmap == null) return new SimilarInfoBean(0, "", "", 0f);

        String imagePath = compareBitmap.toString();
        float[] currentEmbedding = embeddingCache.get(imagePath);

        if (currentEmbedding == null) {
            currentEmbedding = embeddingExtractor.getFaceEmbedding(compareBitmap);
            embeddingCache.put(imagePath, currentEmbedding);
        }

        // 聚类比对
        Map<String, List<Float>> scoreMap = new HashMap<>();
        batchCompareFaces(currentEmbedding, faceImageList, scoreMap);

        SimilarInfoBean mostSimilar = calculateBestMatch(scoreMap, faceImageList);
        return mostSimilar;
    }

    private void batchCompareFaces(float[] currentEmbedding, List<FaceImageInfo> list, Map<String, List<Float>> scoreMap) {
        CompletableFuture[] futures = new CompletableFuture[list.size()];

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                FaceImageInfo storedEmbedding = list.get(index);
                float[] storedFeature = normalize(storedEmbedding.getFeature());
                // 标准化存储的面部特征向量
                storedFeature = normalize(storedFeature);

                // 计算余弦相似度
                float cosineSim = cosineSimilarity(currentEmbedding, storedFeature);

                // 计算L2 norm
                float l2Norm = L2Norm(currentEmbedding, storedFeature);

                // 这里我们选择使用加权方式结合 L2 norm 和 Cosine 相似度
                // 权重可以根据需求调整，这里假设为 0.5
                float combinedSimilarity = 0.6f * cosineSim + 0.4f * (1 - l2Norm); // L2 norm 越小，越相似，因此需要转化成 1 - l2Norm

                synchronized (scoreMap) {
                    scoreMap.computeIfAbsent(storedEmbedding.getName(), k -> new ArrayList<>()).add(combinedSimilarity);
                }
            }, executorService);
        }

        CompletableFuture.allOf(futures).join();
    }

    private SimilarInfoBean calculateBestMatch(Map<String, List<Float>> scoreMap, List<FaceImageInfo> faceImageList) {
        float maxAverageScore = -1f;
        String bestMatchName = "";
//        String bestMatchPath = "";
        SimilarInfoBean bestMatch = new SimilarInfoBean(0, "", "", 0);

        for (Map.Entry<String, List<Float>> entry : scoreMap.entrySet()) {
            float averageScore = (float) entry.getValue().stream().mapToDouble(Float::doubleValue).average().orElse(0);
            if (averageScore > maxAverageScore) {
                maxAverageScore = averageScore;
                bestMatchName = entry.getKey();
                bestMatch = faceImageList.stream()
                        .filter(info -> info.getName().equals(entry.getKey()))
                        .findFirst()
                        .map(info -> new SimilarInfoBean(info.getId(), info.getName(), info.getPath(), averageScore))
                        .orElse(new SimilarInfoBean(0, "", "", 0));
            }
        }

        return bestMatch;
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
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // 计算 L2 norm (欧几里得距离)
    private float L2Norm(float[] vec1, float[] vec2) {
        float sum = 0f;
        for (int i = 0; i < vec1.length; i++) {
            sum += Math.pow(vec1[i] - vec2[i], 2);
        }
        return (float) Math.sqrt(sum);
    }

    // 向量标准化
    private float[] normalize(float[] vec) {
        float norm = 0f;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm == 0) return vec;

        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    public void close() {
        if (embeddingExtractor != null) {
            embeddingExtractor.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
