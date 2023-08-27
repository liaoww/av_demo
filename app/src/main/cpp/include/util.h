#include <jni.h>
#include <string.h>

jstring charToJString(JNIEnv *env, const char *pat);
char* jStringToChar(JNIEnv* env, jstring jstr);