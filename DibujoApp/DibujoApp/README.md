# DibujoApp v0.1

App de dibujo estilo Procreate, construida en Kotlin + Jetpack Compose.
Versión intermedia inicial: pincel con color y grosor ajustable, deshacer/rehacer, exportar a PNG.

## Cómo compilar el APK (sin PC, desde el móvil)

1. Crea un repositorio nuevo en GitHub (ej: `dibujoapp`).
2. Sube todo el contenido de esta carpeta al repo:
   - Opción fácil: abre el repo en GitHub, usa "Add file → Upload files" y arrastra estos archivos manteniendo la estructura de carpetas.
   - Opción recomendada: abre un Codespace vacío del repo, sube este .zip a la raíz, y en la terminal ejecuta:
     ```
     unzip DibujoApp.zip -d .
     mv DibujoApp/* DibujoApp/.github .
     rm -rf DibujoApp DibujoApp.zip
     git add .
     git commit -m "v0.1 lienzo basico"
     git push
     ```
3. En GitHub, ve a la pestaña **Actions** del repo. El workflow "Build APK" se dispara solo al hacer push a `main`.
4. Cuando termine (unos minutos), entra al run finalizado y descarga el artefacto `dibujoapp-debug` — ahí está el `app-debug.apk`.
5. Instala el APK en tu Android (activa "Instalar apps de fuentes desconocidas" si te lo pide).

## Qué incluye esta versión

- Lienzo a pantalla completa, dibujar con el dedo
- Selector de color (paleta básica de 9 colores)
- Control de grosor del pincel (slider)
- Deshacer / Rehacer
- Botón "Borrar todo"
- Guardar el dibujo como PNG en Galería > DibujoApp

## Arquitectura (pensada para crecer)

- `DrawnStroke`: representa un trazo individual (path + color + grosor). No guardamos bitmaps, guardamos "comandos" — esto es clave para que deshacer/rehacer sea barato en memoria y para que el futuro sistema de capas sea simplemente listas de `DrawnStroke` independientes.
- `DrawingViewModel`: estado central — lista de trazos, pila de rehacer, configuración de pincel actual.
- `DrawingScreen`: UI — el Canvas de Compose, la barra de herramientas, exportación a PNG vía MediaStore.

## Roadmap

- [x] v0.1 — lienzo, un pincel, deshacer/rehacer, guardar PNG
- [ ] v0.2 — más tipos de pincel (opacidad, dureza de borde, textura)
- [ ] v0.3 — sistema de capas (crear, ocultar, reordenar, fusionar)
- [ ] v0.4 — zoom/pan, goma de borrar real, selector de color HSV
- [ ] v0.5+ — pinceles con textura, exportación por capas, optimización de rendimiento con canvas grandes
