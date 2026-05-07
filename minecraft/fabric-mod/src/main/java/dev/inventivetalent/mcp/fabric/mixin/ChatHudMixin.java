package dev.inventivetalent.mcp.fabric.mixin;

import dev.inventivetalent.mcp.fabric.ChatStorage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @Inject(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void onAddClientSystemMessage(Component message, CallbackInfo ci) {
        if (message != null) {
            ChatStorage.addMessage(message.getString(), null);
        }
    }

    @Inject(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void onAddServerSystemMessage(Component message, CallbackInfo ci) {
        if (message != null) {
            ChatStorage.addMessage(message.getString(), null);
        }
    }

    @Inject(
        method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD")
    )
    private void onAddPlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (message != null) {
            String sender = tag != null ? tag.toString() : null;
            ChatStorage.addMessage(message.getString(), sender);
        }
    }
}
