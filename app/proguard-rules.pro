-keep class io.canccode.aca.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }
-keep class kotlin.** { *; }
