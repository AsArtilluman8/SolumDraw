package com.solum.draw.vision;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MlKitVisionProbe {
    public interface Callback {
        void onResult(VisionResult result);
        void onError(Exception error);
    }

    private MlKitVisionProbe() {}

    public static void analyze(Bitmap bitmap, Callback callback) {
        if (bitmap == null) {
            callback.onResult(VisionResult.unavailable("ML Kit", "нет картинки"));
            return;
        }

        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

            ObjectDetectorOptions objectOptions =
                    new ObjectDetectorOptions.Builder()
                            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                            .enableMultipleObjects()
                            .enableClassification()
                            .build();

            ObjectDetector detector = ObjectDetection.getClient(objectOptions);

            Task<List<ImageLabel>> labelTask = labeler.process(image);
            Task<List<DetectedObject>> objectTask = detector.process(image);

            Tasks.whenAllComplete(labelTask, objectTask)
                    .addOnSuccessListener(tasks -> {
                        ArrayList<VisionLabel> labels = new ArrayList<>();
                        ArrayList<VisionObject> objects = new ArrayList<>();
                        StringBuilder message = new StringBuilder();

                        if (labelTask.isSuccessful() && labelTask.getResult() != null) {
                            for (ImageLabel label : labelTask.getResult()) {
                                labels.add(new VisionLabel(label.getText(), label.getConfidence(), "mlkit-label"));
                            }
                        } else if (labelTask.getException() != null) {
                            message.append("labels: ").append(labelTask.getException().getMessage()).append("; ");
                        }

                        if (objectTask.isSuccessful() && objectTask.getResult() != null) {
                            for (DetectedObject object : objectTask.getResult()) {
                                Rect box = object.getBoundingBox();
                                RectF norm = new RectF(
                                        clamp(box.left / (float) bitmap.getWidth()),
                                        clamp(box.top / (float) bitmap.getHeight()),
                                        clamp(box.right / (float) bitmap.getWidth()),
                                        clamp(box.bottom / (float) bitmap.getHeight())
                                );

                                ArrayList<VisionLabel> objectLabels = new ArrayList<>();
                                String labelText = "object";
                                float confidence = 0.55f;

                                for (DetectedObject.Label l : object.getLabels()) {
                                    VisionLabel vl = new VisionLabel(l.getText(), l.getConfidence(), "mlkit-object");
                                    objectLabels.add(vl);
                                    if (l.getConfidence() >= confidence) {
                                        confidence = l.getConfidence();
                                        labelText = l.getText();
                                    }
                                }

                                objects.add(new VisionObject(norm, confidence, labelText, "mlkit-object", objectLabels));
                            }
                        } else if (objectTask.getException() != null) {
                            message.append("objects: ").append(objectTask.getException().getMessage()).append("; ");
                        }

                        Collections.sort(labels, new Comparator<VisionLabel>() {
                            @Override public int compare(VisionLabel a, VisionLabel b) {
                                return Float.compare(b.confidence, a.confidence);
                            }
                        });

                        String msg = message.length() == 0 ? "анализ текущего изображения" : message.toString();
                        callback.onResult(new VisionResult("ML Kit", true, msg, labels, objects));
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
    private static String solumDatasetRoute27E(Object labels, Object objects, String extra) {
        try {
            DatasetClassRouter.Route route = DatasetClassRouter.route(labels, objects, extra);
            return route.summary + "\nПричины: " + route.reason;
        } catch (Throwable t) {
            return "dataset_class: unknown | router error: " + t.getClass().getSimpleName();
        }
    }

}
