package dev.servergunnik.musicmod.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/**
 * The plugin channel musicmod:cape, shared with the MusicmodCapes server plugin.
 * The payload is raw bytes in both directions.
 */
public final class Net {

    public record CapePayload(byte[] data) implements CustomPacketPayload {
        public static final Type<CapePayload> TYPE = new Type<>(Fonts.id("cape"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CapePayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new CapePayload(bytes);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Net() {
    }

    /** Registers the channel; {@code onReceive} gets every message from the server. */
    public static void init(Consumer<byte[]> onReceive) {
        PayloadTypeRegistry.serverboundPlay().register(CapePayload.TYPE, CapePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CapePayload.TYPE, CapePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CapePayload.TYPE, (payload, context) -> onReceive.accept(payload.data()));
    }

    /** Sends to the server. False when not connected or the server has no MusicmodCapes plugin. */
    public static boolean send(byte[] data) {
        try {
            if (!ClientPlayNetworking.canSend(CapePayload.TYPE)) {
                return false;
            }
            ClientPlayNetworking.send(new CapePayload(data));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
