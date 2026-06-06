# Generated stub for silvertongue.dictionary proto
# Manual implementation matching dictionary.proto messages

import struct
from typing import List


class LookupRequest:
    def __init__(self, word: str = ""):
        self.word = word

    def SerializeToString(self) -> bytes:
        encoded = self.word.encode("utf-8")
        length = len(encoded)
        return struct.pack(f"!I{length}s", length, encoded)

    @staticmethod
    def FromString(data: bytes) -> "LookupRequest":
        if len(data) < 4:
            return LookupRequest()
        length = struct.unpack("!I", data[:4])[0]
        word = data[4:4 + length].decode("utf-8", errors="ignore")
        return LookupRequest(word=word)


class DictEntry:
    def __init__(self, pos: str = "", definition: str = "", translation: str = "",
                 examples: List[str] = None, phonetics_uk: str = "",
                 phonetics_us: str = "", audio_uk_url: str = "",
                 audio_us_url: str = ""):
        self.pos = pos
        self.definition = definition
        self.translation = translation
        self.examples = examples or []
        self.phonetics_uk = phonetics_uk
        self.phonetics_us = phonetics_us
        self.audio_uk_url = audio_uk_url
        self.audio_us_url = audio_us_url

    def to_dict(self) -> dict:
        return {
            "pos": self.pos,
            "definition": self.definition,
            "translation": self.translation,
            "examples": self.examples,
            "phonetics_uk": self.phonetics_uk,
            "phonetics_us": self.phonetics_us,
            "audio_uk_url": self.audio_uk_url,
            "audio_us_url": self.audio_us_url,
        }

    @staticmethod
    def from_dict(d: dict) -> "DictEntry":
        return DictEntry(
            pos=d.get("pos", ""),
            definition=d.get("definition", ""),
            translation=d.get("translation", ""),
            examples=d.get("examples", []),
            phonetics_uk=d.get("phonetics_uk", ""),
            phonetics_us=d.get("phonetics_us", ""),
            audio_uk_url=d.get("audio_uk_url", ""),
            audio_us_url=d.get("audio_us_url", ""),
        )


class LookupResponse:
    def __init__(self, found: bool = False, word: str = "",
                 entries: List[DictEntry] = None):
        self.found = found
        self.word = word
        self.entries = entries or []

    def SerializeToString(self) -> bytes:
        import json
        data = {
            "found": self.found,
            "word": self.word,
            "entries": [e.to_dict() for e in self.entries],
        }
        return json.dumps(data, ensure_ascii=False).encode("utf-8")

    @staticmethod
    def FromString(data: bytes) -> "LookupResponse":
        import json
        try:
            d = json.loads(data.decode("utf-8", errors="ignore"))
            return LookupResponse(
                found=d.get("found", False),
                word=d.get("word", ""),
                entries=[DictEntry.from_dict(e) for e in d.get("entries", [])],
            )
        except Exception:
            return LookupResponse()
