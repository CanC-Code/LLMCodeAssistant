# [span_15](start_span)Prevent Proguard from stripping/renaming your JNI bridge[span_15](end_span)
-keep class io.canccode.aca.** { *; }

# [span_16](start_span)Maintain native method signatures[span_16](end_span)
-keepclasseswithmembernames class * {
    native <methods>;
}

# [span_17](start_span)General Android and Kotlin safety[span_17](end_span)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }
-keep class kotlin.** { *; }
