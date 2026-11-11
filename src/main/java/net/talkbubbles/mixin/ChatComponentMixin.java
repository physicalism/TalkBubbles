package net.talkbubbles.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.talkbubbles.TalkBubbles;
import net.talkbubbles.accessor.AbstractClientPlayerEntityAccessor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Shadow
    @Final
    @Mutable
    private Minecraft minecraft;

    @Inject(method = "addMessage", at = @At("HEAD"))
    private void addMessageMixin(Component contents, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag, CallbackInfo info) {
        if (minecraft != null && minecraft.player != null && minecraft.level != null) {
            if (talkBubbles$tryMannequinBubble(contents)) {
                return;
            }
            String detectedSenderName = extractSender(contents);
            if (!detectedSenderName.isEmpty()) {
                UUID senderUUID = this.minecraft.getPlayerSocialManager().getDiscoveredUUID(detectedSenderName);

                List<AbstractClientPlayer> list = minecraft.level.getEntitiesOfClass(AbstractClientPlayer.class, minecraft.player.getBoundingBox().inflate(TalkBubbles.CONFIG.chatRange),
                        EntitySelector.NO_SPECTATORS);

                if (!TalkBubbles.CONFIG.showOwnBubble) {
                    list.remove(minecraft.player);
                }
                for (AbstractClientPlayer abstractClientPlayer : list) {
                    if (abstractClientPlayer.getUUID().equals(senderUUID)) {
                        String stringMessage = contents.getString();
                        stringMessage = stringMessage.replaceFirst("[\\s\\S]*" + detectedSenderName + "([^\\p{L}§]|(§.)?)+\\s+", "");
                        talkBubbles$applyBubble(abstractClientPlayer, stringMessage);
                        break;
                    }
                }
            }
        }

    }

    // Splits a message into bubble lines and attaches it to a player or mannequin.
    @Unique
    private void talkBubbles$applyBubble(Entity entity, String message) {
        String[] string = message.split(" ");
        List<String> stringList = new ArrayList<>();
        String stringCollector = "";

        int width = 0;
        int height = 0;
        for (int u = 0; u < string.length; u++) {
            if (minecraft.font.width(stringCollector) < TalkBubbles.CONFIG.maxChatWidth
                    && minecraft.font.width(stringCollector) + minecraft.font.width(string[u]) <= TalkBubbles.CONFIG.maxChatWidth) {
                stringCollector = stringCollector + " " + string[u];
                if (u == string.length - 1) {
                    stringList.add(stringCollector);
                    height++;
                    if (width < minecraft.font.width(stringCollector)) {
                        width = minecraft.font.width(stringCollector);
                    }
                }
            } else {
                stringList.add(stringCollector);

                height++;
                if (width < minecraft.font.width(stringCollector)) {
                    width = minecraft.font.width(stringCollector);
                }

                stringCollector = string[u];

                if (u == string.length - 1) {
                    stringList.add(stringCollector);
                    height++;
                    if (width < minecraft.font.width(stringCollector)) {
                        width = minecraft.font.width(stringCollector);
                    }
                }
            }
        }

        if (width % 2 != 0) {
            width++;
        }
        ((AbstractClientPlayerEntityAccessor) entity).talkBubbles$setChatText(stringList, entity.tickCount, width, height);
    }

    // Datapack NPCs speak through system chat as "[Name] message". If a mannequin with that
    // custom name is nearby, show the message as its bubble.
    @Unique
    private boolean talkBubbles$tryMannequinBubble(Component contents) {
        Matcher matcher = Pattern.compile("^\\[([^\\]]+)\\] (.+)$", Pattern.DOTALL).matcher(contents.getString());
        if (!matcher.matches()) {
            return false;
        }
        String npcName = matcher.group(1);

        ClientMannequin closest = null;
        double closestDistance = Double.MAX_VALUE;
        List<ClientMannequin> mannequins = minecraft.level.getEntitiesOfClass(ClientMannequin.class, minecraft.player.getBoundingBox().inflate(TalkBubbles.CONFIG.chatRange), mannequin -> true);
        for (ClientMannequin mannequin : mannequins) {
            Component customName = mannequin.getCustomName();
            if (customName == null || !customName.getString().equals(npcName)) {
                continue;
            }
            double distance = mannequin.distanceToSqr(minecraft.player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = mannequin;
            }
        }
        if (closest == null) {
            return false;
        }

        talkBubbles$applyBubble(closest, matcher.group(2));
        return true;
    }

    @Unique
    private String extractSender(Component text) {
        String[] words = text.getString().split("(§.)|[^\\w§]+");
        String[] parts = text.toString().split("key='");

        if (parts.length > 1) {
            String translationKey = parts[1].split("'")[0];
            if (translationKey.contains("commands")) {
                return "";
            } else if (translationKey.contains("advancement")) {
                return "";
            }
        }

        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) {
                continue;
            }
            if (TalkBubbles.CONFIG.maxUUIDWordCheck != 0 && i >= TalkBubbles.CONFIG.maxUUIDWordCheck) {
                return "";
            }

            UUID possibleUUID = this.minecraft.getPlayerSocialManager().getDiscoveredUUID(words[i]);
            if (possibleUUID != Util.NIL_UUID) {
                return words[i];
            }
        }

        return "";
    }
}
