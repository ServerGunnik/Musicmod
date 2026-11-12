package dev.servergunnik.musicmod.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/**
 * The plugin channels shared with the MusicmodCapes server plugin:
 * musicmod:cape (which cape a player wears) and musicmod:cape_img (pictures of
 * custom capes). The payload is raw bytes in both directions.
 */
public final class Net {

    /** Raw bytes on one of our channels. */
    public record Raw(Type<Raw> type, byte[] data) implements CustomPacketPayload {
    }

    private static final CustomPacketPayload.Type<Raw> CAPE = new CustomPacketPayload.Type<>(Fonts.id("cape"));
    private static final CustomPacketPayload.Type<Raw> IMAGE = new CustomPacketPayload.Type<>(Fonts.id("cape_img"));

    private Net() {
    }

    private static StreamCodec<RegistryFriendlyByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> type) {
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new Raw(type, bytes);
                });
    }

    /** Registers both channels; the consumers get every message the server sends on them. */
    public static void init(Consumer<byte[]> onCape, Consumer<byte[]> onImage) {
        for (CustomPacketPayload.Type<Raw> type : java.util.List.of(CAPE, IMAGE)) {
            PayloadTypeRegistry.serverboundPlay().register(type, codec(type));
            PayloadTypeRegistry.clientboundPlay().register(type, codec(type));
        }
        ClientPlayNetworking.registerGlobalReceiver(CAPE, (payload, context) -> onCape.accept(payload.data()));
        ClientPlayNetworking.registerGlobalReceiver(IMAGE, (payload, context) -> onImage.accept(payload.data()));
    }

    /**
     * Sends to the server on the cape channel ({@code image} false) or the picture
     * channel. False when not connected or the server has no MusicmodCapes plugin.
     */
    public static boolean send(boolean image, byte[] data) {
        CustomPacketPayload.Type<Raw> type = image ? IMAGE : CAPE;
        try {
            if (!ClientPlayNetworking.canSend(type)) {
                return false;
            }
            ClientPlayNetworking.send(new Raw(type, data));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
