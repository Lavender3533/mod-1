// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TransformSheet {
	public static final TransformSheet EMPTY_SHEET = new TransformSheet(List.of(new Keyframe(0.0F, JointTransform.empty()), new Keyframe(Float.MAX_VALUE, JointTransform.empty())));
	public static final Function<Vec3, TransformSheet> EMPTY_SHEET_PROVIDER = translation -> {
		return new TransformSheet(List.of(new Keyframe(0.0F, JointTransform.translation(new Vec3f(translation))), new Keyframe(Float.MAX_VALUE, JointTransform.empty())));
	};

	private Keyframe[] keyframes;

	public TransformSheet() {
		this(new Keyframe[0]);
	}

	public TransformSheet(int size) {
		this(new Keyframe[size]);
	}

	public TransformSheet(List<Keyframe> keyframeList) {
		this(keyframeList.toArray(new Keyframe[0]));
	}

	public TransformSheet(Keyframe[] keyframes) {
		this.keyframes = keyframes;
	}

	public JointTransform getStartTransform() {
		return this.keyframes[0].transform();
	}

	public Keyframe[] getKeyframes() {
		return this.keyframes;
	}

	public TransformSheet copyAll() {
		return this.copy(0, this.keyframes.length);
	}

	public TransformSheet copy(int start, int end) {
		int len = end - start;
		Keyframe[] newKeyframes = new Keyframe[len];

		for (int i = 0; i < len; i++) {
			Keyframe kf = this.keyframes[i + start];
			newKeyframes[i] = new Keyframe(kf);
		}

		return new TransformSheet(newKeyframes);
	}

	public TransformSheet readFrom(TransformSheet opponent) {
		if (opponent.keyframes.length != this.keyframes.length) {
			this.keyframes = new Keyframe[opponent.keyframes.length];

			for (int i = 0; i < this.keyframes.length; i++) {
				this.keyframes[i] = Keyframe.empty();
			}
		}

		for (int i = 0; i < this.keyframes.length; i++) {
			this.keyframes[i].copyFrom(opponent.keyframes[i]);
		}

		return this;
	}

	public TransformSheet createInterpolated(float[] timestamp) {
		TransformSheet interpolationCreated = new TransformSheet(timestamp.length);

		for (int i = 0; i < timestamp.length; i++) {
			interpolationCreated.keyframes[i] = new Keyframe(timestamp[i], this.getInterpolatedTransform(timestamp[i]));
		}

		return interpolationCreated;
	}

	public void forEach(BiConsumer<Integer, Keyframe> task) {
		this.forEach(task, 0, this.keyframes.length);
	}

	public void forEach(BiConsumer<Integer, Keyframe> task, int start, int end) {
		end = Math.min(end, this.keyframes.length);

		for (int i = start; i < end; i++) {
			task.accept(i, this.keyframes[i]);
		}
	}

	public Vec3f getInterpolatedTranslation(float currentTime) {
		InterpolationInfo interpolInfo = this.getInterpolationInfo(currentTime);

		if (interpolInfo == InterpolationInfo.INVALID) {
			return new Vec3f();
		}

		Vec3f vec3f = MathUtils.lerpVector(this.keyframes[interpolInfo.prev].transform().translation(), this.keyframes[interpolInfo.next].transform().translation(), interpolInfo.delta);
		return vec3f;
	}

	public Quaternionf getInterpolatedRotation(float currentTime) {
		InterpolationInfo interpolInfo = this.getInterpolationInfo(currentTime);

		if (interpolInfo == InterpolationInfo.INVALID) {
			return new Quaternionf();
		}

		Quaternionf quat = MathUtils.lerpQuaternion(this.keyframes[interpolInfo.prev].transform().rotation(), this.keyframes[interpolInfo.next].transform().rotation(), interpolInfo.delta);
		return quat;
	}

	public JointTransform getInterpolatedTransform(float currentTime) {
		return this.getInterpolatedTransform(this.getInterpolationInfo(currentTime));
	}

	public JointTransform getInterpolatedTransform(InterpolationInfo interpolationInfo) {
		if (interpolationInfo == InterpolationInfo.INVALID) {
			return JointTransform.empty();
		}

		JointTransform trasnform = JointTransform.interpolate(this.keyframes[interpolationInfo.prev].transform(), this.keyframes[interpolationInfo.next].transform(), interpolationInfo.delta);
		return trasnform;
	}

	public TransformSheet extend(TransformSheet target) {
		int newKeyLength = this.keyframes.length + target.keyframes.length;
		Keyframe[] newKeyfrmaes = new Keyframe[newKeyLength];

		for (int i = 0; i < this.keyframes.length; i++) {
			newKeyfrmaes[i] = this.keyframes[i];
		}

		for (int i = this.keyframes.length; i < newKeyLength; i++) {
			newKeyfrmaes[i] = new Keyframe(target.keyframes[i - this.keyframes.length]);
		}

		this.keyframes = newKeyfrmaes;

		return this;
	}

	public TransformSheet getFirstFrame() {
		TransformSheet part = this.copy(0, 2);
		Keyframe[] keyframes = part.getKeyframes();
		keyframes[1].transform().copyFrom(keyframes[0].transform());

		return part;
	}

	public void correctAnimationByNewPosition(Vec3f startpos, Vec3f startToEnd, Vec3f modifiedStart, Vec3f modifiedStartToEnd) {
		Keyframe[] keyframes = this.getKeyframes();
		Keyframe startKeyframe = keyframes[0];
		Keyframe endKeyframe = keyframes[keyframes.length - 1];
		float pitchDeg = (float) Math.toDegrees(Mth.atan2(modifiedStartToEnd.y - startToEnd.y, modifiedStartToEnd.length()));
		float yawDeg = (float) MathUtils.getAngleBetween(modifiedStartToEnd.copy().multiply(1.0F, 0.0F, 1.0F), startToEnd.copy().multiply(1.0F, 0.0F, 1.0F));

		for (Keyframe kf : keyframes) {
			float lerp = (kf.time() - startKeyframe.time()) / (endKeyframe.time() - startKeyframe.time());
			Vec3f line = MathUtils.lerpVector(new Vec3f(0F, 0F, 0F), startToEnd, lerp);
			Vec3f modifiedLine = MathUtils.lerpVector(new Vec3f(0F, 0F, 0F), modifiedStartToEnd, lerp);
			Vec3f keyTransform = kf.transform().translation();
			Vec3f startToKeyTransform = keyTransform.copy().sub(startpos).multiply(-1.0F, 1.0F, -1.0F);
			Vec3f animOnLine = startToKeyTransform.copy().sub(line);
			OpenMatrix4f rotator = OpenMatrix4f.createRotatorDeg(pitchDeg, Vec3f.X_AXIS).mulFront(OpenMatrix4f.createRotatorDeg(yawDeg, Vec3f.Y_AXIS));
			Vec3f toNewKeyTransform = modifiedLine.add(OpenMatrix4f.transform3v(rotator, animOnLine, null));
			keyTransform.set(modifiedStart.copy().add((toNewKeyTransform)));
		}
	}

	public InterpolationInfo getInterpolationInfo(float currentTime) {
		if (this.keyframes.length == 0) {
			return InterpolationInfo.INVALID;
		}

		if (currentTime < 0.0F) {
			currentTime = this.keyframes[this.keyframes.length - 1].time() + currentTime;
		}

		// Binary search
		int begin = 0, end = this.keyframes.length - 1;

		while (end - begin > 1) {
			int i = begin + (end - begin) / 2;

			if (this.keyframes[i].time() <= currentTime && this.keyframes[i+1].time() > currentTime) {
				begin = i;
				end = i+1;
				break;
			} else {
				if (this.keyframes[i].time() > currentTime) {
					end = i;
				} else if (this.keyframes[i+1].time() <= currentTime) {
					begin = i;
				}
			}
		}

		float progression = Mth.clamp((currentTime - this.keyframes[begin].time()) / (this.keyframes[end].time() - this.keyframes[begin].time()), 0.0F, 1.0F);
		return new InterpolationInfo(begin, end, Float.isNaN(progression) ? 1.0F : progression);
	}

	public float maxFrameTime() {
		float maxFrameTime = -1.0F;

		for (Keyframe kf : this.keyframes) {
			if (kf.time() > maxFrameTime) {
				maxFrameTime = kf.time();
			}
		}

		return maxFrameTime;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		int idx = 0;

		for (Keyframe kf : this.keyframes) {
			sb.append(kf);

			if (++idx < this.keyframes.length) {
				sb.append("\n");
			}
		}

		return sb.toString();
	}

	public static record InterpolationInfo(int prev, int next, float delta) {
		public static final InterpolationInfo INVALID = new InterpolationInfo(-1, -1, -1.0F);
	}
}
