package com.solum.draw.vision.profile;

public final class ImageAxes {
    private ImageAxes() {}

    public enum StyleAxis {
        LINEART,
        PAINTERLY,
        FLAT_VECTOR,
        PIXEL,
        PHOTO_REALISTIC,
        ABSTRACT,
        TEXTURED,
        UNKNOWN
    }

    public enum ContentAxis {
        CHARACTER,
        CREATURE,
        LANDSCAPE,
        ARCHITECTURE,
        UI_ELEMENT,
        DIAGRAM,
        LOGO,
        PATTERN,
        TEXT_BLOCK,
        PRODUCT_OBJECT,
        VFX_EFFECT,
        UNKNOWN
    }

    public enum PurposeAxis {
        ILLUSTRATION,
        REFERENCE_PHOTO,
        UI_SCREENSHOT,
        DOCUMENT,
        GAME_ASSET,
        DECORATIVE_PATTERN,
        UNKNOWN
    }

    public enum QualityAxis {
        CLEAN,
        NOISY_COMPRESSED,
        LOW_RES,
        SKETCH_ROUGH,
        UNKNOWN
    }
}
