# jjwt usa reflexión para cargar sus implementaciones de servicios.
-keep class io.jsonwebtoken.** { *; }
-keepnames class io.jsonwebtoken.** { *; }
-dontwarn io.jsonwebtoken.**

# Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Retrofit / OkHttp / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }
