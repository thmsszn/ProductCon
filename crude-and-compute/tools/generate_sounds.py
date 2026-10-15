"""Synthesises the loopable server-rack noise (needs numpy and ffmpeg) and writes assets/crudecompute/sounds/rack_hum.ogg.

Real racks are loud for three reasons: turbulent broadband air noise from dozens of small fans, tonal blade-pass whine
(fundamental = blades * rpm / 60 plus harmonics) and a low rumble from PSUs and drives. All three are built in the frequency
domain with exact integer-period tones so the loop is seamless.
"""

import os
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "crudecompute", "sounds")
RATE = 44100
SECONDS = 8
N = RATE * SECONDS


def band_noise(rng, lo, hi, slope=0.0):
    """Circular band-limited noise (random phase spectrum) with an optional spectral tilt."""
    spec = np.zeros(N // 2 + 1, dtype=np.complex128)
    freqs = np.fft.rfftfreq(N, 1 / RATE)
    mask = (freqs >= lo) & (freqs <= hi)
    mag = np.zeros_like(freqs)
    mag[mask] = (freqs[mask] / 1000.0 + 0.05) ** slope
    # soft band edges
    edge = np.clip((freqs - lo) / max(1.0, lo * 0.3), 0, 1) * np.clip((hi - freqs) / max(1.0, hi * 0.2), 0, 1)
    spec = mag * edge * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs)))
    x = np.fft.irfft(spec, N)
    return x / (np.max(np.abs(x)) + 1e-9)


def tone(f, phase=0.0):
    t = np.arange(N) / RATE
    return np.sin(2 * np.pi * f * t + phase)


def main():
    rng = np.random.default_rng(7)
    t = np.arange(N) / RATE
    # slow wobble with a period that divides the loop length -> seamless
    wobble = 1.0 + 0.06 * np.sin(2 * np.pi * 0.5 * t) + 0.04 * np.sin(2 * np.pi * 1.25 * t + 1.0)
    air = band_noise(rng, 400, 7000, slope=-0.25) * 0.55
    hiss = band_noise(rng, 2500, 12000, slope=0.0) * 0.12
    rumble = band_noise(rng, 40, 220, slope=-1.0) * 0.5
    whine = np.zeros(N)
    for k, amp in ((1, 1.0), (2, 0.6), (3, 0.42), (4, 0.28), (5, 0.18)):
        whine += amp * tone(840.0 * k, rng.uniform(0, 6.28)) * (1.0 + 0.1 * np.sin(2 * np.pi * 0.25 * t + k))
    whine2 = np.zeros(N)
    for k, amp in ((1, 1.0), (2, 0.5), (3, 0.3)):
        whine2 += amp * tone(1240.0 * k, rng.uniform(0, 6.28))
    psu = tone(120.0) * 0.25 + tone(240.0) * 0.12
    mix = (air + hiss) * wobble + 0.11 * whine + 0.07 * whine2 + rumble * 0.6 + psu * 0.35
    mix = mix / np.max(np.abs(mix)) * 0.8
    pcm = (mix * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    wav_path = os.path.join(OUT, "_rack_hum.wav")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    ogg = os.path.join(OUT, "rack_hum.ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", ogg], check=True)
    os.remove(wav_path)
    print("wrote", ogg, os.path.getsize(ogg), "bytes")


if __name__ == "__main__":
    sys.exit(main())
