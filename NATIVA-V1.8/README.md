# Rutas Moteras · Android nativo V1.8

App Android nativa de Rutas Moteras · Fuentes de Andalucía. Es una línea independiente de la web/PWA.

- Versión: **1.8** (`versionCode 9`)
- Paquete: `com.lcj355.rutasfuentes`
- Kotlin + AndroidX + osmdroid, una sola `MainActivity` programática (sin layouts XML) y `CompassRoseView`.

## Compilar

Requisitos: JDK 21 y Android SDK 35.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:ANDROID_HOME="C:\Android\sdk"
.\gradlew.bat --no-daemon assembleRelease
```

Salida: `app\build\outputs\apk\release\app-release.apk`.

Copia publicada: `APKs\RutasFuentes-v1.8.apk`.

La build release usa `signingConfigs.debug` con R8/shrinking. No es apta para Google Play sin un keystore propio.

## Datos

`app/src/main/assets/data.json` es un snapshot generado; no editarlo a mano. Las correcciones locales van en `data_overrides.json`.

El generador está en `tools/` y lee los datos de la web hermana:

```powershell
node tools\build_native_data.mjs
node tools\build_native_data.mjs --check
```

Rutas esperadas:

```
..\WEB-V3.40\fuentes_data.js
..\WEB-V3.40\municipios_andalucia.js
..\WEB-V3.40\postal_andalucia.js
..\WEB-V3.40\pueblos_andalucia.js
```

Si mueves esta carpeta, ajusta `webRoot` en `tools\build_native_data.mjs`.

## Estructura

- `app/src/main/java/com/lcj355/rutasfuentes/MainActivity.kt`: UI, mapa, rutas, GPS, APIs, paneles.
- `CompassRoseView.kt`: rosa de brújula.
- `app/src/main/res/`: iconos, temas y sonido de arranque.
- `APKs/`: APK de referencia V1.8.
- `data_overrides.json`: correcciones de datos nativos.
- `gradle/wrapper/`: no borrar; necesario para `gradlew`.

## Notas

- La app es solo online para mapas, rutas, clima y POI.
- El sensor solo se registra en la pantalla de mapa y el GPS procesa fixes nuevos.
- Para publicar en Google Play hay que configurar firma release propia.
