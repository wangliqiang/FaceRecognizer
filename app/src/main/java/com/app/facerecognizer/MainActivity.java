package com.app.facerecognizer;

import static com.app.facerecognizer.utils.AssetImageCopier.copyImagesFromAssets;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.app.facerecognizer.adapter.FaceImageListAdapter;
import com.app.facerecognizer.databinding.ActivityMainBinding;
import com.app.facerecognizer.db.AppDatabase;
import com.app.facerecognizer.db.entities.FaceImageInfo;
import com.app.facerecognizer.ml.FaceEmbeddingExtractor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA};

    private List<FaceImageInfo> faceImageList = new ArrayList();
    public static String CACHE_SEARCH_FACE_DIR;
    private FaceImageListAdapter faceImageListAdapter;

    private FaceEmbeddingExtractor embeddingExtractor;
    AppDatabase database;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        CACHE_SEARCH_FACE_DIR = getCacheDir().getPath() + "/faceSearch";
        database = AppDatabase.getDatabase(this);

        try {
            // 初始化特征提取器
            embeddingExtractor = new FaceEmbeddingExtractor(this);
        } catch (IOException e) {
            Log.e("FaceEmbeddingExtractor", "Error initializing model", e);
        }

        // 检查权限
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        } else {
            initView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadImageList();
    }

    private void initView() {
        int spanCount = 3;
        int ori = getResources().getConfiguration().orientation; //获取屏幕方向
        if (ori == Configuration.ORIENTATION_LANDSCAPE) {
            spanCount = 5;
        }
        GridLayoutManager gridLayoutManager = new GridLayoutManager(MainActivity.this, spanCount);
        binding.recyclerView.setLayoutManager(gridLayoutManager);
        faceImageListAdapter = new FaceImageListAdapter(faceImageList);
        binding.recyclerView.setAdapter(faceImageListAdapter);
        faceImageListAdapter.onItemLongClickListener = faceImageInfo -> {
            new AlertDialog.Builder(this)
                    .setTitle("是否删除这张图片？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            File file = new File(faceImageInfo.getPath());
                            if (file.exists()) {
                                file.delete();
                            }
                            database.faceImageDao().deleteOne(faceImageInfo.getId() + 1);
                        });
                        loadImageList();
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .show();

        };
        binding.exportImage.setOnClickListener(v -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            copyImagesFromAssets(this, CACHE_SEARCH_FACE_DIR);
            // 先清空数据库，然后生成特征值，保存到数据库
            Executors.newSingleThreadExecutor().execute(() -> {
                database.faceImageDao().deleteAll();
                generateEmbeddingsForImages();
            });
        });
        binding.addImage.setOnClickListener(v -> {
            startActivity(new Intent(this, ImageCaptureActivity.class));
        });
        binding.faceVerify.setOnClickListener(v -> {
            startActivity(new Intent(this, FaceRecognizerActivity.class));
        });
    }

    private void generateEmbeddingsForImages() {
        faceImageList.clear();
        File folder = new File(CACHE_SEARCH_FACE_DIR);
        File[] subFaceFiles = folder.listFiles();
        if (subFaceFiles != null) {
            Arrays.stream(subFaceFiles)
                    .filter(file -> !file.isDirectory() && file.getName().matches("(?i).*\\.(jpg|jpeg|png)$"))
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .forEach(file -> {
                        FaceImageInfo faceImageInfo = new FaceImageInfo(file.getName(), file.getPath(), null);
                        faceImageList.add(faceImageInfo);
                    });
        }


        for (int i = 0; i < faceImageList.size(); i++) {
            Bitmap bitmap = BitmapFactory.decodeFile(faceImageList.get(i).getPath());
            float[] embedding = getFaceEmbedding(bitmap);
            faceImageList.get(i).setFeature(embedding);
            database.faceImageDao().insert(faceImageList.get(i));
        }
        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.GONE);
            faceImageListAdapter.notifyDataSetChanged();
        });
    }

    private float[] getFaceEmbedding(Bitmap bitmap) {
        return embeddingExtractor.getFaceEmbedding(bitmap);
    }


    /**
     * 加载人脸文件夹CACHE_SEARCH_FACE_DIR 里面的人脸照片
     */
    private void loadImageList() {
        faceImageList.clear();
        File folder = new File(CACHE_SEARCH_FACE_DIR);
        File[] subFaceFiles = folder.listFiles();
        if (subFaceFiles != null) {
            Arrays.stream(subFaceFiles)
                    .filter(file -> !file.isDirectory() && file.getName().matches("(?i).*\\.(jpg|jpeg|png)$"))
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .forEach(file -> {
                        FaceImageInfo faceImageInfo = new FaceImageInfo(file.getName(), file.getPath(), null);
                        faceImageList.add(faceImageInfo);
                    });
            faceImageListAdapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {

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
        if (embeddingExtractor != null) {
            embeddingExtractor.close();  // 释放资源
        }
    }
}