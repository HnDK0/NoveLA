import React, { useEffect, useState } from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Square,
  Volume2,
  ChevronDown,
  ChevronUp,
  X,
  Gauge,
} from 'lucide-react';
import { ttsService, TtsState } from '../services/tts';

interface TtsMiniPlayerProps {
  onClose: () => void;
}

export const TtsMiniPlayer: React.FC<TtsMiniPlayerProps> = ({ onClose }) => {
  const [state, setState] = useState<TtsState>(ttsService.getState());
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([]);
  const [isMinimized, setIsMinimized] = useState(false);
  const [showSpeedControls, setShowSpeedControls] = useState(false);

  useEffect(() => {
    const unsub = ttsService.subscribe(setState);

    // Load voices
    const updateVoices = () => {
      setVoices(ttsService.getVoices());
    };
    updateVoices();
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.onvoiceschanged = updateVoices;
    }

    return () => unsub();
  }, []);

  if (!state.isPlaying && !state.isPaused) {
    return null;
  }

  return (
    <div className="fixed bottom-20 md:bottom-6 right-4 z-50 animate-in slide-in-from-bottom duration-200">
      <div className="bg-zinc-900/95 backdrop-blur-md border border-amber-500/30 rounded-2xl p-3 shadow-2xl shadow-black/60 max-w-xs sm:max-w-sm text-zinc-100">
        {/* Header */}
        <div className="flex items-center justify-between gap-2 border-b border-zinc-800/80 pb-2 mb-2">
          <div className="flex items-center space-x-2">
            <div className="w-2.5 h-2.5 rounded-full bg-amber-400 animate-pulse" />
            <span className="text-xs font-bold text-amber-400 uppercase tracking-wider flex items-center gap-1">
              <Volume2 className="w-3.5 h-3.5" />
              TTS Narrator
            </span>
          </div>

          <div className="flex items-center space-x-1">
            <button
              onClick={() => setShowSpeedControls(!showSpeedControls)}
              title="Speed & Voice"
              className={`p-1 rounded-lg transition ${
                showSpeedControls ? 'text-amber-400 bg-amber-500/10' : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <Gauge className="w-4 h-4" />
            </button>
            <button
              onClick={() => setIsMinimized(!isMinimized)}
              className="p-1 text-zinc-400 hover:text-zinc-200 rounded-lg transition"
            >
              {isMinimized ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            </button>
            <button
              onClick={() => {
                ttsService.stop();
                onClose();
              }}
              className="p-1 text-zinc-400 hover:text-rose-400 rounded-lg transition"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {!isMinimized && (
          <>
            {/* Speed & Voice panel */}
            {showSpeedControls && (
              <div className="mb-3 p-2.5 bg-zinc-950 border border-zinc-800 rounded-xl space-y-2 text-xs">
                <div>
                  <div className="flex justify-between text-zinc-400 mb-1">
                    <span>Speed Rate</span>
                    <span className="text-amber-400 font-bold">{state.rate.toFixed(1)}x</span>
                  </div>
                  <input
                    type="range"
                    min="0.5"
                    max="2.5"
                    step="0.1"
                    value={state.rate}
                    onChange={(e) => ttsService.setRate(parseFloat(e.target.value))}
                    className="w-full accent-amber-500 h-1.5 bg-zinc-800 rounded-lg cursor-pointer"
                  />
                </div>

                {voices.length > 0 && (
                  <div>
                    <label className="text-zinc-400 block mb-1">Voice</label>
                    <select
                      value={state.voiceURI}
                      onChange={(e) => ttsService.setVoice(e.target.value)}
                      className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-2 py-1 text-xs text-zinc-200"
                    >
                      <option value="">Default System Voice</option>
                      {voices.slice(0, 10).map((v) => (
                        <option key={v.voiceURI} value={v.voiceURI}>
                          {v.name} ({v.lang})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>
            )}

            {/* Status indicator */}
            <div className="text-[11px] text-zinc-400 mb-2 truncate">
              Speaking paragraph #{state.currentParagraphIndex + 1}
            </div>

            {/* Primary Controls */}
            <div className="flex items-center justify-center space-x-3">
              <button
                onClick={() => ttsService.prev()}
                title="Previous paragraph"
                className="p-2 text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800 rounded-xl transition"
              >
                <SkipBack className="w-4 h-4" />
              </button>

              <button
                onClick={() => ttsService.togglePlayPause()}
                title={state.isPaused ? 'Resume' : 'Pause'}
                className="p-3 bg-amber-500 hover:bg-amber-400 text-zinc-950 rounded-xl font-bold transition shadow-md shadow-amber-500/30"
              >
                {state.isPaused ? <Play className="w-5 h-5 fill-current" /> : <Pause className="w-5 h-5 fill-current" />}
              </button>

              <button
                onClick={() => ttsService.next()}
                title="Next paragraph"
                className="p-2 text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800 rounded-xl transition"
              >
                <SkipForward className="w-4 h-4" />
              </button>

              <button
                onClick={() => {
                  ttsService.stop();
                  onClose();
                }}
                title="Stop Narration"
                className="p-2 text-zinc-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-xl transition"
              >
                <Square className="w-4 h-4 fill-current" />
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
