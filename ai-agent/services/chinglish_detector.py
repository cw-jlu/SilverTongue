import re
from typing import Dict, Any

class ChinglishDetector:
    def __init__(self):
        # A simple ruleset for detecting common Chinglish patterns
        self.rules = [
            {
                "pattern": r"\b(very like)\b",
                "category": "Grammar / Word Choice",
                "suggestion": "really like",
                "severity": "medium"
            },
            {
                "pattern": r"\b(play phone)\b",
                "category": "Vocabulary",
                "suggestion": "use my phone / play on my phone",
                "severity": "medium"
            },
            {
                "pattern": r"\b(open the light)\b",
                "category": "Vocabulary",
                "suggestion": "turn on the light",
                "severity": "high"
            },
            {
                "pattern": r"\b(my english is poor)\b",
                "category": "Pragmatics / Naturalness",
                "suggestion": "I'm still improving my English / My English isn't great yet",
                "severity": "low"
            }
        ]
        
    def detect(self, text: str) -> Dict[str, Any]:
        """
        Detect Chinglish patterns in the given text.
        """
        patterns_found = []
        has_chinglish = False
        
        lower_text = text.lower()
        
        for rule in self.rules:
            matches = re.finditer(rule["pattern"], lower_text)
            for match in matches:
                has_chinglish = True
                patterns_found.append({
                    "category": rule["category"],
                    "error_text": match.group(0),
                    "suggestion": rule["suggestion"],
                    "severity": rule["severity"]
                })
                
        return {
            "has_chinglish": has_chinglish,
            "patterns": patterns_found
        }
