#!/usr/bin/env python3
"""
SilverTongue Harvester — yt-dlp + FFmpeg Pipeline
=================================================
用于接收 Spring Boot 后端的采集请求，执行：
1. yt-dlp 下载 YouTube/Netflix 视频
2. FFmpeg 按起止时间切割
3. 上传切割后的文件到 MinIO
4. 回调后端更新状态

用法:
  python harvester.py --clip-id 123 --url "https://youtube.com/watch?v=xxx" --start 10.0 --end 25.0
"""

import argparse
import hashlib
import os
import subprocess
import sys
import tempfile
import time
import json
from pathlib import Path

# ===== Configuration =====
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "silvertongue")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "silvertongue_minio")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "st-materials")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# Try importing MinIO client
try:
    from minio import Minio
    HAS_MINIO = True
except ImportError:
    HAS_MINIO = False
    print("[WARN] minio package not installed, file will be saved locally")


def ensure_yt_dlp():
    """Ensure yt-dlp is available"""
    try:
        subprocess.run(["yt-dlp", "--version"], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[INFO] yt-dlp not found, attempting to install via pip...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "yt-dlp", "-q"])
        return True


def ensure_ffmpeg():
    """Ensure ffmpeg is available"""
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[ERROR] ffmpeg is required but not installed.")
        print("  Download from: https://ffmpeg.org/download.html")
        print("  Or: winget install ffmpeg")
        return False


def download_video(url: str, output_dir: str) -> str:
    """
    Download video using yt-dlp.
    Returns path to downloaded file.
    """
    print(f"[DOWNLOAD] Downloading: {url}")

    # yt-dlp will name the file based on the video title
    cmd = [
        "yt-dlp",
        "--no-playlist",
        "--format", "best[height<=1080]/best",
        "--output", os.path.join(output_dir, "%(title)s.%(ext)s"),
        "--no-warnings",
        url
    ]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"yt-dlp failed: {result.stderr}")

    # Find the downloaded file
    for f in os.listdir(output_dir):
        if f.endswith(('.mp4', '.webm', '.mkv', '.flv')):
            path = os.path.join(output_dir, f)
            print(f"[DOWNLOAD] Downloaded: {path}")
            return path

    raise FileNotFoundError(f"No video file found in {output_dir}")


def cut_video(input_path: str, output_dir: str, start_time: float, end_time: float) -> str:
    """
    Cut video using FFmpeg to the specified time range.
    Returns path to the cut file.
    """
    output_path = os.path.join(output_dir, f"clip_{int(start_time)}-{int(end_time)}.mp4")

    print(f"[FFMPEG] Cutting: {input_path} [{start_time}s - {end_time}s]")

    cmd = [
        "ffmpeg",
        "-y",                        # overwrite
        "-ss", str(start_time),      # start time
        "-i", input_path,
        "-to", str(end_time),        # end time
        "-c:v", "libx264",           # re-encode video
        "-c:a", "aac",               # re-encode audio
        "-preset", "fast",
        "-crf", "23",
        "-movflags", "+faststart",
        output_path
    ]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if result.returncode != 0:
        raise RuntimeError(f"FFmpeg cut failed: {result.stderr}")

    file_size = os.path.getsize(output_path)
    print(f"[FFMPEG] Cut complete: {output_path} ({file_size / 1024:.1f} KB)")
    return output_path


def extract_audio(input_path: str, output_dir: str, start_time: float, end_time: float) -> str:
    """
    Extract audio-only track (for shadowing practice).
    Returns path to the audio file.
    """
    output_path = os.path.join(output_dir, f"audio_{int(start_time)}-{int(end_time)}.mp3")

    cmd = [
        "ffmpeg",
        "-y",
        "-ss", str(start_time),
        "-i", input_path,
        "-to", str(end_time),
        "-vn",                       # no video
        "-c:a", "libmp3lame",
        "-q:a", "2",
        output_path
    ]

    subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if os.path.exists(output_path):
        print(f"[FFMPEG] Audio extracted: {output_path}")
    return output_path


def upload_to_minio(local_path: str, bucket: str, object_name: str) -> str:
    """Upload file to MinIO. Returns the object URL."""
    if not HAS_MINIO:
        print(f"[WARN] MinIO not available, file kept at: {local_path}")
        return f"file://{local_path}"

    client = Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=False
    )

    # Ensure bucket exists
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

    client.fput_object(bucket, object_name, local_path)
    url = client.presigned_get_object(bucket, object_name)
    print(f"[MINIO] Uploaded: {bucket}/{object_name}")
    return url


def compute_md5(file_path: str) -> str:
    """Compute MD5 hash of a file."""
    h = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def notify_backend(clip_id: int, status: int, storage_path: str = ""):
    """Notify Spring Boot backend about completion."""
    try:
        import urllib.request
        payload = json.dumps({
            "clipId": clip_id,
            "status": status,
            "storagePath": storage_path
        }).encode("utf-8")
        req = urllib.request.Request(
            f"{BACKEND_URL}/api/harvester/clip/callback",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        urllib.request.urlopen(req, timeout=10)
        print(f"[NOTIFY] Backend notified: clipId={clip_id}, status={status}")
    except Exception as e:
        print(f"[WARN] Failed to notify backend: {e}")


def main():
    parser = argparse.ArgumentParser(description="SilverTongue Harvester Pipeline")
    parser.add_argument("--clip-id", type=int, required=True, help="Clip ID in MySQL")
    parser.add_argument("--url", type=str, required=True, help="Video URL")
    parser.add_argument("--start", type=float, required=True, help="Start time in seconds")
    parser.add_argument("--end", type=float, required=True, help="End time in seconds")
    args = parser.parse_args()

    # Validate prerequisites
    if not ensure_ffmpeg():
        notify_backend(args.clip_id, 4, "")  # failed
        sys.exit(1)
    ensure_yt_dlp()

    print(f"[START] Clip #{args.clip_id}: {args.url} [{args.start}s - {args.end}s]")

    try:
        with tempfile.TemporaryDirectory(prefix="st_harvest_") as tmpdir:
            # Step 1: Download
            video_path = download_video(args.url, tmpdir)

            # Step 2: Cut video
            clip_path = cut_video(video_path, tmpdir, args.start, args.end)

            # Step 3: Extract audio
            audio_path = extract_audio(video_path, tmpdir, args.start, args.end)

            # Step 4: Upload to MinIO
            video_md5 = compute_md5(clip_path)
            video_object = f"clips/{args.clip_id}/video_{video_md5}.mp4"
            video_url = upload_to_minio(clip_path, MINIO_BUCKET, video_object)

            audio_md5 = compute_md5(audio_path)
            audio_object = f"clips/{args.clip_id}/audio_{audio_md5}.mp3"
            upload_to_minio(audio_path, MINIO_BUCKET, audio_object)

            # Step 5: Notify backend
            notify_backend(args.clip_id, 3, video_url)

            print(f"[DONE] Clip #{args.clip_id} processed successfully")
            sys.exit(0)

    except Exception as e:
        print(f"[ERROR] Clip #{args.clip_id} failed: {e}")
        notify_backend(args.clip_id, 4, "")
        sys.exit(1)


if __name__ == "__main__":
    main()
