package com.solum.draw.vision.profile;

public final class VisionRouterConfig {
    private VisionRouterConfig() {}

    // Patch 28E:
    // true = use ShadowClassRouter result for app predict if it is valid.
    // false = old VisionDecisionEngine result only.
    public static final boolean USE_CLASS_ROUTER_FOR_PREDICT = true;
}
