#include <malloc.h>
#include "util.h"

jstring charToJString(JNIEnv *env, const char *pat) {
    jclass strClass = (*env).FindClass("java/lang/String");
    jmethodID ctorID = (*env).GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jbyteArray bytes = nullptr;
    if (pat != nullptr) {
        bytes = (*env).NewByteArray(strlen(pat));
        (*env).SetByteArrayRegion(bytes, 0, strlen(pat), (jbyte *) pat);
    } else {
        char *unknown = const_cast<char *>("unknown decoder");
        bytes = (*env).NewByteArray(strlen(unknown));
        (*env).SetByteArrayRegion(bytes, 0, strlen(unknown), (jbyte *) pat);
    }
    jstring encoding = (*env).NewStringUTF("utf-8");
    return (jstring) (*env).NewObject(strClass, ctorID, bytes, encoding);
}

char *jStringToChar(JNIEnv *env, jstring jstr) {
    char *rtn = nullptr;
    jclass clsstring = env->FindClass("java/lang/String");
    jstring strencode = env->NewStringUTF("GB2312");
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray barr = (jbyteArray) env->CallObjectMethod(jstr, mid, strencode);
    jsize alen = env->GetArrayLength(barr);
    jbyte *ba = env->GetByteArrayElements(barr, JNI_FALSE);
    if (alen > 0) {
        rtn = (char *) malloc(alen + 1);
        memcpy(rtn, ba, alen);
        rtn[alen] = 0;
    }
    env->ReleaseByteArrayElements(barr, ba, 0);
    return rtn;
}