/**
 * Local replacement for react-audio-voice-recorder's useAudioRecorder hook.
 * The npm package bundles React 18's JSX runtime which crashes on React 19.
 */
import { useState, useRef, useCallback, useEffect } from 'react';

export function useAudioRecorder() {
  const [isRecording, setIsRecording] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [recordingBlob, setRecordingBlob] = useState(null);
  const [mediaRecorder, setMediaRecorder] = useState(null);

  const chunksRef = useRef([]);
  const timerRef = useRef(null);

  const startTimer = useCallback(() => {
    timerRef.current = setInterval(() => {
      setRecordingTime((prev) => prev + 1);
    }, 1000);
  }, []);

  const stopTimer = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);

      chunksRef.current = [];
      setRecordingBlob(null);
      setRecordingTime(0);
      setIsPaused(false);

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
        setRecordingBlob(blob);
        // Stop all tracks to release the microphone
        stream.getTracks().forEach((track) => track.stop());
      };

      recorder.start(100); // collect data every 100ms
      setMediaRecorder(recorder);
      setIsRecording(true);
      startTimer();
    } catch (err) {
      console.error('Failed to start recording:', err);
    }
  }, [startTimer]);

  const stopRecording = useCallback(() => {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      setIsRecording(false);
      setIsPaused(false);
      stopTimer();
    }
  }, [mediaRecorder, stopTimer]);

  const togglePauseResume = useCallback(() => {
    if (!mediaRecorder) return;
    if (mediaRecorder.state === 'recording') {
      mediaRecorder.pause();
      setIsPaused(true);
      stopTimer();
    } else if (mediaRecorder.state === 'paused') {
      mediaRecorder.resume();
      setIsPaused(false);
      startTimer();
    }
  }, [mediaRecorder, startTimer, stopTimer]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopTimer();
      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
      }
    };
  }, []);

  return {
    startRecording,
    stopRecording,
    togglePauseResume,
    recordingBlob,
    isRecording,
    isPaused,
    recordingTime,
    mediaRecorder,
  };
}
