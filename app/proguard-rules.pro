# 1. Project-Specific: Keep your JNI bridge classes
# This must match your 'namespace' in build.gradle
-keep class io.canccode.aca.** { *; }

# 2. Native: Keep all native method declarations across the app
-keepclasseswithmembernames class * {
    native <methods>;
}

# 3. Kotlin: Keep Kotlin-specific metadata (important for some library reflections)
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# 4. Standard Android: Keep common attributes and Enums
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }

# 5. Model/Data Classes: If you add GSON or JSON parsing later,
# add rules to keep those specific data models here.
