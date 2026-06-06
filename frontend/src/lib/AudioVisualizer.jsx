/**
 * Local replacement for react-audio-visualize's LiveAudioVisualizer.
 * The npm package bundles React 18's JSX runtime which crashes on React 19.
 * This is a drop-in replacement using the Web Audio API + Canvas directly.
 */
import { useRef, useEffect, useCallback, useState } from 'react';

export function LiveAudioVisualizer({
  mediaRecorder,
  width = '100%',
  height = '100%',
  barWidth = 2,
  gap = 1,
  backgroundColor = 'transparent',
  barColor = 'rgb(160, 198, 255)',
  fftSize = 1024,
  maxDecibels = -10,
  minDecibels = -90,
  smoothingTimeConstant = 0.4,
}) {
  const canvasRef = useRef(null);
  const [audioCtx] = useState(() => new (window.AudioContext || window.webkitAudioContext)());
  const analyserRef = useRef(null);
  const animFrameRef = useRef(null);

  useEffect(() => {
    if (!mediaRecorder?.stream) return;

    const analyser = audioCtx.createAnalyser();
    analyser.fftSize = fftSize;
    analyser.minDecibels = minDecibels;
    analyser.maxDecibels = maxDecibels;
    analyser.smoothingTimeConstant = smoothingTimeConstant;

    const source = audioCtx.createMediaStreamSource(mediaRecorder.stream);
    source.connect(analyser);
    analyserRef.current = analyser;

    return () => {
      source.disconnect();
      analyser.disconnect();
    };
  }, [mediaRecorder?.stream]);

  const draw = useCallback(() => {
    const analyser = analyserRef.current;
    const canvas = canvasRef.current;
    if (!analyser || !canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const data = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(data);

    const barsCount = Math.floor(canvas.width / (barWidth + gap));
    const step = Math.floor(data.length / barsCount) || 1;
    const half = canvas.height / 2;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (backgroundColor !== 'transparent') {
      ctx.fillStyle = backgroundColor;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
    }

    ctx.fillStyle = barColor;
    for (let i = 0; i < barsCount; i++) {
      let sum = 0;
      for (let j = 0; j < step && i * step + j < data.length; j++) {
        sum += data[i * step + j];
      }
      const avg = sum / step;
      const barHeight = avg || 1;
      const x = i * (barWidth + gap);
      const y = half - barHeight / 2;
      ctx.beginPath();
      if (ctx.roundRect) {
        ctx.roundRect(x, y, barWidth, barHeight, 50);
        ctx.fill();
      } else {
        ctx.fillRect(x, y, barWidth, barHeight);
      }
    }

    if (mediaRecorder.state === 'recording') {
      animFrameRef.current = requestAnimationFrame(draw);
    }
  }, [barWidth, gap, backgroundColor, barColor]);

  useEffect(() => {
    if (analyserRef.current && mediaRecorder?.state === 'recording') {
      draw();
    }
    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
    };
  }, [analyserRef.current, mediaRecorder?.state, draw]);

  return <canvas ref={canvasRef} width={width} height={height} style={{ aspectRatio: 'unset' }} />;
}

export function AudioVisualizer(props) {
  // Minimal stub – not currently used in the project
  return <canvas width={props.width} height={props.height} />;
}
