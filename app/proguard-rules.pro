# ProGuard rules for Manga GUI
-keep class com.mangagui.app.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

