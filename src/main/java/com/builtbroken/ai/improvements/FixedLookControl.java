package com.builtbroken.ai.improvements;

import java.util.Optional;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/** Look controller using the cached FastTrig atan2 lookup table. */
public class FixedLookControl extends LookControl {
    public FixedLookControl(Mob entity) {
        super(entity);
    }

    @Override
    protected Optional<Float> getXRotD() {
        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - (this.mob.getY() + this.mob.getEyeHeight());
        double dz = this.wantedZ - this.mob.getZ();
        double horizontalDistance = Mth.sqrt((float) (dx * dx + dz * dz));
        if (!(Math.abs(dy) > 1.0E-5F) && !(Math.abs(horizontalDistance) > 1.0E-5F)) {
            return Optional.empty();
        }
        return Optional.of((float) (-(tan(dy, horizontalDistance) * 57.2957763671875D)));
    }

    @Override
    protected Optional<Float> getYRotD() {
        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        if (!(Math.abs(dz) > 1.0E-5F) && !(Math.abs(dx) > 1.0E-5F)) {
            return Optional.empty();
        }
        return Optional.of((float) (tan(dz, dx) * 57.2957763671875D) - 90.0F);
    }

    public static float tan(double a, double b) {
        return FastTrig.atan2(a, b);
    }

    public void copyDataIntoSelf(LookControl oldHelper) {
        wantedX = oldHelper.getWantedX();
        wantedY = oldHelper.getWantedY();
        wantedZ = oldHelper.getWantedZ();
        lookAtCooldown = oldHelper.lookAtCooldown;
        xMaxRotAngle = oldHelper.xMaxRotAngle;
        yMaxRotSpeed = oldHelper.yMaxRotSpeed;
    }
}
