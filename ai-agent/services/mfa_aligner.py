import random
from typing import Dict, Any

class MFAAligner:
    """
    Mock implementation of Montreal Forced Aligner.
    In a real environment, this would call the `mfa align` subprocess,
    parse the resulting TextGrid files, and compute phoneme-level scores.
    """
    
    def __init__(self):
        # We might load an acoustic model or dictionary here in the future
        pass
        
    def align_and_score(self, audio_data: bytes, target_text: str) -> Dict[str, Any]:
        """
        Mock alignment and scoring.
        Returns a dictionary containing accuracy, fluency, completeness, and word/phoneme details.
        """
        words = target_text.split()
        word_assessments = []
        
        current_time = 0.0
        
        for word in words:
            # Mock 1-3 phonemes per word
            num_phonemes = random.randint(1, 3)
            phonemes = []
            word_score_total = 0.0
            
            for i in range(num_phonemes):
                duration = random.uniform(0.1, 0.3)
                # Random score between 60 and 100
                score = random.uniform(60.0, 100.0)
                phonemes.append({
                    "phoneme": f"ph_{i}",
                    "score": round(score, 2),
                    "start_time": round(current_time, 2),
                    "end_time": round(current_time + duration, 2)
                })
                current_time += duration
                word_score_total += score
                
            word_score = word_score_total / num_phonemes
            word_assessments.append({
                "word": word,
                "score": round(word_score, 2),
                "phonemes": phonemes
            })
            
            current_time += random.uniform(0.05, 0.15) # pause between words
            
        # Overall scores
        accuracy = random.uniform(70.0, 95.0)
        fluency = random.uniform(75.0, 98.0)
        completeness = 100.0 # Assuming all words were read for the mock
        
        final_score = (accuracy * 0.5) + (fluency * 0.3) + (completeness * 0.2)
        
        return {
            "final_score": round(final_score, 2),
            "accuracy": round(accuracy, 2),
            "fluency": round(fluency, 2),
            "completeness": round(completeness, 2),
            "words": word_assessments
        }
