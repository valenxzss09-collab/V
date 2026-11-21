package com.fofo.racas.mixin;

import com.fofo.racas.Race;
import com.fofo.racas.RaceData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mostra "Raça - Nome" na lista de jogadores (tab). */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void racas$tabName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        Race r = RaceData.get().getRace(self.getUuid());
        if (r != null) {
            cir.setReturnValue(
                Text.literal(r.display).formatted(Formatting.GOLD)
                    .append(Text.literal(" - ").formatted(Formatting.GRAY))
                    .append(self.getName()));
        }
    }
}
