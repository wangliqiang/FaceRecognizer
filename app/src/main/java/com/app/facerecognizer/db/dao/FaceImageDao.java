package com.app.facerecognizer.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.app.facerecognizer.db.entities.FaceImageInfo;

import java.util.List;

@Dao
public interface FaceImageDao {
    @Insert
    void insert(FaceImageInfo faceImageInfo);

    @Query("SELECT * FROM face_images")
    List<FaceImageInfo> getAll();

    @Query("DELETE FROM face_images WHERE id = :id")
    void deleteOne(int id);

    @Query("DELETE FROM face_images")
    void deleteAll();
}
