package com.app.facerecognizer.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetImageCopier {

    public static void copyImagesFromAssets(Context context, String destinationPath) {
        try {

            File file = new File(destinationPath);
            if (!file.exists()) {
                file.mkdirs();
            }

            // 获取 assets 文件夹下的文件列表
            String[] assetFiles = context.getAssets().list("faceImages");

            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    // 打开输入流读取文件
                    InputStream inputStream = context.getAssets().open("faceImages/" + fileName);

                    // 创建输出文件和输出流
                    File outputFile = new File(destinationPath, fileName);
                    FileOutputStream outputStream = new FileOutputStream(outputFile);

                    // 复制数据
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    // 关闭流
                    inputStream.close();
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

