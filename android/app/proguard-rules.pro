# NusaTunnel — keep rules engine gobind (Xray-core via AndroidLibXrayLite)
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nusa.tunnel.**$$serializer { *; }
-keepclassmembers class com.nusa.tunnel.** { *** Companion; }
-keepclasseswithmembers class com.nusa.tunnel.** { kotlinx.serialization.KSerializer serializer(...); }
