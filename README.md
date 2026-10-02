# Nube DJ 0.1.0

Primer MVP de una aplicación DJ para Android, creada como proyecto separado de Nube Música.

## Funciona en esta versión

- Dos decks A/B independientes con reproducción simultánea.
- Carga de audio mediante el selector de documentos de Android.
  - Permite archivos locales.
  - Puede mostrar Google Drive si Drive está disponible como proveedor de documentos en el dispositivo.
- Play/Pausa por deck.
- Cue y Set Cue por deck.
- Seek de pista.
- Volumen independiente por deck.
- Tempo/Pitch de reproducción de 75% a 125%.
- TAP BPM manual por deck.
- SYNC A <- B y B <- A basado en los BPM marcados con TAP.
- Crossfader equal-power A/B.
- Interfaz horizontal pensada para teléfono/tablet.
- Pantalla activa durante la mezcla.

## Limitaciones deliberadas del MVP

Todavía no incluye scratching, waveform real, EQ de 3 bandas, filtros, loops, hot cues múltiples, preescucha por audífonos, MIDI, stems ni conexión MEGA directa. Esas funciones necesitan un motor de audio de menor latencia y una arquitectura más cercana a una aplicación DJ profesional.

## Identidad Android

- applicationId: `cl.fernando.nubedj`
- versionCode: `1`
- versionName: `0.1.0`
- minSdk: 28
- targetSdk: 36
- Media3: 1.9.4

Puede instalarse junto a Nube Música porque utiliza un applicationId distinto.

## Compilar

Abrir el proyecto en Android Studio y ejecutar `app`, o ejecutar:

```bash
./gradlew assembleDebug
```

La APK se genera normalmente en:

`app/build/outputs/apk/debug/app-debug.apk`

El proyecto también incluye `.github/workflows/android-debug.yml` para compilar automáticamente una APK debug mediante GitHub Actions.
