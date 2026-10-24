# Rutas Moteras · Fuentes de Andalucía

Repositorio con dos líneas de evolución independientes:

| Línea | Carpeta | Estado |
|---|---|---|
| Web/PWA | `WEB-V3.40/` | Versión 3.40; solo online |
| Web/PWA nativa | `WEB-V1.8/` | Versión 1.8; carcasa basada en la app nativa |
| Android nativo | `NATIVA-V1.8/` | Versión 1.8 (`versionCode 9`); Kotlin/osmdroid |

La antigua línea APK Capacitor 3.40 se retiró; se ha recuperado una APK instalable en `WEB-V3.40/APK-Android/` a partir de la base 3.39 y los datos actuales.

La raíz conserva un `index.html` mínimo de compatibilidad que redirige a `WEB-V3.40/`, para que la URL publicada anteriormente siga funcionando.

## Web

```powershell
cd WEB-V3.40
npm install
npm run dev        # http://localhost:3000
npm test
```

La web publicada queda en:

`https://lcj355.github.io/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/WEB-V3.40/`

Los datos de fuentes, fotos, accesos, municipios y códigos postales están dentro de `WEB-V3.40/`. El pipeline de datos de Access es externo: `G:\-IA-\FUENTES-Access\`.

## Web V-1.8 (carcasa nativa)

```powershell
cd WEB-V1.8
npm run dev        # http://127.0.0.1:8081
npm test
```

Publicada en:

`https://lcj355.github.io/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/WEB-V1.8/`

Reutiliza el motor móvil del APK 3.39 y los datos actuales, con una carcasa nueva basada en `NATIVA-V1.8` (barra superior, búsqueda, capas, clima, brújula, barra de ruta y cinco accesos inferiores). La raíz sigue redirigiendo a `WEB-V3.40/` para no romper enlaces antiguos.

## Android nativo

```powershell
cd NATIVA-V1.8
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:ANDROID_HOME="C:\Android\sdk"
.\gradlew.bat --no-daemon assembleRelease
```

Salida: `NATIVA-V1.8\app\build\outputs\apk\release\app-release.apk`.

El APK de referencia está en `NATIVA-V1.8\APKs\RutasFuentes-v1.8.apk`. La build release usa firma de debug y R8; no es apta para Google Play sin configurar un keystore propio.

## Datos nativos

`NATIVA-V1.8\app\src\main\assets\data.json` es un snapshot generado desde los datos de la web. Para regenerarlo:

```powershell
cd NATIVA-V1.8
node tools/build_native_data.mjs
node tools/build_native_data.mjs --check
```

El generador lee `..\WEB-V3.40` y aplica `data_overrides.json`. No editar `data.json` a mano.

## Publicación

Publicación web normal mediante Git/GitHub Pages. No guardar tokens en el repositorio; si se usa publicación temporal desde el navegador, debe ser un token fine-grained limitado a este repositorio.
