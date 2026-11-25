-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.jotnar.materialisland.**$$serializer { *; }
-keepclassmembers class dev.jotnar.materialisland.** { *** Companion; }
-keepclasseswithmembers class dev.jotnar.materialisland.** { kotlinx.serialization.KSerializer serializer(...); }
# Reflexión sobre APIs ocultas de ViewTreeObserver (región táctil del overlay)
-keep class android.view.ViewTreeObserver$** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
