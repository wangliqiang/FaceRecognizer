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
        this.executorService = Executors.newCachedThreadPool();
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
        // 查找最相似的人脸
//        return findMostSimilarFace(currentEmbedding, faceImageList);
        return mostSimilar;
    }

    private SimilarInfoBean findMostSimilarFace(float[] currentEmbedding, List<FaceImageInfo> list) {
        // 使用并行流提高比对速度
        float highestSimilarity = -1f;
        SimilarInfoBean mostSimilar = new SimilarInfoBean(0, "", "", highestSimilarity);

        // 并行处理，提高比对速度
        for (FaceImageInfo storedEmbedding : list) {
            float similarity = cosineSimilarity(currentEmbedding, storedEmbedding.getFeature());
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                mostSimilar = new SimilarInfoBean(
                        storedEmbedding.getId(),
                        storedEmbedding.getName(),
                        storedEmbedding.getPath(),
                        highestSimilarity
                );
            }
        }

        return mostSimilar;
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

        // 防止除零错误，如果某个向量长度为0，返回相似度为0
        if (normA == 0 || normB == 0) return 0f;

        return dotProduct / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
    }

    // 批量计算余弦相似度，如果一次比较多个面孔
    private SimilarInfoBean[] batchCompareFaces(float[] currentEmbedding, List<FaceImageInfo> list) {
        SimilarInfoBean[] results = new SimilarInfoBean[list.size()];

        // 使用 CompletableFuture 并行执行
        CompletableFuture<Void>[] futures = new CompletableFuture[list.size()];

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                FaceImageInfo storedEmbedding = list.get(index);
                float similarity = cosineSimilarity(currentEmbedding, storedEmbedding.getFeature());
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

    public void close() {
        if (embeddingExtractor != null) {
            embeddingExtractor.close(); // 释放 TensorFlow Lite 资源
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
