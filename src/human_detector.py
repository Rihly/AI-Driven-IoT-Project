#!/usr/bin/env python3
"""
human_detector.py — Raspberry Pi Human Detection Camera
Uses Picamera2 + OpenCV HOG person detector to periodically check for humans.
Records a short video clip whenever a person is detected.

Requirements:
    sudo apt install -y python3-picamera2 python3-opencv
    pip3 install opencv-python-headless  # if not already available via apt
"""

import cv2
import time
import logging
import os
from datetime import datetime
from picamera2 import Picamera2
from picamera2.encoders import H264Encoder
from picamera2.outputs import FfmpegOutput

# ── Configuration ─────────────────────────────────────────────────────────────

CHECK_INTERVAL_SECONDS = 10       # How often to check for a human (seconds)
CLIP_DURATION_SECONDS  = 15       # How long to record when a human is detected
CLIPS_DIR              = "clips"  # Folder where video clips are saved

# Preview / detection frame size (smaller = faster detection)
DETECT_WIDTH  = 640
DETECT_HEIGHT = 480

# Recording resolution (can be higher than detection size)
RECORD_WIDTH  = 1280
RECORD_HEIGHT = 720

# HOG detector tuning — lower winStride = more thorough but slower
HOG_WIN_STRIDE = (8, 8)
HOG_PADDING    = (4, 4)
HOG_SCALE      = 1.05

# Minimum detection confidence: ignore boxes smaller than this area
MIN_BOX_AREA = 3000   # pixels²

# ── Logging setup ──────────────────────────────────────────────────────────────

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger(__name__)

# ── Helpers ────────────────────────────────────────────────────────────────────

def ensure_clips_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)
    log.info(f"Clips will be saved to: {os.path.abspath(path)}")


def build_hog_detector() -> cv2.HOGDescriptor:
    """Return an OpenCV HOG people detector with default SVM weights."""
    hog = cv2.HOGDescriptor()
    hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())
    return hog


def detect_humans(frame_bgr, hog: cv2.HOGDescriptor) -> list:
    """
    Run HOG person detection on a BGR frame.
    Returns a list of bounding boxes [(x, y, w, h), ...] for detected people.
    """
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

    boxes, weights = hog.detectMultiScale(
        gray,
        winStride=HOG_WIN_STRIDE,
        padding=HOG_PADDING,
        scale=HOG_SCALE,
    )

    # Filter out tiny false-positive boxes
    confirmed = [
        (x, y, w, h)
        for (x, y, w, h) in (boxes if len(boxes) else [])
        if w * h >= MIN_BOX_AREA
    ]
    return confirmed


def clip_filename() -> str:
    """Generate a timestamped filename for a new clip."""
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    return os.path.join(CLIPS_DIR, f"human_{ts}.mp4")


# ── Main loop ──────────────────────────────────────────────────────────────────

def main():
    ensure_clips_dir(CLIPS_DIR)
    hog = build_hog_detector()

    # Initialise Picamera2
    cam = Picamera2()

    # Still/preview config used for periodic snapshots
    detect_config = cam.create_still_configuration(
        main={"size": (DETECT_WIDTH, DETECT_HEIGHT), "format": "BGR888"}
    )

    # Video config used when recording a clip
    record_config = cam.create_video_configuration(
        main={"size": (RECORD_WIDTH, RECORD_HEIGHT), "format": "RGB888"}
    )

    cam.configure(detect_config)
    cam.start()
    log.info("Camera started. Warming up for 2 seconds …")
    time.sleep(2)

    encoder = H264Encoder()

    log.info(
        f"Monitoring started — checking every {CHECK_INTERVAL_SECONDS}s. "
        "Press Ctrl+C to stop."
    )

    try:
        while True:
            # ── 1. Grab a snapshot for detection ──────────────────────────────
            log.info("Checking for humans …")
            frame = cam.capture_array()          # BGR888 array

            humans = detect_humans(frame, hog)

            if not humans:
                log.info(f"No humans detected. Next check in {CHECK_INTERVAL_SECONDS}s.")
                time.sleep(CHECK_INTERVAL_SECONDS)
                continue

            # ── 2. Human detected — switch to video mode and record ───────────
            log.info(f"⚠  Human(s) detected ({len(humans)} box(es))! Recording …")

            output_path = clip_filename()

            cam.stop()
            cam.configure(record_config)
            cam.start()

            output = FfmpegOutput(output_path)
            cam.start_recording(encoder, output)
            time.sleep(CLIP_DURATION_SECONDS)
            cam.stop_recording()

            log.info(f"Clip saved → {output_path}")

            # ── 3. Switch back to detection mode ──────────────────────────────
            cam.stop()
            cam.configure(detect_config)
            cam.start()
            time.sleep(1)   # brief settle before next check

            log.info(f"Resuming monitoring. Next check in {CHECK_INTERVAL_SECONDS}s.")
            time.sleep(CHECK_INTERVAL_SECONDS)

    except KeyboardInterrupt:
        log.info("Interrupted by user — shutting down.")
    finally:
        cam.stop()
        log.info("Camera stopped. Goodbye!")


if __name__ == "__main__":
    main()
