# TIRUp Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}

# Keep ViewModels and AOD components
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.tirup.app.presentation.aod.** { *; }

