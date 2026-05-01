"""Test: apply only Arm_R rotation to find correct elbow axis."""
import json, math
import numpy as np

def rot_x(d):
    r=math.radians(d); return np.array([[1,0,0],[0,math.cos(r),-math.sin(r)],[0,math.sin(r),math.cos(r)]])
def rot_y(d):
    r=math.radians(d); return np.array([[math.cos(r),0,math.sin(r)],[0,1,0],[-math.sin(r),0,math.cos(r)]])
def rot_z(d):
    r=math.radians(d); return np.array([[math.cos(r),-math.sin(r),0],[math.sin(r),math.cos(r),0],[0,0,1]])

def apply_rot(rot3, m4):
    r=m4.copy(); r[:3,:3]=rot3@m4[:3,:3]; return r

with open("src/main/resources/assets/combat_arts/models/animations/biped/living/idle.json") as f:
    idle_data=json.load(f)

im = {e["name"]: np.array(e["transform"][0]).reshape(4,4) for e in idle_data["animation"]}

# TEST: bend Arm_R 90° around different axes — only this joint moves
TEST_AXIS = "ry"  # Try "rx", "ry", "rz", "rx_neg", "ry_neg", "rz_neg"
TEST_ANGLE = 90

if TEST_AXIS == "rx": test_rot = rot_x(TEST_ANGLE)
elif TEST_AXIS == "rx_neg": test_rot = rot_x(-TEST_ANGLE)
elif TEST_AXIS == "ry": test_rot = rot_y(TEST_ANGLE)
elif TEST_AXIS == "ry_neg": test_rot = rot_y(-TEST_ANGLE)
elif TEST_AXIS == "rz": test_rot = rot_z(TEST_ANGLE)
elif TEST_AXIS == "rz_neg": test_rot = rot_z(-TEST_ANGLE)

times = [0.0, 0.4, 0.8]
ae = []
for jn, idle_m in im.items():
    if jn == "Arm_R":
        bent = apply_rot(test_rot, idle_m)
        kf = [idle_m, bent, bent]
    else:
        kf = [idle_m, idle_m, idle_m]
    ae.append({"name": jn, "time": times, "transform": [m.flatten().tolist() for m in kf]})

with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/draw_weapon.json", "w") as f:
    json.dump({"animation": ae}, f)

# Same for sheath (just so it loads)
with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/sheath_weapon.json", "w") as f:
    json.dump({"animation": ae}, f)

print(f"Test: Arm_R = {TEST_AXIS}({TEST_ANGLE})")
