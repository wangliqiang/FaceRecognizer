package com.app.facerecognizer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.app.facerecognizer.databinding.ActivityImageCaptureBinding;
import com.app.facerecognizer.db.AppDatabase;
import com.app.facerecognizer.db.entities.FaceImageInfo;
import com.app.facerecognizer.ml.FaceEmbeddingExtractor;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageCaptureActivity extends AppCompatActivity {

    ActivityImageCaptureBinding binding;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private FaceEmbeddingExtractor embeddingExtractor;
    AppDatabase database;

    public static String CACHE_SEARCH_FACE_DIR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        CACHE_SEARCH_FACE_DIR = getCacheDir().getPath() + "/faceSearch";
        try {
            // 初始化特征提取器
            embeddingExtractor = new FaceEmbeddingExtractor(this);
        } catch (IOException e) {
            Log.e("FaceEmbeddingExtractor", "Error initializing model", e);
        }
        database = AppDatabase.getDatabase(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();

        binding.btnCapture.setOnClickListener(v -> {
            takePhoto();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                imageCapture = new ImageCapture.Builder().build();
                Preview preview = new Preview.Builder()
                        .build();

                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageCapture, preview);

                binding.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                camera.getCameraControl().enableTorch(false);

            } catch (Exception e) {
                Log.e("CameraActivity", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void takePhoto() {
        if (imageCapture == null) return;

        File folder = new File(CACHE_SEARCH_FACE_DIR);
        if (!folder.exists()) folder.mkdirs();

        File file = new File(getExternalCacheDir(), "template.jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(file).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
                        file.delete();
                        showSaveImageDialog(bitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraActivity", "Photo capture failed: " + exception.getMessage(), exception);
                    }
                });
    }

    private void showSaveImageDialog(Bitmap bitmap) {
        // 创建输入框
        final EditText input = new EditText(this);
        input.setHint("请输入图片名称");

        // 创建弹窗
        new AlertDialog.Builder(this)
                .setTitle("输入图片名称")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    // 获取用户输入的文件名
                    String imageName = input.getText().toString().trim();
                    if (!imageName.isEmpty()) {
                        // 使用 ML Kit 检测人脸并裁剪
//                        detectFaceAndCrop(bitmap, imageName);
                        // 直接保存图片，不做任何人脸检测和裁剪
                        saveBitmapToFile(bitmap, new File(CACHE_SEARCH_FACE_DIR, imageName + ".jpg"));
                    } else {
                        // 如果没有输入名称，提示用户
                        Log.e("CameraActivity", "图片名称不能为空！");
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveBitmapToFile(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            FaceImageInfo info = new FaceImageInfo();
            info.setName(file.getName());
            info.setPath(file.getPath());
            info.setFeature(getFaceEmbedding(bitmap));
            Executors.newSingleThreadExecutor().execute(() -> database.faceImageDao().insert(info));
            Log.i("CameraActivity", "Image saved to " + file.getPath());
            Toast.makeText(this, "录入人脸信息成功", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e("CameraActivity", "Failed to save image", e);
        }
    }

    private void detectFaceAndCrop(Bitmap bitmap, String imageName) {
        // 转换为 ML Kit 使用的 InputImage
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 配置人脸检测选项
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        FaceDetector detector = FaceDetection.getClient(options);

        // 使用 ML Kit 进行人脸检测
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0);  // 假设图片中只有一张人脸，取第一个人脸
                        Bitmap croppedBitmap = cropHeadRegion(bitmap, face);
                        saveBitmapToFile(croppedBitmap, new File(CACHE_SEARCH_FACE_DIR, imageName + ".jpg"));
                    } else {
                        Log.e("CameraActivity", "No face detected.");
                    }
                })
                .addOnFailureListener(e -> Log.e("CameraActivity", "Face detection failed: " + e.getMessage()));
    }

    private Bitmap cropHeadRegion(Bitmap originalBitmap, Face face) {
        // 获取人脸框架信息
        float left = face.getBoundingBox().left - 50;
        float top = face.getBoundingBox().top;
        float right = face.getBoundingBox().right + 50;
        float bottom = face.getBoundingBox().bottom;

        // 计算裁剪区域：这里假设裁剪区域为人脸框的上半部分（头部）
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        // 添加一些上下偏移，使裁剪区域包含更多的头部
        int cropTop = Math.max(0, (int) (top - (bottom - top) * 0.2));  // 增加顶部裁剪区域
        int cropBottom = Math.min(height, (int) (bottom + (bottom - top) * 0.2));  // 增加底部裁剪区域
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);  // 水平镜像翻转
        // 进行裁剪
        return Bitmap.createBitmap(originalBitmap, (int) left, cropTop, (int) (right - left), cropBottom - cropTop, matrix, true);
    }

    private float[] getFaceEmbedding(Bitmap bitmap) {
        return embeddingExtractor.getFaceEmbedding(bitmap);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

}