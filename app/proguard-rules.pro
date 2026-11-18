# Google Mobile Ads and UMP ship consumer ProGuard rules; these keeps are a
# safety net for reflective SDK entry points.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**
