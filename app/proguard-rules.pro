# kotlinx.serialization: keep generated serializers and the Companion lookups used at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nulis.launcher.**$$serializer { *; }
-keepclassmembers class com.nulis.launcher.** { *** Companion; }
-keepclasseswithmembers class com.nulis.launcher.** { kotlinx.serialization.KSerializer serializer(...); }
