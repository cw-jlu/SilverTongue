import grpc
from loguru import logger
import sys
import os

# Add proto to path
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'proto'))
import assessment_pb2
import assessment_pb2_grpc

from services.mfa_aligner import MFAAligner
from services.chinglish_detector import ChinglishDetector

class AssessmentServiceServicer(assessment_pb2_grpc.AssessmentServiceServicer):
    def __init__(self):
        self.aligner = MFAAligner()
        self.detector = ChinglishDetector()
        logger.info("Initialized AssessmentServiceServicer")
        
    def AssessPronunciation(self, request, context):
        """
        gRPC implementation for M8: Pronunciation Assessment
        """
        logger.info(f"Received AssessPronunciation request for session: {request.session_id}")
        
        # Call MFA aligner mock
        result = self.aligner.align_and_score(request.audio_data, request.target_text)
        
        # Build Protobuf response
        response = assessment_pb2.AssessmentResponse(
            score=result["final_score"],
            accuracy=result["accuracy"],
            fluency=result["fluency"],
            completeness=result["completeness"],
        )
        
        # Add word assessments
        for w in result["words"]:
            word_obj = response.words.add()
            word_obj.word = w["word"]
            word_obj.score = w["score"]
            for p in w["phonemes"]:
                phoneme_obj = word_obj.phonemes.add()
                phoneme_obj.phoneme = p["phoneme"]
                phoneme_obj.score = p["score"]
                phoneme_obj.start_time = p["start_time"]
                phoneme_obj.end_time = p["end_time"]
                
        return response
        
    def DetectChinglish(self, request, context):
        """
        gRPC implementation for M11: Chinglish Detection
        """
        logger.info(f"Received DetectChinglish request: {request.text}")
        
        result = self.detector.detect(request.text)
        
        response = assessment_pb2.ChinglishResponse(
            has_chinglish=result["has_chinglish"]
        )
        
        for p in result["patterns"]:
            pattern_obj = response.patterns.add()
            pattern_obj.category = p["category"]
            pattern_obj.error_text = p["error_text"]
            pattern_obj.suggestion = p["suggestion"]
            pattern_obj.severity = p["severity"]
            
        return response
