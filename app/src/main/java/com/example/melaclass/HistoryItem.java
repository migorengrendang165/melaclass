package com.example.melaclass;

public class HistoryItem {
    private String imagePath;
    private String prediction;

    public HistoryItem(String imagePath, String prediction) {
        this.imagePath = imagePath;
        this.prediction = prediction;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getPrediction() {
        return prediction;
    }
}
