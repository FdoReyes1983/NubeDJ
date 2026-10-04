# Nube DJ v0.3.0

## Performance UI
- New full-screen DJ layout inspired by physical two-deck workflows.
- Much larger touch waveform for each deck.
- Larger jog wheels and mirrored deck layout.
- Real vertical pitch fader with center detent and selectable ±8 / ±16 / ±50 ranges.
- Primary controls kept on-screen: PLAY, CUE, SYNC, LOOP, SET, TAP.
- System bars become transient so Android navigation no longer covers deck controls.

## DJ interaction
- CUE keeps momentary/stutter behavior.
- Jog center = scratch-style scrub; jog rim = temporary pitch bend/nudge.
- Waveform: touch/drag seek, overview absolute seek, pinch zoom, double-tap reset zoom.
- Loop sizes 1/2/4/8/16/32 beats with +/- controls.
- Mixer popup contains channel volume and LOW/MID/HIGH EQ.

## BPM / loading
- BPM is analyzed independently from the full waveform, so playback does not wait for analysis.
- New BPM estimator combines onset autocorrelation with median beat-interval detection to reduce half/double-time errors.
- BPM test pulse trains cover slow and fast tempos.
- BPM/waveform analysis is cached locally for faster reloads.
- Google Drive starts playback as an authenticated stream while local cache/download and analysis continue in the background.

## Notes
- Jog scratch is still Media3-based seek/scrub. True continuous reverse/forward vinyl audio will require the future native low-latency Oboe/AAudio engine.
