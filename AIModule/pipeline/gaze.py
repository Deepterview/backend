import numpy as np
import mediapipe as mp

def analyze_gaze(frame: np.ndarray) -> dict:
    mp_face_mesh = mp.solutions.face_mesh

    image = frame
    
    if frame is None:
        return

    
    return