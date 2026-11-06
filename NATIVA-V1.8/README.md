# Rutas Moteras · Android nativo V1.9.1

App Android nativa de Rutas Moteras · Fuentes de Andalucía. Es una línea independiente de la web/PWA.

- Versión: **1.9.1** (`versionCode 11`)
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

Copia publicada: `APKs\RutasFuentes-v1.9.1.apk`.

## Firma

La build release usa un keystore propio:

- `keystore/rutasfuentes-release.jks` (ignorado por Git)
- `keystore.properties` (ignorado por Git)

**Haz copia de seguridad de ambos ficheros.** Si se pierden, no se podrán
publicar actualizaciones instalables sobre la app existente.

> Importante: las versiones 1.8 y anteriores estaban firmadas con la clave
> debug. La 1.9 usa la clave release nueva, así que **no se instala encima de
> 1.8**: hay que exportar la copia de datos, desinstalar 1.8, instalar 1.9 e
> importar la copia. A partir de 1.9 las actualizaciones sí son directas.

## Actualizaciones

Al iniciar, la app consulta:

```
https://github.com/LCJ355/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/releases/latest/download/update.json
```

Si `versionCode` es mayor que el instalado, ofrece descargar e instalar el APK
desde GitHub Releases. El APK se verifica con SHA-256 y con el paquete/versión
antes de abrir el instalador. También hay un botón manual **🔄 Buscar
actualización** en Acerca de.

Publicar una versión nueva:

1. Subir `versionCode` y `versionName` en `app/build.gradle`.
2. Compilar `assembleRelease` y copiar el APK a `APKs/`.
3. Crear un release **como borrador** con un tag nuevo (`v1.10.0`, etc.):
   las releases publicadas son inmutables y no admiten assets nuevos.
4. Subir al borrador los assets `RutasFuentes.apk` y `update.json` (raíz del repo).
5. Publicar el release (no borrador ni prerelease); `latest/download` pasará a apuntar a él.

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

- `app/src/main/java/com/lcj355/rutasfuentes/MainActivity.kt`: UI, mapa, rutas, GPS, APIs, paneles y actualizaciones.
- `CompassRoseView.kt`: rosa de brújula.
- `app/src/main/res/`: iconos, temas y sonido de arranque.
- `APKs/`: APK de referencia V1.9.1.
- `data_overrides.json`: correcciones de datos nativos.
- `gradle/wrapper/`: no borrar; necesario para `gradlew`.

## Notas

- La app es solo online para mapas, rutas, clima y POI.
- El sensor solo se registra en la pantalla de mapa y el GPS procesa fixes nuevos.
- Para publicar en Google Play hay que configurar firma release propia.
