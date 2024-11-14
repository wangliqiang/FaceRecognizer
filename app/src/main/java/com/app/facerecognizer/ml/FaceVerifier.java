package com.app.facerecognizer.ml;

import android.graphics.Bitmap;

import com.app.facerecognizer.db.entities.FaceImageInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceVerifier {

    private FaceEmbeddingExtractor embeddingExtractor;
    private Map<String, float[]> embeddingCache;
    private ExecutorService executorService; // 线程池

    public FaceVerifier(FaceEmbeddingExtractor embeddingExtractor, Map<String, float[]> embeddingCache) {
        this.embeddingExtractor = embeddingExtractor;
        this.embeddingCache = embeddingCache;
        int numThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(numThreads);
    }

    public SimilarInfoBean verifyFace(Bitmap compareBitmap, List<FaceImageInfo> faceImageList) {
        if (compareBitmap == null) return new SimilarInfoBean(0, "", "", 0f);

        // 获取图像的特征值
        String imagePath = compareBitmap.toString();
        float[] currentEmbedding = embeddingCache.get(imagePath);

        if (currentEmbedding == null) {
            currentEmbedding = embeddingExtractor.getFaceEmbedding(compareBitmap);
            // 缓存特征值
            embeddingCache.put(imagePath, currentEmbedding);
        }

        // 使用批量比对方法进行比对
        SimilarInfoBean[] compareResults = batchCompareFaces(currentEmbedding, faceImageList);

        // 找到最相似的面部信息
        SimilarInfoBean mostSimilar = new SimilarInfoBean(0, "", "", -1f);
        for (SimilarInfoBean result : compareResults) {
            if (result.getSimilarity() > mostSimilar.getSimilarity()) {
                mostSimilar = result;
            }
        }
        return mostSimilar;
    }

    // 批量计算余弦相似度，如果一次比较多个面孔
    private SimilarInfoBean[] batchCompareFaces(float[] currentEmbedding, List<FaceImageInfo> list) {
        SimilarInfoBean[] results = new SimilarInfoBean[list.size()];

        // 使用 CompletableFuture 并行执行
        CompletableFuture[] futures = new CompletableFuture[list.size()];

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                FaceImageInfo storedEmbedding = list.get(index);

                float[] storedFeature = storedEmbedding.getFeature();

                float similarity = cosineSimilarity (normalize(currentEmbedding), normalize(storedFeature));
                results[index] = new SimilarInfoBean(
                        storedEmbedding.getId(),
                        storedEmbedding.getName(),
                        storedEmbedding.getPath(),
                        similarity
                );
            }, executorService);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures).join();

        return results;
    }

    // 基于三元组损失的相似度计算
    private float tripletLossBasedSimilarity(float[] vec1, float[] vec2) {
        // 计算余弦相似度和欧氏距离
        float cosineSim = cosineSimilarity(vec1, vec2);
        float euclideanDist = euclideanDistance(vec1, vec2);

        // 归一化欧氏距离
        float euclideanSim = 1 / (1 + euclideanDist);  // 使得距离越小相似度越大

        // 三元组损失思想：如果余弦相似度很高，欧氏距离很小，表明这两个人脸非常相似
        return 0.9f * cosineSim + 0.1f * euclideanSim;
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

        if (normA == 0 || normB == 0) return 0f;

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float euclideanDistance(float[] vec1, float[] vec2) {
        float sum = 0;
        for (int i = 0; i < vec1.length; i++) {
            sum += (float) Math.pow(vec1[i] - vec2[i], 2);
        }
        return (float) Math.sqrt(sum);
    }

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
            embeddingExtractor.close(); // 释放 TensorFlow Lite 资源
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
