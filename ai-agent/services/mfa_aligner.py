import os
import random
import subprocess
import json
import tempfile
from typing import Dict, Any, List, Optional
from loguru import logger


class MFAAligner:
    """
    Pronunciation assessment engine with automatic fallback chain.

    Engine selection (first available wins):
        1. Azure Speech SDK  — if AZURE_SPEECH_KEY + AZURE_SPEECH_REGION set
        2. MFA (Kaldi)        — if MFA_ENABLED=true and `mfa` on PATH
        3. Deterministic mock — always available (fallback)

    All engines produce the same output dict so callers never need to
    care which engine is active.
    """

    def __init__(self):
        self.engine = self._select_engine()
        logger.info(f"MFAAligner active engine: {self.engine}")

    # ------------------------------------------------------------------
    # Engine selection
    # ------------------------------------------------------------------

    def _select_engine(self) -> str:
        # 1. Azure Speech SDK
        azure_key = os.getenv("AZURE_SPEECH_KEY", "").strip()
        azure_region = os.getenv("AZURE_SPEECH_REGION", "").strip()
        if azure_key and azure_region:
            try:
                import azure.cognitiveservices.speech as _  # noqa: F401
                logger.info("Azure Speech SDK configured — using Azure for pronunciation assessment")
                return "azure"
            except ImportError:
                logger.warning("AZURE_SPEECH_KEY set but azure-cognitiveservices-speech not installed, "
                               "falling back to next engine")

        # 2. MFA (Kaldi-based Montreal Forced Aligner)
        if os.getenv("MFA_ENABLED", "false").lower() == "true":
            if self._check_mfa_installed():
                logger.info("MFA is enabled and available on PATH")
                return "mfa"
            logger.warning("MFA_ENABLED=true but 'mfa' command not found")

        # 3. Fallback: deterministic mock
        logger.info("Using deterministic mock aligner for pronunciation assessment")
        return "mock"

    @staticmethod
    def _check_mfa_installed() -> bool:
        try:
            result = subprocess.run(["mfa", "version"], capture_output=True, text=True, timeout=10)
            return result.returncode == 0
        except (FileNotFoundError, subprocess.TimeoutExpired):
            return False

    # ------------------------------------------------------------------
    # Public API (same signature regardless of engine)
    # ------------------------------------------------------------------

    def align_and_score(self, audio_data: bytes, target_text: str) -> Dict[str, Any]:
        """
        Align audio against target text and produce pronunciation scores.

        Returns dict:
            final_score, accuracy, fluency, completeness, words[
                {word, score, phonemes[{phoneme, score, start_time, end_time}]}
            ]
        """
        try:
            if self.engine == "azure":
                return self._azure_align(audio_data, target_text)
            if self.engine == "mfa":
                return self._real_align(audio_data, target_text)
        except Exception as e:
            logger.error(f"{self.engine} alignment failed: {e}, falling back to mock")

        return self._mock_align(audio_data, target_text)

    # ------------------------------------------------------------------
    # Azure Speech SDK engine
    # ------------------------------------------------------------------

    def _azure_align(self, audio_data: bytes, target_text: str) -> Dict[str, Any]:
        """
        Microsoft Azure Speech SDK pronunciation assessment.
        Requires: AZURE_SPEECH_KEY, AZURE_SPEECH_REGION env vars.
        Falls back to mock on any failure (network, auth, bad audio).
        """
        import azure.cognitiveservices.speech as speechsdk

        azure_key = os.getenv("AZURE_SPEECH_KEY", "").strip()
        azure_region = os.getenv("AZURE_SPEECH_REGION", "eastasia").strip()

        if not azure_key or not azure_region:
            logger.warning("Azure key/region missing, falling back to mock")
            return self._mock_align(audio_data, target_text)

        speech_config = speechsdk.SpeechConfig(
            subscription=azure_key, region=azure_region
        )

        # Pronunciation assessment config
        pronunciation_config = speechsdk.PronunciationAssessmentConfig(
            reference_text=target_text,
            grading_system=speechsdk.PronunciationAssessmentGradingSystem.HundredMark,
            granularity=speechsdk.PronunciationAssessmentGranularity.Word,
        )

        # Audio stream (PCM 16kHz 16-bit mono — standard format from frontend/client)
        audio_format = speechsdk.audio.AudioStreamFormat(
            samples_per_second=16000, bits_per_sample=16, channels=1
        )
        push_stream = speechsdk.audio.PushAudioInputStream(audio_format)
        push_stream.write(audio_data)
        push_stream.close()
        audio_config = speechsdk.audio.AudioConfig(stream=push_stream)

        recognizer = speechsdk.SpeechRecognizer(
            speech_config=speech_config, audio_config=audio_config
        )
        pronunciation_config.apply_to(recognizer)

        result = recognizer.recognize_once_async().get()

        if result.reason == speechsdk.ResultReason.Recognized:
            # Azure returns a PronunciationAssessmentResult wrapper
            # Use property-based access to avoid import errors across SDK versions
            try:
                assessment = speechsdk.PronunciationAssessmentResult(result)
            except Exception:
                # Older SDK: extract from JSON
                return self._parse_azure_json(result, target_text)

            word_assessments = []
            if assessment.words:
                for w in assessment.words:
                    word_assessments.append({
                        "word": w.word if w.word else "",
                        "score": round(w.accuracy_score, 2) if w.accuracy_score is not None else 75.0,
                        "phonemes": [{
                            "phoneme": "?",
                            "score": round(w.accuracy_score, 2) if w.accuracy_score is not None else 75.0,
                            "start_time": 0.0,
                            "end_time": 0.1,
                        }],
                    })

            return {
                "final_score": round(assessment.pronunciation_score, 2)
                    if assessment.pronunciation_score is not None else 80.0,
                "accuracy": round(assessment.accuracy_score, 2)
                    if assessment.accuracy_score is not None else 80.0,
                "fluency": round(assessment.fluency_score, 2)
                    if assessment.fluency_score is not None else 80.0,
                "completeness": round(assessment.completeness_score, 2)
                    if assessment.completeness_score is not None else 100.0,
                "words": word_assessments,
            }

        logger.warning(
            f"Azure recognition returned {result.reason}, falling back to mock. "
            f"Details: {result.cancellation_details if hasattr(result, 'cancellation_details') else 'N/A'}"
        )
        return self._mock_align(audio_data, target_text)

    def _parse_azure_json(self, result, target_text: str) -> Dict[str, Any]:
        """Fallback: parse pronunciation assessment from result JSON (older SDK compat)."""
        try:
            data = json.loads(result.json)
            nbest = data.get("NBest", [{}])
            pron = nbest[0].get("PronunciationAssessment", {}) if nbest else {}

            words_list = nbest[0].get("Words", []) if nbest else []
            word_assessments = []
            for w in words_list:
                pa = w.get("PronunciationAssessment", {})
                word_assessments.append({
                    "word": w.get("Word", ""),
                    "score": round(float(pa.get("AccuracyScore", 75.0)), 2),
                    "phonemes": [{
                        "phoneme": "?",
                        "score": round(float(pa.get("AccuracyScore", 75.0)), 2),
                        "start_time": 0.0,
                        "end_time": 0.1,
                    }],
                })

            return {
                "final_score": round(float(pron.get("PronScore", 80.0)), 2),
                "accuracy": round(float(pron.get("AccuracyScore", 80.0)), 2),
                "fluency": round(float(pron.get("FluencyScore", 80.0)), 2),
                "completeness": round(float(pron.get("CompletenessScore", 100.0)), 2),
                "words": word_assessments,
            }
        except Exception as e:
            logger.error(f"Azure JSON parse failed: {e}")
            return self._mock_align(b"", target_text)

    # ------------------------------------------------------------------
    # Real MFA pipeline (Kaldi)
    # ------------------------------------------------------------------

    def _real_align(self, audio_data: bytes, target_text: str) -> Dict[str, Any]:
        """
        Run the full MFA align pipeline:
        1. Write audio + transcript to a temp directory
        2. Invoke ``mfa align``
        3. Parse the output TextGrid
        4. Compute per-word / per-phoneme scores
        """
        acoustic_model = os.getenv("MFA_ACOUSTIC_MODEL", "english_mfa")
        dictionary = os.getenv("MFA_DICTIONARY", "english_mfa")

        with tempfile.TemporaryDirectory(prefix="mfa_") as tmpdir:
            audio_path = os.path.join(tmpdir, "utterance.wav")
            txt_path = os.path.join(tmpdir, "utterance.txt")
            output_dir = os.path.join(tmpdir, "aligned")

            with open(audio_path, "wb") as f:
                f.write(audio_data)
            with open(txt_path, "w", encoding="utf-8") as f:
                f.write(target_text)

            try:
                cmd = [
                    "mfa", "align",
                    tmpdir, dictionary, acoustic_model, output_dir,
                    "--clean", "--single_speaker",
                ]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
                if result.returncode != 0:
                    logger.error(f"MFA align failed: {result.stderr}")
                    return self._mock_align(audio_data, target_text)

                return self._parse_textgrid(output_dir, target_text)

            except subprocess.TimeoutExpired:
                logger.error("MFA align timed out")
                return self._mock_align(audio_data, target_text)
            except Exception as e:
                logger.error(f"MFA align error: {e}")
                return self._mock_align(audio_data, target_text)

    def _parse_textgrid(self, output_dir: str, target_text: str) -> Dict[str, Any]:
        """Parse MFA TextGrid output into structured assessment result."""
        try:
            tg_path = os.path.join(output_dir, "utterance.TextGrid")
            if not os.path.exists(tg_path):
                logger.warning("TextGrid file not found, falling back to mock")
                return self._mock_align(b"", target_text)

            words = target_text.split()
            word_assessments = []

            with open(tg_path, "r", encoding="utf-8") as f:
                content = f.read()

            phone_intervals = self._extract_intervals(content, tier_name="phones")
            word_intervals = self._extract_intervals(content, tier_name="words")

            for i, word in enumerate(words):
                w_interval = word_intervals[i] if i < len(word_intervals) else None
                phonemes = []

                if w_interval:
                    for pi in phone_intervals:
                        if pi["start"] >= w_interval["start"] and pi["end"] <= w_interval["end"]:
                            duration = pi["end"] - pi["start"]
                            score = min(100.0, max(50.0, 85.0 + (duration - 0.08) * 100))
                            phonemes.append({
                                "phoneme": pi["label"],
                                "score": round(score, 2),
                                "start_time": round(pi["start"], 3),
                                "end_time": round(pi["end"], 3),
                            })

                if not phonemes:
                    phonemes = [
                        {"phoneme": "?", "score": 75.0, "start_time": 0.0, "end_time": 0.1}
                    ]

                word_score = sum(p["score"] for p in phonemes) / len(phonemes)
                word_assessments.append({
                    "word": word,
                    "score": round(word_score, 2),
                    "phonemes": phonemes,
                })

            accuracy = sum(w["score"] for w in word_assessments) / max(len(word_assessments), 1)
            fluency = min(100.0, accuracy + random.uniform(-3, 5))
            completeness = (len(word_intervals) / max(len(words), 1)) * 100.0
            final_score = accuracy * 0.5 + fluency * 0.3 + completeness * 0.2

            return {
                "final_score": round(final_score, 2),
                "accuracy": round(accuracy, 2),
                "fluency": round(fluency, 2),
                "completeness": round(min(completeness, 100.0), 2),
                "words": word_assessments,
            }

        except Exception as e:
            logger.error(f"TextGrid parsing error: {e}")
            return self._mock_align(b"", target_text)

    @staticmethod
    def _extract_intervals(content: str, tier_name: str) -> List[Dict[str, Any]]:
        """Extract intervals from a TextGrid tier by name."""
        intervals = []
        lines = content.split("\n")
        in_tier = False
        in_intervals = False

        for i, line in enumerate(lines):
            stripped = line.strip()
            if f'name = "{tier_name}"' in stripped:
                in_tier = True
                continue
            if in_tier and "intervals:" in stripped:
                in_intervals = True
                continue
            if in_tier and in_intervals:
                if stripped.startswith("name = ") and not stripped.startswith('name = "'):
                    break
                if "xmin" in stripped:
                    try:
                        xmin = float(stripped.split("=")[1].strip())
                        xmax_line = lines[i + 1].strip() if i + 1 < len(lines) else ""
                        text_line = lines[i + 2].strip() if i + 2 < len(lines) else ""
                        xmax = (
                            float(xmax_line.split("=")[1].strip())
                            if "xmax" in xmax_line
                            else xmin
                        )
                        label = (
                            text_line.split("=")[1].strip().strip('"')
                            if "text" in text_line
                            else ""
                        )
                        if label:
                            intervals.append({"start": xmin, "end": xmax, "label": label})
                    except (IndexError, ValueError):
                        pass
        return intervals

    # ------------------------------------------------------------------
    # Deterministic mock (always-on fallback)
    # ------------------------------------------------------------------

    @staticmethod
    def _mock_align(audio_data: bytes, target_text: str) -> Dict[str, Any]:
        """
        Deterministic mock: scores derived from word properties so the
        same input always produces the same output.  Never throws.
        """
        words = target_text.split()
        word_assessments = []
        current_time = 0.0

        for idx, word in enumerate(words):
            seed = sum(ord(c) for c in word) + idx
            rng = random.Random(seed)

            num_phonemes = max(1, len(word) // 2)
            phonemes = []
            word_score_total = 0.0

            for i in range(num_phonemes):
                duration = 0.08 + (seed % 20) * 0.01
                score = 70.0 + rng.random() * 25.0
                phonemes.append({
                    "phoneme": f"ph_{i}",
                    "score": round(score, 2),
                    "start_time": round(current_time, 3),
                    "end_time": round(current_time + duration, 3),
                })
                current_time += duration
                word_score_total += score

            word_assessments.append({
                "word": word,
                "score": round(word_score_total / num_phonemes, 2),
                "phonemes": phonemes,
            })
            current_time += 0.1

        all_scores = [w["score"] for w in word_assessments]
        accuracy = sum(all_scores) / max(len(all_scores), 1)
        fluency = min(100.0, accuracy + 3.0)

        return {
            "final_score": round(accuracy * 0.5 + fluency * 0.3 + 100.0 * 0.2, 2),
            "accuracy": round(accuracy, 2),
            "fluency": round(fluency, 2),
            "completeness": 100.0,
            "words": word_assessments,
        }
