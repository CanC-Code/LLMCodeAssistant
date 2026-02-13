# 1. Protect the JNI Bridge and its internal structures
# This ensures llama_jni.cpp can find the LlamaBridge object
-keep class io.canccode.aca.LlamaBridge {
    native <methods>;
    *;
}

# 2. Protect the Callback Interface
# This is critical! JNI GetMethodID uses the names 'onToken', 'onComplete', and 'onError'.
# If these are renamed to a(), b(), c(), the app will crash during inference.
-keep interface io.canccode.aca.LlamaBridge$GenerateCallback {
    *;
}

# 3. Protect the implementation of the callback in Fragments/Activities
-keep class * implements io.canccode.aca.LlamaBridge$GenerateCallback {
    public void onToken(java.lang.String);
    public void onComplete(java.lang.String);
    public void onError(java.lang.String);
}

# 4. Protect ModelManager and ProjectLoader
# These are accessed via logic that may rely on specific class names
-keep class io.canccode.aca.ModelManager { *; }
-keep class io.canccode.aca.ProjectLoader { *; }

# 5. General JNI Housekeeping
# Keeps all native method declarations across the app
-keepclasseswithmembernames class * {
    native <methods>;
}

# 6. Library Specifics
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Enums for type-safe settings if used
-keepclassmembers enum * { *; }

# Prevent shrinking of Kotlin-specific metadata that might be needed for reflection
-keep class kotlin.Metadata { *; }
