package dev.servergunnik.musicmod.mixin;

import dev.servergunnik.musicmod.compat.Capes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swaps the cape in the skin the game is about to render for the local player. */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerCapeMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void musicmod$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            PlayerSkin withCape = Capes.apply(this, cir.getReturnValue());
            if (withCape != null) {
                cir.setReturnValue(withCape);
            }
        } catch (Throwable ignored) {
            // A cosmetic must never break rendering.
        }
    }
}
