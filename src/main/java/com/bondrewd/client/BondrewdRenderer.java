package com.bondrewd.client;

import com.bondrewd.BondrewdMod;
import com.bondrewd.entity.BondrewdEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class BondrewdRenderer extends BipedEntityRenderer<BondrewdEntity, BondrewdModel> {
    private static final Identifier TEXTURE = new Identifier(BondrewdMod.MOD_ID, "textures/entity/bondrewd.png");

    public BondrewdRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BondrewdModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(BondrewdEntity entity) { return TEXTURE; }

    @Override
    protected void scale(BondrewdEntity entity, MatrixStack matrices, float amount) {
        float s = 1.1f + (entity.getPhase() - 1) * 0.05f;   // grows slightly each phase
        matrices.scale(s, s, s);
    }
}
