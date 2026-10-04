# Nube DJ v0.2.1

## Cambios de interacción
- CUE momentáneo estilo DJ: al mantener presionado reproduce desde el punto CUE; al soltar vuelve al CUE. Pulsaciones repetidas generan efecto stutter.
- Waveform táctil:
  - tocar/arrastrar en la forma de onda principal mueve la reproducción dentro de la ventana visible;
  - nueva mini-vista inferior de toda la pista para saltar directamente a cualquier punto.
- Jog táctil: girar el plato adelanta/retrocede la posición de reproducción (scrub básico).

## Interfaz
- Se respetan los insets de las barras del sistema para que el Deck B no quede oculto bajo la navegación lateral de Android.
- Mixer con ancho proporcional en vez de ancho fijo.
- Controles inferiores redistribuidos por peso para evitar recortes.
- El control inferior ahora está correctamente rotulado VOL.

## Pendiente / siguiente etapa
El jog de esta versión hace scrub táctil sobre Media3. Un scratch de vinilo real, con audio continuo hacia delante y hacia atrás y latencia profesional, requiere migrar la ruta crítica de audio a Oboe/AAudio/C++.
