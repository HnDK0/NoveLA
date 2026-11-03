export interface TtsState {
  isPlaying: boolean;
  isPaused: boolean;
  currentParagraphIndex: number;
  rate: number;
  pitch: number;
  voiceURI: string;
}

type TtsListener = (state: TtsState) => void;

class TtsManager {
  private synth: SpeechSynthesis | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private paragraphs: string[] = [];
  private currentIndex: number = 0;
  private isPlaying: boolean = false;
  private isPaused: boolean = false;
  private rate: number = 1.0;
  private pitch: number = 1.0;
  private voiceURI: string = '';
  private listeners: Set<TtsListener> = new Set();
  private onChapterFinishCallback: (() => void) | null = null;

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
    }
  }

  public subscribe(listener: TtsListener): () => void {
    this.listeners.add(listener);
    this.notify();
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    const state: TtsState = {
      isPlaying: this.isPlaying,
      isPaused: this.isPaused,
      currentParagraphIndex: this.currentIndex,
      rate: this.rate,
      pitch: this.pitch,
      voiceURI: this.voiceURI,
    };
    this.listeners.forEach((fn) => fn(state));
  }

  public getVoices(): SpeechSynthesisVoice[] {
    if (!this.synth) return [];
    return this.synth.getVoices();
  }

  public setVoice(voiceURI: string): void {
    this.voiceURI = voiceURI;
    this.notify();
  }

  public setRate(rate: number): void {
    this.rate = Math.max(0.5, Math.min(2.5, rate));
    if (this.isPlaying && !this.isPaused) {
      this.speakCurrent();
    } else {
      this.notify();
    }
  }

  public setPitch(pitch: number): void {
    this.pitch = Math.max(0.5, Math.min(2.0, pitch));
    this.notify();
  }

  public start(paragraphs: string[], startIndex: number = 0, onFinish?: () => void): void {
    if (!this.synth) return;
    this.stop();
    this.paragraphs = paragraphs.filter((p) => p.trim().length > 0);
    this.currentIndex = Math.max(0, Math.min(startIndex, this.paragraphs.length - 1));
    this.onChapterFinishCallback = onFinish || null;
    this.isPlaying = true;
    this.isPaused = false;
    this.speakCurrent();
  }

  private speakCurrent(): void {
    if (!this.synth || !this.isPlaying || this.currentIndex >= this.paragraphs.length) {
      if (this.currentIndex >= this.paragraphs.length && this.onChapterFinishCallback) {
        this.onChapterFinishCallback();
      }
      this.stop();
      return;
    }

    this.synth.cancel();

    const text = this.paragraphs[this.currentIndex];
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = this.rate;
    utterance.pitch = this.pitch;

    if (this.voiceURI) {
      const voice = this.getVoices().find((v) => v.voiceURI === this.voiceURI);
      if (voice) utterance.voice = voice;
    }

    utterance.onend = () => {
      if (this.isPlaying && !this.isPaused) {
        this.currentIndex++;
        this.speakCurrent();
      }
    };

    utterance.onerror = (e) => {
      console.warn('TTS utterance error:', e);
      if (this.isPlaying && !this.isPaused) {
        this.currentIndex++;
        this.speakCurrent();
      }
    };

    this.currentUtterance = utterance;
    this.synth.speak(utterance);
    this.notify();
  }

  public pause(): void {
    if (!this.synth || !this.isPlaying) return;
    this.synth.pause();
    this.isPaused = true;
    this.notify();
  }

  public resume(): void {
    if (!this.synth || !this.isPlaying) return;
    this.synth.resume();
    this.isPaused = false;
    this.notify();
  }

  public togglePlayPause(): void {
    if (this.isPaused) {
      this.resume();
    } else if (this.isPlaying) {
      this.pause();
    }
  }

  public next(): void {
    if (!this.isPlaying) return;
    if (this.currentIndex < this.paragraphs.length - 1) {
      this.currentIndex++;
      this.isPaused = false;
      this.speakCurrent();
    } else {
      this.stop();
    }
  }

  public prev(): void {
    if (!this.isPlaying) return;
    if (this.currentIndex > 0) {
      this.currentIndex--;
      this.isPaused = false;
      this.speakCurrent();
    }
  }

  public stop(): void {
    if (this.synth) {
      this.synth.cancel();
    }
    this.isPlaying = false;
    this.isPaused = false;
    this.currentUtterance = null;
    this.notify();
  }

  public getState(): TtsState {
    return {
      isPlaying: this.isPlaying,
      isPaused: this.isPaused,
      currentParagraphIndex: this.currentIndex,
      rate: this.rate,
      pitch: this.pitch,
      voiceURI: this.voiceURI,
    };
  }
}

export const ttsService = new TtsManager();
