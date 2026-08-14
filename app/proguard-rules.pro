-dontwarn org.bouncycastle.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor

# Keep the GitHub update-checker serializable models intact for R8 release builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep class ai.deepseek.harness.update.** { *; }
-keepclassmembers class ai.deepseek.harness.update.** { *; }
