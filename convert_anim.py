"""Convert Bedrock draw/sheath animations to EF format using confirmed Rx mapping."""
import json, math
import numpy as np


def mat_to_quat(m):
    trace = m[0,0]+m[1,1]+m[2,2]
    if trace > 0:
        s = 0.5/math.sqrt(trace+1.0)
        w=0.25/s; x=(m[2,1]-m[1,2])*s; y=(m[0,2]-m[2,0])*s; z=(m[1,0]-m[0,1])*s
    elif m[0,0]>m[1,1] and m[0,0]>m[2,2]:
        s=2.0*math.sqrt(1.0+m[0,0]-m[1,1]-m[2,2])
        w=(m[2,1]-m[1,2])/s; x=0.25*s; y=(m[0,1]+m[1,0])/s; z=(m[0,2]+m[2,0])/s
    elif m[1,1]>m[2,2]:
        s=2.0*math.sqrt(1.0+m[1,1]-m[0,0]-m[2,2])
        w=(m[0,2]-m[2,0])/s; x=(m[0,1]+m[1,0])/s; y=0.25*s; z=(m[1,2]+m[2,1])/s
    else:
        s=2.0*math.sqrt(1.0+m[2,2]-m[0,0]-m[1,1])
        w=(m[1,0]-m[0,1])/s; x=(m[0,2]+m[2,0])/s; y=(m[1,2]+m[2,1])/s; z=0.25*s
    n=math.sqrt(w*w+x*x+y*y+z*z); return np.array([w,x,y,z])/n

def quat_to_mat(q):
    w,x,y,z=q
    return np.array([
        [1-2*(y*y+z*z),2*(x*y-w*z),2*(x*z+w*y)],
        [2*(x*y+w*z),1-2*(x*x+z*z),2*(y*z-w*x)],
        [2*(x*z-w*y),2*(y*z+w*x),1-2*(x*x+y*y)]])

def quat_slerp(q1,q2,t):
    dot=np.dot(q1,q2)
    if dot<0: q2=-q2; dot=-dot
    if dot>0.9995: r=q1+t*(q2-q1); return r/np.linalg.norm(r)
    theta=math.acos(np.clip(dot,-1,1)); st=math.sin(theta)
    return (math.sin((1-t)*theta)/st)*q1+(math.sin(t*theta)/st)*q2

def slerp_mat(a,b,t):
    qa=mat_to_quat(a[:3,:3]); qb=mat_to_quat(b[:3,:3]); qr=quat_slerp(qa,qb,t)
    r=a.copy(); r[:3,:3]=quat_to_mat(qr); return r

def rot_x(d):
    r=math.radians(d)
    return np.array([[1,0,0],[0,math.cos(r),-math.sin(r)],[0,math.sin(r),math.cos(r)]])
def rot_y(d):
    r=math.radians(d)
    return np.array([[math.cos(r),0,math.sin(r)],[0,1,0],[-math.sin(r),0,math.cos(r)]])
def rot_z(d):
    r=math.radians(d)
    return np.array([[math.cos(r),-math.sin(r),0],[math.sin(r),math.cos(r),0],[0,0,1]])

def apply_rot(rot3, m4):
    r=m4.copy(); r[:3,:3]=rot3@m4[:3,:3]; return r

def bedrock_to_ef_abs(idle_abs, bx, by, bz, joint_name):
    """Convert Bedrock euler (degrees) to EF absolute matrix.
    Per-joint axis mapping based on each joint's local frame orientation.
    Confirmed by axis tests:
    - Shoulder_R: Rx(-bx) raises arm
    - Arm_R: Ry(+bx) bends elbow forward (Bedrock xRot maps to Ry directly)
    """
    if joint_name in ("Arm_R", "Arm_L"):
        # Forearm: Bedrock xRot (elbow bend) → EF Ry
        rot = rot_y(bx)
    elif joint_name in ("Hand_R", "Hand_L"):
        # Hand: similar to forearm
        rot = rot_y(bx)
    else:
        # Shoulder, body, head, legs: standard mapping
        rot = rot_z(-bz) @ rot_y(-by) @ rot_x(-bx)
    return apply_rot(rot, idle_abs)

def lerp_matrix(a,b,t):
    return a*(1-t)+b*t


with open("src/main/resources/assets/combat_arts/animations/combat/anim_draw_weapon.animation.json") as f:
    bedrock = json.load(f)
