package com.app.facerecognizer.ml;

public class SimilarInfoBean {
    private int id;
    private String name;
    private String path;
    private float similarity;

    public SimilarInfoBean() {
    }
    public SimilarInfoBean(int id, String name, String path, float similarity) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.similarity = similarity;
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

    public float getSimilarity() {
        return similarity;
    }

    public void setSimilarity(float similarity) {
        this.similarity = similarity;
    }
}
