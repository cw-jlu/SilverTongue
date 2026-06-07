import { useState, useEffect, useRef, useCallback } from 'react';
import api from '../api/client';
import WaveSurfer from 'wavesurfer.js';
import Regions from 'wavesurfer.js/dist/plugins/regions.js';
import { useAudioRecorder } from '../lib/useAudioRecorder';
import { LiveAudioVisualizer } from '../lib/AudioVisualizer';
import {
  Panel,
  PanelGroup,
  PanelResizeHandle,
} from 'react-resizable-panels';
import {
  Play, Pause, SkipBack, SkipForward, Repeat, Repeat1,
  Gauge, Mic, Square, Check, X, Download, BookOpen,
  Scissors, ZoomIn, ZoomOut, RefreshCw, Upload,
} from 'lucide-react';

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------
const PLAYBACK_RATES = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0];
const PLAY_MODES = {
  single: { icon: Repeat1, label: '单段循环' },
  loop:   { icon: Repeat,  label: 'A-B 循环' },
  all:    { icon: SkipForward, label: '全部播放' },
};

// ---------------------------------------------------------------------------
// 主组件
// ---------------------------------------------------------------------------
export default function Shadowing() {
  // -- 剪辑列表 --
  const [clips, setClips] = useState([]);
  const [selectedClip, setSelectedClip] = useState(null);
  const [file, setFile] = useState(null);
  const [uploadMsg, setUploadMsg] = useState('');

  // -- 播放状态 --
  const [playbackRate, setPlaybackRate] = useState(1.0);
  const [playMode, setPlayMode] = useState('single');
  const [currentSegment, setCurrentSegment] = useState(0);
  const [segments, setSegments] = useState([]);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  // -- 波形 --
  const wavesurferRef = useRef(null);
  const wsRegionsRef = useRef(null);
  const waveformContainerRef = useRef(null);
  const [wsReady, setWsReady] = useState(false);

  // -- 录音 --
  const recordingWsRef = useRef(null);
  const recordingContainerRef = useRef(null);
  const [recordingBlob, setRecordingBlob] = useState(null);
  const [recordingUrl, setRecordingUrl] = useState(null);
  const [assessmentResult, setAssessmentResult] = useState(null);

  // -- 词典弹窗 --
  const [dictOpen, setDictOpen] = useState(false);
  const [dictWord, setDictWord] = useState('');
  const [dictResult, setDictResult] = useState(null);
  const [dictLoading, setDictLoading] = useState(false);

  // -- 变速弹窗 --
  const [speedOpen, setSpeedOpen] = useState(false);

  // -- 录音器 --
  const {
    startRecording,
    stopRecording,
    togglePauseResume,
    recordingBlob: liveBlob,
    isRecording,
    isPaused,
    recordingTime,
    mediaRecorder,
  } = useAudioRecorder();

  // -------------------------------------------------------------------
  // 加载剪辑列表
  // -------------------------------------------------------------------
  const loadClips = () =>
    api.get('/clips?page=1&size=50').then((r) => setClips(r.data || []));

  useEffect(() => { loadClips(); }, []);

  // -------------------------------------------------------------------
  // 上传素材
  // -------------------------------------------------------------------
  const upload = async () => {
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    try {
      const r = await api.post('/material/upload', fd);
      setUploadMsg(`上传成功: ${r.data?.title || file.name}`);
      setFile(null);
      loadClips();
    } catch (err) {
      setUploadMsg(err?.message || '上传失败');
    }
  };

  // -------------------------------------------------------------------
  // 选择剪辑 → 初始化 WaveSurfer
  // -------------------------------------------------------------------
  const selectClip = useCallback((clip) => {
    setSelectedClip(clip);
    setCurrentSegment(0);
    setAssessmentResult(null);
    setRecordingBlob(null);
    setRecordingUrl(null);
  }, []);

  // -------------------------------------------------------------------
  // WaveSurfer 初始化
  // -------------------------------------------------------------------
  useEffect(() => {
    if (!selectedClip || !waveformContainerRef.current) return;

    // 销毁旧实例
    if (wavesurferRef.current) {
      wavesurferRef.current.destroy();
      wavesurferRef.current = null;
      wsRegionsRef.current = null;
    }

    // 构建音频 URL：优先使用 clip 的 audioPath，否则用 MinIO URL
    const audioUrl =
      selectedClip.audioPath ||
      selectedClip.sourceUrl ||
      `/api/clips/${selectedClip.id}/audio`;

    const ws = WaveSurfer.create({
      container: waveformContainerRef.current,
      url: audioUrl,
      height: 120,
      barWidth: 2,
      cursorWidth: 1,
      autoCenter: true,
      autoScroll: true,
      minPxPerSec: 100,
      waveColor: '#e5e7eb',
      progressColor: 'rgba(99, 102, 241, 0.5)',
      normalize: true,
    });

    const regions = ws.registerPlugin(Regions.create());

    ws.on('ready', () => {
      setDuration(ws.getDuration());
      setWsReady(true);

      // 如果有字幕/transcription，解析为 segments
      const trans = selectedClip.transcription;
      if (trans && trans.timeline) {
        setSegments(trans.timeline);
      } else if (selectedClip.content) {
        // 简单地将文本按句分割
        const text = selectedClip.content;
        const dur = ws.getDuration();
        const sentences = text.split(/(?<=[.!?])\s+/).filter(Boolean);
        const segDur = dur / sentences.length;
        const timeline = sentences.map((txt, i) => ({
          text: txt,
          startTime: i * segDur,
          endTime: (i + 1) * segDur,
        }));
        setSegments(timeline);
      }
    });

    ws.on('timeupdate', (t) => setCurrentTime(t));
    ws.on('play', () => setIsPlaying(true));
    ws.on('pause', () => setIsPlaying(false));
    ws.on('finish', () => {
      setIsPlaying(false);
      if (playMode === 'loop') {
        ws.setTime(0);
        ws.play();
      }
    });

    // Region 事件：播放到 region 结束时的行为
    regions.on('region-out', (region) => {
      if (region.id.startsWith('segment-') && playMode === 'single') {
        ws.pause();
      }
    });

    wavesurferRef.current = ws;
    wsRegionsRef.current = regions;

    return () => {
      ws.destroy();
      wavesurferRef.current = null;
      wsRegionsRef.current = null;
    };
  }, [selectedClip]);

  // -------------------------------------------------------------------
  // 更新播放速率
  // -------------------------------------------------------------------
  useEffect(() => {
    wavesurferRef.current?.setPlaybackRate(playbackRate);
  }, [playbackRate]);

  // -------------------------------------------------------------------
  // 播放模式 — 更新 region
  // -------------------------------------------------------------------
  useEffect(() => {
    const ws = wavesurferRef.current;
    const regions = wsRegionsRef.current;
    if (!ws || !regions || !segments.length) return;

    regions.clearRegions();

    if (playMode === 'single' || playMode === 'loop') {
      const seg = segments[currentSegment];
      if (!seg) return;

      const region = regions.addRegion({
        id: `segment-${currentSegment}`,
        start: seg.startTime,
        end: seg.endTime,
        color: 'rgba(99, 102, 241, 0.1)',
        drag: false,
        resize: false,
      });
      ws.setScrollTime(seg.startTime);
      ws.setTime(seg.startTime);
    }
  }, [playMode, currentSegment, segments]);

  // -------------------------------------------------------------------
  // 播放 / 暂停
  // -------------------------------------------------------------------
  const playPause = () => {
    const ws = wavesurferRef.current;
    if (!ws) return;
    ws.playPause();
  };

  const prevSegment = () => {
    if (currentSegment > 0) setCurrentSegment(currentSegment - 1);
  };

  const nextSegment = () => {
    if (currentSegment < segments.length - 1) setCurrentSegment(currentSegment + 1);
  };

  // -------------------------------------------------------------------
  // 录音处理
  // -------------------------------------------------------------------
  useEffect(() => {
    if (liveBlob) {
      setRecordingBlob(liveBlob);
      const url = URL.createObjectURL(liveBlob);
      setRecordingUrl(url);

      // 初始化录音波形
      if (recordingContainerRef.current) {
        if (recordingWsRef.current) recordingWsRef.current.destroy();

        const rws = WaveSurfer.create({
          container: recordingContainerRef.current,
          url,
          height: 100,
          barWidth: 2,
          cursorWidth: 1,
          autoCenter: true,
          autoScroll: true,
          minPxPerSec: 150,
          waveColor: '#fda4af',
          progressColor: 'rgba(251, 113, 133, 0.5)',
          normalize: false,
        });
        recordingWsRef.current = rws;
      }
    }
  }, [liveBlob]);

  // -------------------------------------------------------------------
  // 提交录音评测
  // -------------------------------------------------------------------
  const submitRecording = async () => {
    if (!recordingBlob || !selectedClip) return;

    const fd = new FormData();
    fd.append('audio', recordingBlob, 'recording.wav');
    fd.append('targetText', selectedClip.content || '');
    fd.append('clipId', selectedClip.id);

    try {
      const r = await api.post('/shadowing/record', fd);
      // Backend 返回 ApiResult<{ clipId, audioUrl, targetText, assessment }>
      const payload = r.data || r;
      setAssessmentResult(payload.assessment || payload);
    } catch (err) {
      console.error('评估失败:', err);
    }
  };

  // -------------------------------------------------------------------
  // 词典查词
  // -------------------------------------------------------------------
  const lookupWord = async (word) => {
    setDictOpen(true);
    setDictWord(word);
    setDictLoading(true);
    setDictResult(null);
    try {
      const r = await api.get(`/dict/lookup?word=${encodeURIComponent(word)}`);
      setDictResult(r);
    } catch (err) {
      setDictResult({ found: false, word });
    } finally {
      setDictLoading(false);
    }
  };

  // 字幕选中文字事件
  const handleTextSelection = () => {
    const sel = window.getSelection();
    const text = sel?.toString().trim();
    if (text && /^[a-zA-Z]+$/.test(text)) {
      lookupWord(text);
    }
  };

  // -------------------------------------------------------------------
  // 格式化时间
  // -------------------------------------------------------------------
  const fmt = (s) => {
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return `${m}:${String(sec).padStart(2, '0')}`;
  };

  // -------------------------------------------------------------------
  // 渲染
  // -------------------------------------------------------------------
  const PlayIcon = isPlaying ? Pause : Play;
  const PlayModeIcon = PLAY_MODES[playMode]?.icon || Repeat1;

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <h2>🎬 影子跟读</h2>

      {/* ================================================================ */}
      {/* 上传区域 */}
      {/* ================================================================ */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input
            type="file"
            accept="audio/*,video/*"
            onChange={(e) => setFile(e.target.files[0])}
          />
          <button className="btn btn-sm" onClick={upload}>
            <Upload size={14} style={{ marginRight: 4 }} /> 上传
          </button>
          <button className="btn btn-sm btn-ghost" onClick={loadClips}>
            <RefreshCw size={14} style={{ marginRight: 4 }} /> 刷新列表
          </button>
          {uploadMsg && (
            <span style={{ fontSize: 13, color: '#6b7280' }}>{uploadMsg}</span>
          )}
        </div>
      </div>

      {/* ================================================================ */}
      {/* 主布局：剪辑列表 + 播放器 */}
      {/* ================================================================ */}
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {/* ---- 左侧剪辑列表 ---- */}
        <div className="card" style={{ width: 280, flexShrink: 0, maxHeight: 600, overflow: 'auto' }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: 15 }}>📋 语料切片</h3>
          {clips.length === 0 && (
            <p style={{ color: '#9ca3af', fontSize: 13 }}>暂无切片，请上传素材</p>
          )}
          {clips.map((c) => (
            <div
              key={c.id}
              onClick={() => selectClip(c)}
              style={{
                padding: '8px 10px',
                borderRadius: 6,
                cursor: 'pointer',
                background: selectedClip?.id === c.id ? '#eef2ff' : 'transparent',
                borderBottom: '1px solid #f3f4f6',
                transition: 'background 0.15s',
              }}
            >
              <p style={{ margin: 0, fontSize: 13, fontWeight: 500 }}>
                {c.content ? c.content.slice(0, 60) + (c.content.length > 60 ? '...' : '') : '(无字幕)'}
              </p>
              <small style={{ color: '#9ca3af' }}>
                ⏱ {c.startTime}s – {c.endTime}s
              </small>
            </div>
          ))}
        </div>

        {/* ---- 右侧播放区域 ---- */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {!selectedClip ? (
            <div
              className="card"
              style={{
                height: 300,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#9ca3af',
              }}
            >
              请从左侧选择一个语料切片开始跟读
            </div>
          ) : (
            <PanelGroup direction="vertical" style={{ height: 560 }}>
              {/* ==================================================== */}
              {/* 上栏：原声波形 + 播放控制 */}
              {/* ==================================================== */}
              <Panel defaultSize={55} minSize={30}>
                <div className="card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                  {/* 波形容器 */}
                  <div
                    ref={waveformContainerRef}
                    style={{ flex: 1, minHeight: 100, borderRadius: 8, overflow: 'hidden' }}
                  />

                  {/* 时间显示 */}
                  <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: 12, color: '#6b7280', marginTop: 4 }}>
                    {fmt(currentTime)} / {fmt(duration)}
                  </div>

                  {/* ============ 播放控制栏 ============ */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 8,
                      padding: '8px 0',
                      borderTop: '1px solid #f3f4f6',
                      marginTop: 8,
                    }}
                  >
                    {/* 变速 */}
                    <div style={{ position: 'relative' }}>
                      <button
                        className="btn btn-sm btn-ghost"
                        onClick={() => setSpeedOpen(!speedOpen)}
                        title="播放速度"
                      >
                        <Gauge size={16} />
                        {playbackRate !== 1.0 && (
                          <span style={{ fontSize: 10, marginLeft: 2 }}>{playbackRate}x</span>
                        )}
                      </button>
                      {speedOpen && (
                        <div
                          className="card"
                          style={{
                            position: 'absolute',
                            bottom: '100%',
                            left: 0,
                            marginBottom: 4,
                            padding: '4px 8px',
                            zIndex: 10,
                            display: 'flex',
                            gap: 4,
                          }}
                        >
                          {PLAYBACK_RATES.map((r) => (
                            <button
                              key={r}
                              className={`btn btn-xs ${r === playbackRate ? 'btn-primary' : 'btn-ghost'}`}
                              onClick={() => { setPlaybackRate(r); setSpeedOpen(false); }}
                            >
                              {r}x
                            </button>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* 播放模式 */}
                    <button
                      className="btn btn-sm btn-ghost"
                      onClick={() => {
                        const modes = Object.keys(PLAY_MODES);
                        const idx = modes.indexOf(playMode);
                        setPlayMode(modes[(idx + 1) % modes.length]);
                      }}
                      title={PLAY_MODES[playMode]?.label}
                    >
                      <PlayModeIcon size={16} />
                    </button>

                    {/* 上一段 */}
                    <button className="btn btn-sm btn-ghost" onClick={prevSegment} disabled={currentSegment === 0}>
                      <SkipBack size={18} />
                    </button>

                    {/* 播放 / 暂停 */}
                    <button
                      className="btn btn-primary"
                      onClick={playPause}
                      style={{ borderRadius: '50%', width: 42, height: 42, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                      <PlayIcon size={20} fill="white" />
                    </button>

                    {/* 下一段 */}
                    <button className="btn btn-sm btn-ghost" onClick={nextSegment} disabled={currentSegment >= segments.length - 1}>
                      <SkipForward size={18} />
                    </button>

                    {/* 段落指示 */}
                    {segments.length > 0 && (
                      <span style={{ fontSize: 12, color: '#6b7280', marginLeft: 8 }}>
                        第 {currentSegment + 1}/{segments.length} 段
                      </span>
                    )}
                  </div>

                  {/* ============ 字幕显示 (可选) ============ */}
                  {segments.length > 0 && (
                    <div
                      className="caption-area"
                      onMouseUp={handleTextSelection}
                      style={{
                        padding: '8px 12px',
                        fontSize: 14,
                        lineHeight: 1.6,
                        background: '#f9fafb',
                        borderRadius: 6,
                        marginTop: 4,
                        cursor: 'text',
                        userSelect: 'text',
                      }}
                    >
                      {segments.map((seg, i) => (
                        <span
                          key={i}
                          style={{
                            padding: '2px 2px',
                            borderRadius: 3,
                            background: i === currentSegment ? '#dbeafe' : 'transparent',
                            fontWeight: i === currentSegment ? 600 : 400,
                            cursor: 'pointer',
                          }}
                          onClick={() => setCurrentSegment(i)}
                        >
                          {seg.text}{' '}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </Panel>

              <PanelResizeHandle style={{ height: 6, background: '#e5e7eb', borderRadius: 3, margin: '4px 0' }} />

              {/* ==================================================== */}
              {/* 下栏：用户录音 */}
              {/* ==================================================== */}
              <Panel defaultSize={45} minSize={20}>
                <div className="card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                  {/* 录音中 */}
                  {isRecording ? (
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
                      <LiveAudioVisualizer
                        mediaRecorder={mediaRecorder}
                        barWidth={2}
                        gap={2}
                        width={300}
                        height={60}
                        fftSize={512}
                        maxDecibels={-10}
                        minDecibels={-80}
                        smoothingTimeConstant={0.4}
                      />
                      <span style={{ fontSize: 14, color: '#6b7280' }}>
                        {Math.floor(recordingTime / 60)}:{String(recordingTime % 60).padStart(2, '0')}
                      </span>
                      <div style={{ display: 'flex', gap: 8 }}>
                        <button
                          className="btn btn-danger"
                          style={{ borderRadius: '50%', width: 36, height: 36 }}
                          onClick={() => {
                            stopRecording();
                            // 取消（不保存）
                          }}
                          title="取消"
                        >
                          <X size={16} />
                        </button>
                        <button
                          className="btn btn-ghost"
                          style={{ borderRadius: '50%', width: 36, height: 36 }}
                          onClick={togglePauseResume}
                        >
                          {isPaused ? <Play size={16} /> : <Pause size={16} />}
                        </button>
                        <button
                          className="btn btn-success"
                          style={{ borderRadius: '50%', width: 36, height: 36 }}
                          onClick={stopRecording}
                          title="完成"
                        >
                          <Check size={16} />
                        </button>
                      </div>
                    </div>
                  ) : recordingUrl ? (
                    /* 已有录音 */
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                      <div ref={recordingContainerRef} style={{ flex: 1, minHeight: 80 }} />
                      <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 8 }}>
                        <button className="btn btn-sm btn-primary" onClick={submitRecording}>
                          📊 提交评测
                        </button>
                        <button
                          className="btn btn-sm btn-ghost"
                          onClick={() => {
                            setRecordingBlob(null);
                            setRecordingUrl(null);
                            setAssessmentResult(null);
                            if (recordingWsRef.current) {
                              recordingWsRef.current.destroy();
                              recordingWsRef.current = null;
                            }
                          }}
                        >
                          重新录制
                        </button>
                      </div>
                      {/* 评测结果 */}
                      {assessmentResult && (
                        <div
                          style={{
                            padding: 12,
                            background: '#f0fdf4',
                            borderRadius: 8,
                            marginTop: 8,
                          }}
                        >
                          <p style={{ margin: 0, fontWeight: 600, color: '#166534' }}>
                            📈 得分: {assessmentResult.finalScore?.toFixed(1) || '—'} / 100
                          </p>
                          <div style={{ display: 'flex', gap: 16, fontSize: 13, color: '#4b5563', marginTop: 4 }}>
                            <span>准确度: {assessmentResult.accuracy?.toFixed(1) || '—'}</span>
                            <span>流利度: {assessmentResult.fluency?.toFixed(1) || '—'}</span>
                            <span>完整度: {assessmentResult.completeness?.toFixed(1) || '—'}</span>
                          </div>
                          {assessmentResult.words && (
                            <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                              {assessmentResult.words.map((w, i) => (
                                <span
                                  key={i}
                                  style={{
                                    padding: '2px 6px',
                                    borderRadius: 4,
                                    fontSize: 12,
                                    background: w.score >= 80 ? '#bbf7d0' : w.score >= 60 ? '#fef08a' : '#fecaca',
                                    color: w.score >= 80 ? '#166534' : w.score >= 60 ? '#854d0e' : '#991b1b',
                                  }}
                                  title={`${w.word}: ${w.score}`}
                                >
                                  {w.word}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  ) : (
                    /* 无录音 */
                    <div
                      style={{
                        flex: 1,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <button
                        className="btn btn-danger"
                        style={{ borderRadius: '50%', width: 56, height: 56, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                        onClick={startRecording}
                        title="开始跟读录音"
                      >
                        <Mic size={24} fill="white" />
                      </button>
                    </div>
                  )}
                </div>
              </Panel>
            </PanelGroup>
          )}
        </div>
      </div>

      {/* ================================================================ */}
      {/* 词典弹窗 */}
      {/* ================================================================ */}
      {dictOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.3)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 100,
          }}
          onClick={() => setDictOpen(false)}
        >
          <div
            className="card"
            style={{ maxWidth: 420, width: '90%', maxHeight: '70vh', overflow: 'auto' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0 }}>
                <BookOpen size={18} style={{ marginRight: 6 }} />
                {dictWord}
              </h3>
              <button className="btn btn-sm btn-ghost" onClick={() => setDictOpen(false)}>
                <X size={16} />
              </button>
            </div>

            {dictLoading ? (
              <p style={{ color: '#9ca3af' }}>查询中...</p>
            ) : dictResult?.found ? (
              dictResult.entries.map((entry, i) => (
                <div key={i} style={{ marginBottom: 12, paddingBottom: 12, borderBottom: '1px solid #f3f4f6' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <span style={{ fontWeight: 600, color: '#6366f1', fontSize: 13 }}>
                      {entry.pos}
                    </span>
                    {entry.phoneticsUk && (
                      <span style={{ fontSize: 12, color: '#6b7280' }}>UK {entry.phoneticsUk}</span>
                    )}
                    {entry.phoneticsUs && (
                      <span style={{ fontSize: 12, color: '#6b7280' }}>US {entry.phoneticsUs}</span>
                    )}
                  </div>
                  <p style={{ margin: '4px 0', fontSize: 14 }}>{entry.definition}</p>
                  {entry.translation && (
                    <p style={{ margin: '2px 0', fontSize: 13, color: '#059669' }}>
                      {entry.translation}
                    </p>
                  )}
                  {entry.examples?.length > 0 && (
                    <ul style={{ margin: '4px 0 0 0', paddingLeft: 18, fontSize: 12, color: '#6b7280' }}>
                      {entry.examples.map((ex, j) => (
                        <li key={j}>{ex}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ))
            ) : (
              <p style={{ color: '#9ca3af' }}>未找到 "{dictWord}" 的释义</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
