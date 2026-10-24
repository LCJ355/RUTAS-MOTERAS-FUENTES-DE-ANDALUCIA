# Rutas Moteras V-1.8 · Fuentes de Andalucía

Web/PWA basada en la app nativa **NATIVA-V1.8**. Sustituye la interfaz móvil de `WEB-V3.40` por una carcasa nativa con mapa a pantalla completa, barra superior, brújula, barra de ruta y navegación inferior de cinco accesos.

## Ejecutar

```powershell
cd "G:\-IA-\Rutas Moteras -Fuentes de Andalucía-\WEB-V1.8"
npm run dev
# http://127.0.0.1:8081/
npm test
```

No requiere instalación: el servidor usa solo Node.js y Leaflet/MarkerCluster están vendorizados en `vendor/`.

## Publicación

GitHub Pages sirve la carpeta en:

`https://lcj355.github.io/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/WEB-V1.8/`

El `manifest.json` y el Service Worker usan rutas relativas para funcionar bajo esa subcarpeta. `sw.js` cachea la carcasa y los datos; mapas, rutas, clima, POI y teselas siguen necesitando Internet.

## Base reutilizada

- Motor funcional móvil del APK **3.39** (`index.html`): fuentes, filtros, búsqueda, rutas OSRM, elevación, GPX, POI, clima, fotos, notas, accesos, copias de seguridad y publicación GitHub.
- Datos actuales de `WEB-V3.40`: 13.719 fuentes, 810 pueblos/pedanías y 790 códigos postales.
- Carcasa nueva `native.css` + `native.js`: top bar, buscador nativo, capas, clima, brújula, diana, zoom, barra de ruta y cinco botones inferiores.
- Iconos y aguja de la app nativa copiados a `assets/`.

## Estado de paridad

Implementado:

- Mapa Leaflet a pantalla completa y clústeres de fuentes.
- Búsqueda de fuentes, pueblos y CP con panel nativo.
- Capas OSM, Topo, CyclOSM y satélite.
- Clima actual del centro del mapa.
- GPS, centrar ubicación, seguimiento y brújula con sensor (o rumbo GPS).
- Panel de fuentes, panel de ruta, POIs, clima de ruta, GPX, rutas guardadas, fotos, notas, accesos y backup.
- Barra de ruta con distancia/tiempo y perfil de elevación.
- PWA con caché de carcasa/datos.

Pendiente / limitado por navegador:

- Navegación giro a giro y GPS en segundo plano como en Android.
- Rotación nativa del mapa con dos dedos (la rosa muestra rumbo, no gira el mapa).
- Paneles internos de ruta/fuentes conservan el diseño del motor 3.39; la carcasa y el flujo móvil sí son nativos.
- Grabación de ruta y carpetas/intents de fotos nativos.
- Clima/POI/radar dependen de servicios externos y de conexión.

## Estructura

- `index.html`: motor móvil + enlaces a la carcasa nativa.
- `native.css`: tema nativo, top bar, bottom bar, paneles, rosa y hojas.
- `native.js`: interacción de la carcasa, búsqueda, capas, clima, brújula y rutas.
- `vendor/`: Leaflet 1.9.4 y MarkerCluster 1.5.3.
- `assets/`: logo y aguja de la app nativa.
- `fuentes_data.js`, `photos_data.js`, `access_data.js`: datos publicados.
- `sw.js`: caché básica offline.
- `tests/smoke.mjs`: comprobaciones estáticas.
