# F7 Download Manager - R8 rules
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn org.jetbrains.annotations.**

# WebView Javascript bridges (none are exposed, but keep the API from being stripped
# if a future page bridge is added behind an explicit allowlist).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
