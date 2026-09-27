# Proguard rules for JK BMS Dual Monitor
-keepattributes *Annotation*
-dontwarn javax.annotation.**
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
