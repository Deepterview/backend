import os
import cv2
from pipeline.extractor import extract_audio

def run_pipeline(video_path: str, output_dir: str = "output", frame_interval: int = 5 ) -> dict:
    os.makedirs(output_dir, exist_ok=True)
    audio_path = extract_audio(video_path, output_dir)

    cap = cv2.VideoCapture(video_path)

    while True:
        ret, frame = cap.read()
        if not ret:
            break
        """
        if frame_index % frame_interval == 0:
            gaze = analyze_gaze(frame)      # 배열 직접 전달
            emotion = analyze_emotion(frame) # 배열 직접 전달
            frame_results.append({...})
        """

        frame_index += 1

    cap.release()

    return

if __name__ == "__main__":
    result = run_pipeline(
        video_path="Analyze_Test.mp4",
        output_dir="output",
        frame_interval=5   
    )