"""Generate draw/sheath animations using confirmed tweaker values and correct bone mapping.
Confirmed by user:
- ef_shoulder (Arm_R): [20, 210, 50] = reach behind pose
- ef_arm (Hand_R): [80, -20, 0] = elbow bend
"""
import json, math
import numpy as np

def rot_x(d):
    r=math.radians(d); return np.array([[1,0,0],[0,math.cos(r),-math.sin(r)],[0,math.sin(r),math.cos(r)]])
def rot_y(d):
    r=math.radians(d); return np.array([[math.cos(r),0,math.sin(r)],[0,1,0],[-math.sin(r),0,math.cos(r)]])
def rot_z(d):
    r=math.radians(d); return np.array([[math.cos(r),-math.sin(r),0],[math.sin(r),math.cos(r),0],[0,0,1]])

def apply_rot(rx, ry, rz, mat4):
    rot3 = rot_x(rx) @ rot_y(ry) @ rot_z(rz)
    r = mat4.copy()
    r[:3,:3] = rot3 @ mat4[:3,:3]
    return r

def mat_to_quat(m):
    trace=m[0,0]+m[1,1]+m[2,2]
    if trace>0:
        s=0.5/math.sqrt(trace+1.0); w=0.25/s; x=(m[2,1]-m[1,2])*s; y=(m[0,2]-m[2,0])*s; z=(m[1,0]-m[0,1])*s
    elif m[0,0]>m[1,1] and m[0,0]>m[2,2]:
        s=2.0*math.sqrt(1.0+m[0,0]-m[1,1]-m[2,2]); w=(m[2,1]-m[1,2])/s; x=0.25*s; y=(m[0,1]+m[1,0])/s; z=(m[0,2]+m[2,0])/s
    elif m[1,1]>m[2,2]:
        s=2.0*math.sqrt(1.0+m[1,1]-m[0,0]-m[2,2]); w=(m[0,2]-m[2,0])/s; x=(m[0,1]+m[1,0])/s; y=0.25*s; z=(m[1,2]+m[2,1])/s
    else:
        s=2.0*math.sqrt(1.0+m[2,2]-m[0,0]-m[1,1]); w=(m[1,0]-m[0,1])/s; x=(m[0,2]+m[2,0])/s; y=(m[1,2]+m[2,1])/s; z=0.25*s
    n=math.sqrt(w*w+x*x+y*y+z*z); return np.array([w,x,y,z])/n

def quat_to_mat(q):
    w,x,y,z=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-w*z),2*(x*z+w*y)],[2*(x*y+w*z),1-2*(x*x+z*z),2*(y*z-w*x)],[2*(x*z-w*y),2*(y*z+w*x),1-2*(x*x+y*y)]])

def quat_slerp(q1,q2,t):
    dot=np.dot(q1,q2)
    if dot<0: q2=-q2; dot=-dot
    if dot>0.9995: r=q1+t*(q2-q1); return r/np.linalg.norm(r)
    theta=math.acos(np.clip(dot,-1,1)); st=math.sin(theta)
    return (math.sin((1-t)*theta)/st)*q1+(math.sin(t*theta)/st)*q2

def slerp_mat(a, b, t):
    qa=mat_to_quat(a[:3,:3]); qb=mat_to_quat(b[:3,:3]); qr=quat_slerp(qa,qb,t)
    r=a.copy(); r[:3,:3]=quat_to_mat(qr); return r

def lerp_matrix(a,b,t): return a*(1-t)+b*t

# Load EF reference animations
with open("src/main/resources/assets/combat_arts/models/animations/biped/living/idle.json") as f:
    idle_data=json.load(f)
with open("src/main/resources/assets/combat_arts/models/animations/biped/living/hold_longsword.json") as f:
    hold_data=json.load(f)

def get_matrices(data):
    return {e["name"]: np.array(e["transform"][0]).reshape(4,4) for e in data["animation"]}

im = get_matrices(idle_data)
hm = get_matrices(hold_data)

# Confirmed tweaker values for "reach behind" pose
# Applied to ABSOLUTE matrix via apply_rot(rx, ry, rz, idle_abs)
reach_arm = apply_rot(20, 210, 50, im["Arm_R"])       # whole arm reaches behind
reach_hand = apply_rot(80, -20, 0, im["Hand_R"])       # elbow bends

# Intermediate keyframes to force arm to go UP (not down)
# idle(0°) → 70° up → 140° overhead → 210° behind
arm_up1 = apply_rot(20*0.33, 210*0.33, 50*0.33, im["Arm_R"])    # ~70° up
arm_up2 = apply_rot(20*0.66, 210*0.66, 50*0.66, im["Arm_R"])    # ~140° overhead
hand_up1 = slerp_mat(im["Hand_R"], reach_hand, 0.3)
hand_up2 = slerp_mat(im["Hand_R"], reach_hand, 0.6)

# === DRAW WEAPON ===
# Path: idle → up → overhead → behind(grab) → REVERSE back → hold
times = [0.0, 0.05, 0.10, 0.15, 0.20, 0.30, 0.42, 0.55, 0.80]
ae = []
for jn in im:
    idle_m = im[jn]
    hold_m = hm.get(jn, idle_m)
    if jn == "Arm_R":
        kf = [idle_m, arm_up1, arm_up2, reach_arm, reach_arm,
              arm_up2, slerp_mat(arm_up1, hold_m, 0.5), hold_m, hold_m]
    elif jn == "Hand_R":
        kf = [idle_m, hand_up1, hand_up2, reach_hand, reach_hand,
              hand_up2, slerp_mat(hand_up1, hold_m, 0.5), hold_m, hold_m]
    else:
        kf = [idle_m, idle_m, idle_m, idle_m, idle_m,
              idle_m, lerp_matrix(idle_m, hold_m, 0.5), hold_m, hold_m]
    ae.append({"name": jn, "time": times, "transform": [m.flatten().tolist() for m in kf]})

with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/draw_weapon.json", "w") as f:
    json.dump({"animation": ae}, f)

# === SHEATH WEAPON (hold → up → overhead → behind(place) → REVERSE back → idle) ===
st = [0.0, 0.10, 0.22, 0.30, 0.40, 0.50, 0.62, 0.75, 0.80]
se = []
for jn in im:
    idle_m = im[jn]
    hold_m = hm.get(jn, idle_m)
    if jn == "Arm_R":
        kf = [hold_m, slerp_mat(hold_m, arm_up1, 0.5), arm_up1, arm_up2,
              reach_arm, reach_arm,
              arm_up2, arm_up1, idle_m]
    elif jn == "Hand_R":
        kf = [hold_m, slerp_mat(hold_m, hand_up1, 0.5), hand_up1, hand_up2,
              reach_hand, reach_hand,
              hand_up2, hand_up1, idle_m]
    else:
        kf = [hold_m, hold_m, hold_m, lerp_matrix(hold_m, idle_m, 0.3),
              lerp_matrix(hold_m, idle_m, 0.5), idle_m, idle_m, idle_m, idle_m]
    se.append({"name": jn, "time": st, "transform": [m.flatten().tolist() for m in kf]})

with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/sheath_weapon.json", "w") as f:
    json.dump({"animation": se}, f)

print(f"Draw: {len(ae)} joints, {len(times)} keyframes")
print(f"Sheath: {len(se)} joints, {len(st)} keyframes")
print("Using confirmed values: Arm_R=[20,210,50], Hand_R=[80,-20,0]")
