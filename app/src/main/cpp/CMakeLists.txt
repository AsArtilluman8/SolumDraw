cmake_minimum_required(VERSION 3.22.1)
project(solumdraw_cv LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED OPENCV_MOBILE_NATIVE_DIR)
    set(OPENCV_MOBILE_NATIVE_DIR "$ENV{HOME}/third_party/opencv-mobile-android/opencv-mobile-4.13.0-android/sdk/native")
endif()

set(OPENCV_INCLUDE_DIR "${OPENCV_MOBILE_NATIVE_DIR}/jni/include")
set(OPENCV_STATIC_DIR "${OPENCV_MOBILE_NATIVE_DIR}/staticlibs/${ANDROID_ABI}")
set(OPENCV_3RDPARTY_DIR "${OPENCV_MOBILE_NATIVE_DIR}/3rdparty/libs/${ANDROID_ABI}")

if(NOT EXISTS "${OPENCV_INCLUDE_DIR}/opencv2/core.hpp")
    message(FATAL_ERROR "OpenCV mobile headers not found: ${OPENCV_INCLUDE_DIR}. Download opencv-mobile-4.13.0-android.zip into ~/third_party/opencv-mobile-android first.")
endif()

if(NOT EXISTS "${OPENCV_STATIC_DIR}/libopencv_core.a")
    message(FATAL_ERROR "OpenCV mobile static libs not found: ${OPENCV_STATIC_DIR}. Current ABI=${ANDROID_ABI}")
endif()

add_library(solumdraw_cv SHARED solumdraw_cv_jni.cpp)

target_include_directories(solumdraw_cv PRIVATE ${OPENCV_INCLUDE_DIR})

target_link_libraries(solumdraw_cv
        ${OPENCV_STATIC_DIR}/libopencv_imgproc.a
        ${OPENCV_STATIC_DIR}/libopencv_core.a
        ${OPENCV_3RDPARTY_DIR}/libkleidicv_hal.a
        ${OPENCV_3RDPARTY_DIR}/libkleidicv.a
        ${OPENCV_3RDPARTY_DIR}/libkleidicv_thread.a
        android
        log
        z
        jnigraphics)
