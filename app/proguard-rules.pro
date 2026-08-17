-dontwarn org.bouncycastle.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep Compose-generated and app UI classes
-keep class ai.deepseek.harness.ui.** { *; }
-keepclassmembers class ai.deepseek.harness.ui.** { *; }
-keep class ai.deepseek.harness.ui.design.** { *; }
-keep class ai.deepseek.harness.ui.image.** { *; }

# Keep app packages
-keep class ai.deepseek.harness.chat.** { *; }
-keep class ai.deepseek.harness.dsh.** { *; }
-keep class ai.deepseek.harness.**$Companion { *; }

# Keep data classes used by serialization
-keep class ai.deepseek.harness.chat.ChatMessage
-keep class ai.deepseek.harness.chat.ChatMessageContent
-keep class ai.deepseek.harness.chat.ChatSessionEntry
-keepclassmembers class ai.deepseek.harness.chat.ChatSessionEntry { *; }

# Keep Compose annotations
-keepattributes androidx.compose.runtime.Composable
-keepclassmembers class ** { @androidx.compose.runtime.Composable <methods>; }

# Keep R8 from stripping Compose state
-keepclassmembers class ai.deepseek.harness.** { @androidx.compose.runtime.State <fields>; }

# Keep reflection entry points
-keep class ai.deepseek.harness.MainActivity
-keep class ai.deepseek.harness.NodeApp
-keepclassmembers class ai.deepseek.harness.NodeApp { *; }