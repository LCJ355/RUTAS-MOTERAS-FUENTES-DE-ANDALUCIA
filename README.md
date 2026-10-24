# Rutas Moteras - Fuentes de Andalucía

Planificador web de rutas moteras por fuentes y manantiales de Andalucía.

La aplicación es **solo online**. Necesita conexión a Internet para cargar los mapas y consultar las APIs de rutas, elevación, puntos de interés y meteorología.

## Datos actuales

- FD: 13,719 fuentes
- TC: 712 pueblos con coordenadas
- TP: 711 asociaciones de pueblo y provincia

Pipeline de datos actual:

`FUENTES_Andalucia.accdb -> export_rutas.py -> fuentes_data.js`

`export_rutas.py` genera `PROVS`, `TC`, `TP` y `FD` directamente desde la base de datos Access.

## Publicación

Web pública: https://lcj355.github.io/RUTAS-MOTERAS-FUENTES-DE-ANDALUCIA/

La publicación normal se realiza mediante Git y GitHub Pages. La publicación desde el navegador a través de la API de GitHub es una solución temporal: usa únicamente un token fine-grained limitado a este repositorio y guárdalo solo durante la sesión. No uses nunca un token clásico con permisos amplios.
