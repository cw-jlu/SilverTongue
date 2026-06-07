#!/usr/bin/env python3
"""
SilverTongue Harvester - yt-dlp + FFmpeg pipeline.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "silvertongue")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "silvertongue_minio")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "st-materials")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

try:
    from minio import Minio

    HAS_MINIO = True
except ImportError:
    HAS_MINIO = False
    print("[WARN] minio package not installed, file will be saved locally")


def ensure_yt_dlp():
    try:
        subprocess.run(["yt-dlp", "--version"], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[INFO] yt-dlp not found, attempting to install via pip...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "yt-dlp", "-q"])
        return True


def ensure_ffmpeg():
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[ERROR] ffmpeg is required but not installed.")
        print("  Download from: https://ffmpeg.org/download.html")
        print("  Or: winget install ffmpeg")
        return False


def download_video(url: str, output_dir: str) -> str:
    print(f"[DOWNLOAD] Downloading: {url}")

    cmd = [
        "yt-dlp",
        "--no-playlist",
        "--format",
        "best[height<=1080]/best",
        "--output",
        os.path.join(output_dir, "%(title)s.%(ext)s"),
        "--no-warnings",
        url,
    ]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"yt-dlp failed: {result.stderr}")

    for name in os.listdir(output_dir):
        if name.endswith((".mp4", ".webm", ".mkv", ".flv")):
            path = os.path.join(output_dir, name)
            print(f"[DOWNLOAD] Downloaded: {path}")
            return path

    raise FileNotFoundError(f"No video file found in {output_dir}")


def cut_video(input_path: str, output_dir: str, start_time: float, end_time: float) -> str:
    output_path = os.path.join(output_dir, f"clip_{int(start_time)}-{int(end_time)}.mp4")

    print(f"[FFMPEG] Cutting: {input_path} [{start_time}s - {end_time}s]")

    cmd = [
        "ffmpeg",
        "-y",
        "-ss",
        str(start_time),
        "-i",
        input_path,
        "-to",
        str(end_time),
        "-c:v",
        "libx264",
        "-c:a",
        "aac",
        "-preset",
        "fast",
        "-crf",
        "23",
        "-movflags",
        "+faststart",
        output_path,
    ]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if result.returncode != 0:
        raise RuntimeError(f"FFmpeg cut failed: {result.stderr}")

    file_size = os.path.getsize(output_path)
    print(f"[FFMPEG] Cut complete: {output_path} ({file_size / 1024:.1f} KB)")
    return output_path


def extract_audio(input_path: str, output_dir: str, start_time: float, end_time: float) -> str:
    output_path = os.path.join(output_dir, f"audio_{int(start_time)}-{int(end_time)}.mp3")

    cmd = [
        "ffmpeg",
        "-y",
        "-ss",
        str(start_time),
        "-i",
        input_path,
        "-to",
        str(end_time),
        "-vn",
        "-c:a",
        "libmp3lame",
        "-q:a",
        "2",
        output_path,
    ]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        print(f"[WARN] Audio extraction failed: {result.stderr}")
        return ""

    if os.path.exists(output_path):
        print(f"[FFMPEG] Audio extracted: {output_path}")
        return output_path

    return ""


def upload_to_minio(local_path: str, bucket: str, object_name: str) -> str:
    if not HAS_MINIO:
        print(f"[WARN] MinIO not available, file kept at: {local_path}")
        return f"file://{local_path}"

    client = Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=False,
    )

    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

    client.fput_object(bucket, object_name, local_path)
    print(f"[MINIO] Uploaded: {bucket}/{object_name}")
    return f"{bucket}/{object_name}"


def compute_md5(file_path: str) -> str:
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as handle:
        for chunk in iter(lambda: handle.read(8192), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()


def notify_backend(clip_id: int, status: int, storage_path: str = ""):
    try:
        import urllib.request

        payload = json.dumps(
            {
                "clipId": clip_id,
                "status": status,
                "storagePath": storage_path,
            }
        ).encode("utf-8")
        request = urllib.request.Request(
            f"{BACKEND_URL}/api/clips/callback",
            data=payload,
            headers={"Content-Type": "application/json"},
        )
        urllib.request.urlopen(request, timeout=10)
        print(f"[NOTIFY] Backend notified: clipId={clip_id}, status={status}")
    except Exception as exc:
        print(f"[WARN] Failed to notify backend: {exc}")


def process_clip(clip_id: int, url: str, start_time: float, end_time: float):
    if not ensure_ffmpeg():
        notify_backend(clip_id, 4, "")
        raise RuntimeError("ffmpeg is required but not installed")
    ensure_yt_dlp()

    print(f"[START] Clip #{clip_id}: {url} [{start_time}s - {end_time}s]")

    with tempfile.TemporaryDirectory(prefix="st_harvest_") as tmpdir:
        video_path = download_video(url, tmpdir)
        clip_path = cut_video(video_path, tmpdir, start_time, end_time)
        audio_path = extract_audio(video_path, tmpdir, start_time, end_time)

        video_md5 = compute_md5(clip_path)
        video_object = f"clips/{clip_id}/video_{video_md5}.mp4"
        video_storage_path = upload_to_minio(clip_path, MINIO_BUCKET, video_object)

        if audio_path and os.path.exists(audio_path):
            audio_md5 = compute_md5(audio_path)
            audio_object = f"clips/{clip_id}/audio_{audio_md5}.mp3"
            upload_to_minio(audio_path, MINIO_BUCKET, audio_object)

        notify_backend(clip_id, 3, video_storage_path)
        print(f"[DONE] Clip #{clip_id} processed successfully")
        return video_storage_path


def main():
    parser = argparse.ArgumentParser(description="SilverTongue Harvester Pipeline")
    parser.add_argument("--clip-id", type=int, required=True, help="Clip ID in MySQL")
    parser.add_argument("--url", type=str, required=True, help="Video URL")
    parser.add_argument("--start", type=float, required=True, help="Start time in seconds")
    parser.add_argument("--end", type=float, required=True, help="End time in seconds")
    args = parser.parse_args()

    try:
        process_clip(args.clip_id, args.url, args.start, args.end)
        sys.exit(0)
    except Exception as exc:
        print(f"[ERROR] Clip #{args.clip_id} failed: {exc}")
        notify_backend(args.clip_id, 4, "")
        sys.exit(1)


if __name__ == "__main__":
    main()
