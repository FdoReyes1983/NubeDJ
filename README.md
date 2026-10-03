# Nube DJ v0.2.0

Segunda iteración del prototipo DJ para Android, enfocada en una experiencia visual de cabina y biblioteca en nube.

## Incluido en v0.2

- Dos decks A/B simultáneos con crossfader equal-power.
- Waveform desplazable visualmente durante la reproducción.
- Análisis automático local de BPM y forma de onda mediante MediaCodec.
- Jog wheels visuales sincronizados con la posición de reproducción.
- CUE / SET / PLAY / PAUSA / SYNC / TAP.
- Pitch/tempo 75–125%.
- Mixer central con VU meters y ecualización LOW/MID/HIGH por deck.
- Biblioteca Google Drive integrada con Mi Drive, Compartidos, carpetas, búsqueda y botones LOAD A / LOAD B.
- Las canciones de Drive se descargan a caché privada antes de reproducirse y analizarse.
- Sin Toasts rutinarios: el estado se muestra dentro de cada deck.
- GitHub Actions para generar la APK debug desde el teléfono.

## Google Drive

Consulta `docs/GOOGLE_DRIVE.md`. El nuevo paquete `cl.fernando.nubedj` debe registrarse como cliente OAuth Android usando la SHA-1 de la clave incluida.

## Compilación

GitHub Actions genera el artefacto `Nube-DJ-v0.2.0-debug` en cada push a `main`.
