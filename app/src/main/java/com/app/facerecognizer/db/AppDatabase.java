package com.app.facerecognizer.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.app.facerecognizer.db.dao.FaceImageDao;
import com.app.facerecognizer.db.entities.FaceImageInfo;

@Database(entities = {FaceImageInfo.class}, version = 1)
@TypeConverters({FloatConvert.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract FaceImageDao faceImageDao();

    private static volatile AppDatabase INSTANCE = null;

    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "face_recognizer.db").build();
                }
            }
        }
        return INSTANCE;
    }
}
