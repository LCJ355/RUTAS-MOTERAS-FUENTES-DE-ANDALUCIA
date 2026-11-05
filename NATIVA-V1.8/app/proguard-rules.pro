# Rutas Fuentes - reglas R8 (APK ofuscada)

# osmdroid usa reflexion en algunos puntos (tile sources, atributos)
-keep class org.osmdroid.** { *; }
-keep class org.osmdroid.views.MapView { *; }
-dontwarn org.osmdroid.**

# okhttp / TLS opcionales
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin metadata y corrutinas
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.coroutines.**

# FileProvider y componentes del manifest se conservan automaticamente
