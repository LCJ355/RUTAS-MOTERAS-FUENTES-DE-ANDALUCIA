# APK Android V-3.40 (reconstruida)

APK Capacitor de la web, recuperada a partir de la base móvil 3.39 y los datos actuales de `WEB-V3.40`.

- Archivo: `Rutas-Moteras-3.40.apk`
- Paquete: `com.lcj355.rutasmoteras`
- Versión visible: `V-3.40`
- `versionCode`: 61
- Firma: debug (instalable, no apta para Google Play)
- SHA-256: `06834EEE7BE1BBE70868F34CD8C37CF77CC108374DCCF4306381D594E7395EEB`

## Qué se reconstruyó

La APK 3.39 contenía el HTML móvil y los datos embebidos. Se extrajo esa base, se actualizaron los datos compartidos desde `WEB-V3.40` y se cambió la versión a 3.40. Después se generó el contenedor Android con Capacitor 7.4.3.

La APK se probó en el OPPO: arranca, pide permiso de ubicación, carga el mapa y muestra `V-3.40`; la ayuda integrada abre correctamente.

## Limitaciones

- Es una reconstrucción, no el binario original borrado.
- La base de interfaz móvil es la de 3.39; los datos son los actuales de 3.40.
- No se conserva aquí el proyecto Android completo para no reintroducir una tercera línea de código. La app nativa sigue en `NATIVA-V1.8/`.
- Si se necesita modificar esta APK, hay que volver a generar un proyecto Capacitor con `appId com.lcj355.rutasmoteras`, `appName Rutas Moteras` y esta carpeta como `webDir`.

## Instalar

```powershell
C:\Android\sdk\platform-tools\adb.exe install -r Rutas-Moteras-3.40.apk
```

## Histórico

La APK de la línea anterior se conserva sin mantenimiento:

- `historicos/Rutas-Moteras-3.39.apk` (`com.lcj355.rutasmoteras`, versionCode 60).