draw_anim = bedrock["animations"]["animation.player.draw_weapon"]

with open("src/main/resources/assets/combat_arts/animations/combat/anim_sheath_weapon.animation.json") as f:
    sheath_bedrock = json.load(f)
sheath_anim = sheath_bedrock["animations"]["animation.player.sheath_weapon"]

with open("src/main/resources/assets/combat_arts/models/animations/biped/living/idle.json") as f:
    idle_data = json.load(f)
with open("src/main/resources/assets/combat_arts/models/animations/biped/living/hold_longsword.json") as f:
    hold_data = json.load(f)


def get_matrices(data):
    return {e["name"]: np.array(e["transform"][0]).reshape(4,4) for e in data["animation"]}

im = get_matrices(idle_data)
hm = get_matrices(hold_data)

BONE_MAP = {
    "waist": "Torso", "chest": "Chest", "head": "Head",
    "rightUpperArm": "Shoulder_R", "rightLowerArm": "Arm_R", "rightHand": "Hand_R",
    "leftUpperArm": "Shoulder_L", "leftLowerArm": "Arm_L", "leftHand": "Hand_L",
    "rightUpperLeg": "Thigh_R", "rightLowerLeg": "Leg_R",
    "leftUpperLeg": "Thigh_L", "leftLowerLeg": "Leg_L",
}


def parse_bedrock_bone(bone_data):
    rot = bone_data.get("rotation", {})
    kfs = []
    for time_str, vals in rot.items():
        t = float(time_str)
        if isinstance(vals, list):
            kfs.append((t, vals[0], vals[1], vals[2]))
        else:
            kfs.append((t, 0, 0, 0))
    kfs.sort(key=lambda x: x[0])
    return kfs


def interp_bedrock(kfs, t):
    if t <= kfs[0][0]:
        return kfs[0][1], kfs[0][2], kfs[0][3]
    if t >= kfs[-1][0]:
        return kfs[-1][1], kfs[-1][2], kfs[-1][3]
    for i in range(len(kfs)-1):
        if kfs[i][0] <= t <= kfs[i+1][0]:
            alpha = (t - kfs[i][0]) / (kfs[i+1][0] - kfs[i][0])
            bx = kfs[i][1] + alpha * (kfs[i+1][1] - kfs[i][1])
            by = kfs[i][2] + alpha * (kfs[i+1][2] - kfs[i][2])
            bz = kfs[i][3] + alpha * (kfs[i+1][3] - kfs[i][3])
            return bx, by, bz
    return 0, 0, 0


def convert_animation(bedrock_anim, idle_mats, hold_mats, idle_to_hold=True):
    bones = bedrock_anim["bones"]
    bone_kfs = {}
    all_times = set()

    for bone_name, bone_data in bones.items():
        ef_joint = BONE_MAP.get(bone_name)
        if ef_joint and ef_joint in idle_mats:
            kfs = parse_bedrock_bone(bone_data)
            if not kfs:
                continue
            bone_kfs[ef_joint] = kfs
            for t, _, _, _ in kfs:
                all_times.add(t)

    if not all_times:
        all_times.add(0.0)
    all_times = sorted(all_times)

    entries = []
    for jn in idle_mats:
        idle_m = idle_mats[jn]
        hold_m = hold_mats.get(jn, idle_m)
        transforms = []
        if jn in bone_kfs:
            kfs = bone_kfs[jn]
            for t in all_times:
                bx, by, bz = interp_bedrock(kfs, t)
                abs_mat = bedrock_to_ef_abs(idle_m, bx, by, bz, jn)
                transforms.append(abs_mat.flatten().tolist())
        else:
            for t in all_times:
                transforms.append(idle_m.flatten().tolist())
        entries.append({"name": jn, "time": list(all_times), "transform": transforms})
    return entries


draw_entries = convert_animation(draw_anim, im, hm)
with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/draw_weapon.json", "w") as f:
    json.dump({"animation": draw_entries}, f)

sheath_entries = convert_animation(sheath_anim, im, hm)
with open("src/main/resources/assets/combat_arts/models/animations/biped/combat/sheath_weapon.json", "w") as f:
    json.dump({"animation": sheath_entries}, f)

print(f"Draw: {len(draw_entries)} joints")
print(f"Sheath: {len(sheath_entries)} joints")
print("Mapping: EF abs = Rz(-bz) @ Ry(-by) @ Rx(-bx) * idle_abs")
