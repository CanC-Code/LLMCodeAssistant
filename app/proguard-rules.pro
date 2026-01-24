# Project-Specific: Keep your JNI bridge classes
# This matches the namespace 'io.canccode.aca' defined in your build.gradle
-keep class io.canccode.aca.** { *; }

# Native: Keep all native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}

# General Safety: Keep standard attributes and enums
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }

# Kotlin: Metadata safety for reflection
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
