#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_com_solum_draw_cv_OpenCvMobileNative_nativeBackendName(JNIEnv* env, jclass) {
    std::string name = "opencv-mobile-static ";
    name += CV_VERSION;
    return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_solum_draw_cv_OpenCvMobileNative_nativeSmokeTest(JNIEnv*, jclass) {
    cv::Mat src(8, 8, CV_8UC1, cv::Scalar(0));
    cv::rectangle(src, cv::Rect(2, 2, 4, 4), cv::Scalar(255), -1);
    cv::Mat edges;
    cv::Canny(src, edges, 50, 120);
    return cv::countNonZero(edges);
}
