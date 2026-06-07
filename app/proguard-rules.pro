-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**
-keep class com.nothing.thirdparty.** { *; }
-dontwarn com.nothing.thirdparty.**

# Keep models for Firebase Realtime Database
-keep class com.better.nothing.music.vizualizer.model.** { *; }
-keepclassmembers class com.better.nothing.music.vizualizer.model.** {
    <init>(...);
    private <fields>;
    public <fields>;
}
