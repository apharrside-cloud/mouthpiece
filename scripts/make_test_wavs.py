"""Builds jfk33p.wav (jfk.wav x3 with 1.2 s pauses between) for the streaming test and pushes it to the phone."""
import subprocess
import wave

SRC = 'app/src/androidTest/assets/jfk.wav'
with wave.open(SRC, 'rb') as w:
    params = w.getparams()
    frames = w.readframes(w.getnframes())
silence = b'\x00\x00' * int(params.framerate * 1.2)
out = frames + silence + frames + silence + frames
with wave.open('jfk33p.wav', 'wb') as o:
    o.setparams(params)
    o.writeframes(out)
print('written', len(out) / 2 / params.framerate, 'sec')
