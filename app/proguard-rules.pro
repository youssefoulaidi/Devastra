# PDFBox-Android uses reflection on some font/graphics classes.
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
