package com.solum.draw.vision.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImageProfile {
    public String imagePath = "";

    public VisualFeatureVector features = new VisualFeatureVector();

    public final List<String> mlKitLabels = new ArrayList<>();
    public final List<Float> mlKitLabelScores = new ArrayList<>();
    public String rawPredicted = "";

    public ImageAxes.StyleAxis styleAxis = ImageAxes.StyleAxis.UNKNOWN;
    public ImageAxes.ContentAxis contentAxis = ImageAxes.ContentAxis.UNKNOWN;
    public ImageAxes.PurposeAxis purposeAxis = ImageAxes.PurposeAxis.UNKNOWN;
    public ImageAxes.QualityAxis qualityAxis = ImageAxes.QualityAxis.UNKNOWN;

    public float styleAxisConf = 0f;
    public float contentAxisConf = 0f;
    public float purposeAxisConf = 0f;
    public float qualityAxisConf = 0f;

    public final Map<String, Float> classScores = new LinkedHashMap<>();

    public String shadowFinalClass = "";
    public final List<String> shadowTop3 = new ArrayList<>();
    public float shadowConfidence = 0f;

    public boolean featuresDone = false;
    public boolean axesDone = false;
    public boolean scoringDone = false;

    public void resetShadow() {
        shadowFinalClass = "";
        shadowTop3.clear();
        shadowConfidence = 0f;
        axesDone = false;
        scoringDone = false;
        classScores.clear();
        styleAxis = ImageAxes.StyleAxis.UNKNOWN;
        contentAxis = ImageAxes.ContentAxis.UNKNOWN;
        purposeAxis = ImageAxes.PurposeAxis.UNKNOWN;
        qualityAxis = ImageAxes.QualityAxis.UNKNOWN;
        styleAxisConf = 0f;
        contentAxisConf = 0f;
        purposeAxisConf = 0f;
        qualityAxisConf = 0f;
    }
}
