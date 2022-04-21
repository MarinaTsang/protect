/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>

#ifndef jungle_battery_fast_utils_AES_h
#define jungle_battery_fast_utils_AES_h
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     jungle_battery_fast_utils_AESHelper
 * Method:    encrypt
 * Signature: ([BI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_jungle_battery_fast_utils_AESHelper_encrypt
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     jungle_battery_fast_utils_AESHelper
 * Method:    decrypt
 * Signature: ([BI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_jungle_battery_fast_utils_AESHelper_decrypt
  (JNIEnv *, jclass, jbyteArray);

/*
 *
 */
JNIEXPORT jstring JNICALL Java_jungle_battery_fast_utils_AESHelper_key
        (JNIEnv *env, jclass clazz);

#ifdef __cplusplus
}
#endif
#endif