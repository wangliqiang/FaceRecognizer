package com.app.facerecognizer;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.app.facerecognizer.databinding.ActivityFaceRecognizerBinding;
import com.app.facerecognizer.db.AppDatabase;
import com.app.facerecognizer.db.entities.FaceImageInfo;
import com.app.facerecognizer.ml.FaceEmbeddingExtractor;
import com.app.facerecognizer.ml.FaceVerifier;
import com.app.facerecognizer.ml.SimilarInfoBean;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FaceRecognizerActivity extends AppCompatActivity {

    private ActivityFaceRecognizerBinding binding;
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA};
    public static String CACHE_SEARCH_FACE_DIR;

    private CameraSelector cameraSelector;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private FaceDetectorOptions faceDetectorOptions;
    private FaceDetector detector;
    private boolean isCheckFace = false;

    private ExecutorService cameraExecutor;
    private FaceEmbeddingExtractor embeddingExtractor;
    private FaceVerifier faceVerifier;
    AppDatabase database;


    private List<FaceImageInfo> faceImageList = new ArrayList();
    // 缓存嵌入向量
    private Map<String, float[]> embeddingCache = new HashMap<>();
    private boolean isVerifyPass = false;
    private Bitmap compareBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFaceRecognizerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        CACHE_SEARCH_FACE_DIR = getCacheDir().getPath() + "/faceSearch";
        cameraExecutor = Executors.newFixedThreadPool(3);
        database = AppDatabase.getDatabase(this);
        try {
            // 初始化特征提取器
            embeddingExtractor = new FaceEmbeddingExtractor(this);
            faceVerifier = new FaceVerifier(embeddingExtractor, embeddingCache);
        } catch (IOException e) {
            Log.e("FaceEmbeddingExtractor", "Error initializing model", e);
        }
        // 检查权限
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        } else {
            initView();
            initData();
            // 特征值数据加载完成后，初始化相机
            initCamera();
        }
    }

    private void initView() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void initData() {
        loadFaceImages();

        // 初始化存储的人脸嵌入向量
        cameraExecutor.execute(() -> {
            while (!isVerifyPass) {
                if (faceImageList != null && isCheckFace) {
                    processImage();
                }
            }
        });
    }

    /**
     * 加载人脸文件夹CACHE_SEARCH_FACE_DIR 里面的人脸照片
     */
    private void loadFaceImages() {
        faceImageList.clear();
        cameraExecutor.execute(() -> {
            faceImageList = database.faceImageDao().getAll();
        });
    }

    private void initCamera() {

        // 获取相机提供器
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // 设置相机选择器，选择后置摄像头（可根据需要修改为前置等其他摄像头）
        cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        // 设置人脸检测器
        faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        detector = FaceDetection.getClient(faceDetectorOptions);

        // 创建图像分析用例
        imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @OptIn(markerClass = ExperimentalGetImage.class)
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                // 检测人脸
                detector.process(image)
                        .addOnSuccessListener(faces -> {
                            if (!faces.isEmpty()) {
                                isCheckFace = true;
                            } else {
                                isCheckFace = false;
                            }
                        })
                        .addOnCompleteListener(task -> {
                            runOnUiThread(() -> {
                                compareBitmap = imageProxy.toBitmap();
                                binding.faceAvatar.setImageBitmap(imageProxy.toBitmap());
                            });
                            imageProxy.close(); // 关闭 imageProxy 以释放资源
                        });
            }
        });

        // 创建相机实例并绑定用例
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(FaceRecognizerActivity.this, cameraSelector, imageAnalysis);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage() {
        if (compareBitmap == null) return;
        String imagePath = compareBitmap.toString();
        float[] currentEmbedding = embeddingCache.get(imagePath);
        if (currentEmbedding == null) {
            currentEmbedding = embeddingExtractor.getFaceEmbedding(compareBitmap);
            // 将计算得到的嵌入向量缓存
            embeddingCache.put(imagePath, currentEmbedding);
        }

        // 找到最相似的人脸
        SimilarInfoBean similarInfoBean = faceVerifier.verifyFace(compareBitmap, faceImageList);
        if (similarInfoBean.getSimilarity() > 0.85) {
            isVerifyPass = true;
            Log.e("===========", "Most similar image: " + similarInfoBean.getName() + ", Similarity: " + similarInfoBean.getSimilarity());
            runOnUiThread(() -> {
                binding.avatar.setImageBitmap(BitmapFactory.decodeFile(similarInfoBean.getPath()));
                binding.userName.setText(similarInfoBean.getName().split("\\.")[0]);
                binding.tipsView.setText("人证核验通过!");
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCamera();
            } else {
                Log.e("Permissions", "Some permissions are missing.");
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止人脸检测和比对
        isVerifyPass = true; // 停止 while 循环

        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            try {
                cameraExecutor.shutdown();
                if (!cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cameraExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
            }
        }

        // 释放资源，确保不会再使用已关闭的 Interpreter
        if (embeddingExtractor != null) {
            embeddingExtractor.close(); // 释放资源
            embeddingExtractor = null;
        }
        if (faceVerifier != null) {
            faceVerifier.close(); // 释放资源
            faceVerifier = null;
        }
    }
}