package com.app.facerecognizer.db.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "face_images")
public class FaceImageInfo {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String path;
    private float[] feature;

    public FaceImageInfo(){}

    public FaceImageInfo(String name, String path, float[] feature) {
        this.name = name;
        this.path = path;
        this.feature = feature;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public float[] getFeature() {
        return feature;
    }

    public void setFeature(float[] feature) {
        this.feature = feature;
    }
}
