# Keep all Kotlin classes (for reflection/serialization if used)
-keep class kotlin.** { *; }

# Keep all JNI classes
-keep class com.llmassistant.llm.** { *; }
-keep class com.llmassistant.ui.** { *; }
-keep class com.llmassistant.editor.** { *; }

# Keep all native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep annotations
-keepattributes *Annotation*

# Keep enums
-keepclassmembers enum * { *; }