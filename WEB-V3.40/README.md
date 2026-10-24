# Rutas Moteras · Web/PWA V3.40

Interfaz web y PWA de Rutas Moteras · Fuentes de Andalucía. Es una aplicación **solo online**: mapas, rutas, elevación, POI, clima y fotos remotas necesitan Internet.

## Ejecutar

```powershell
npm install
npm run dev
```

Servidor local: `http://localhost:3000`.

## Tests

```powershell
npm test
```

`scripts/test_core.mjs` extrae helpers del `index.html` y valida datos, versiones y recursos. Mantén los nombres `KEYS`, `simplifyPolyline`, `serializeEndpoint`, `restoreEndpoint` y `migrateLegacyStorage` extraíbles.

## Archivos principales

- `index.html`: aplicación completa, UI y lógica en un solo archivo.
- `fuentes_data.js`, `photos_data.js`, `access_data.js`: datos publicados.
- `municipios_andalucia.js`, `pueblos_andalucia.js`, `postal_andalucia.js`: datos auxiliares.
- `manifest.json`, `sw.js`, `icon-192.png`, `icon-512.png`: PWA.
- `server.js`: servidor Express local.

La aplicación no usa caché offline. `sw.js` solo elimina la caché antigua y se desregistra; no añadir caché offline.

## APK Android

`APK-Android/Rutas-Moteras-3.40.apk` es una reconstrucción de la APK Capacitor a partir de la base móvil 3.39 y los datos actuales. Detalles y SHA en `APK-Android/README.md`. No es una tercera línea de código: la app nativa independiente sigue en `NATIVA-V1.8/`.

## Versión y publicación

- La versión web está en `const RM_VERSION` de `index.html` y en `package.json`/`manifest.json`.
- URL: `https://lcj355.github.io/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/WEB-V3.40/`
- La raíz del repositorio redirige a esta carpeta para conservar el enlace anterior.
- Publicar mediante Git/GitHub Pages. No guardar tokens en el proyecto; cualquier token temporal debe ser fine-grained y limitado a este repositorio.

## Datos

`fuentes_data.js` se genera con el pipeline externo de Access en `G:\-IA-\FUENTES-Access\` (`FUENTES_Andalucia.accdb -> export_rutas.py -> fuentes_data.js`). No regenerarlo desde esta carpeta.

Los datos de la app Android nativa se generan desde estos mismos archivos con `NATIVA-V1.8/tools/build_native_data.mjs`.
