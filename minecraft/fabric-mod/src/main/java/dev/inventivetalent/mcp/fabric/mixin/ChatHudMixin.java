package dev.inventivetalent.mcp.fabric.mixin;

import dev.inventivetalent.mcp.fabric.ChatStorage;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        if (message != null) {
            // Extract a best-effort sender name from the formatted text.
            // For most server chat this is included in the message text itself.
            ChatStorage.addMessage(message.getString(), null);
        }
    }
}
