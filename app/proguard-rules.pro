# Prevent Proguard from stripping/renaming your JNI bridge
-keep class io.canccode.aca.** { *; }

# Maintain native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# General Android and Kotlin safety
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }
-keep class kotlin.** { *; }
