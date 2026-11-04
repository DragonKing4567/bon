package com.bondrewd.client;

import com.bondrewd.entity.BondrewdEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/** Player-shaped model that strikes a different pose for each of Bondrewd's moves. */
public class BondrewdModel extends PlayerEntityModel<BondrewdEntity> {
    public BondrewdModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(BondrewdEntity e, float limbAngle, float limbDistance, float anim, float headYaw, float headPitch) {
        super.setAngles(e, limbAngle, limbDistance, anim, headYaw, headPitch);
        float flutter = MathHelper.sin(anim * 0.6f) * 0.08f;

        switch (e.getMove()) {
            case GRAB -> {                       // one arm thrust forward, claw-like
                rightArm.pitch = -1.57f + flutter; rightArm.yaw = -0.1f;
                leftArm.pitch = 0.4f;
            }
            case SLAM -> {                       // both hands raised to the sky
                rightArm.pitch = -2.9f + flutter; leftArm.pitch = -2.9f - flutter;
                rightArm.roll = 0.25f; leftArm.roll = -0.25f;
            }
            case PULSE -> {                      // arms flung wide: prayer
                rightArm.pitch = -0.2f; leftArm.pitch = -0.2f;
                rightArm.roll = 1.6f + flutter; leftArm.roll = -1.6f - flutter;
            }
            case COMPASS -> {                    // both arms aimed down the beam
                rightArm.pitch = -1.57f; leftArm.pitch = -1.57f;
                rightArm.yaw = -0.15f; leftArm.yaw = 0.15f;
                head.pitch = headPitch * ((float) Math.PI / 180f);
            }
            case STEP -> {                       // lean back, arms trailing
                rightArm.pitch = 0.9f; leftArm.pitch = 0.9f;
                body.pitch = 0.35f;
            }
            default -> {}
        }
        leftSleeve.copyTransform(leftArm);
        rightSleeve.copyTransform(rightArm);
        jacket.copyTransform(body);
        hat.copyTransform(head);
    }
}
