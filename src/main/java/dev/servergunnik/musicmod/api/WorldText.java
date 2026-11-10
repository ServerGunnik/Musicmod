package dev.servergunnik.musicmod.api;

import java.util.List;

/** A floating text in the world (backed by a client-only text display entity). */
public interface WorldText {
    /** {@code font}: one of the mod's fonts, or null for Minecraft's font. */
    void setText(List<Span> spans, String font);

    void setPos(double x, double y, double z);

    void setScale(float scale);

    /** true: always turn toward the camera. false: keep the rotation set by {@link #setFacing}. */
    void setBillboard(boolean faceCamera);

    /** Direction the front of the text points to (Minecraft yaw/pitch, degrees). */
    void setFacing(float yaw, float pitch);

    /** 0..255 */
    void setOpacity(int opacity);

    void setBackground(int argb);

    boolean alive();

    void remove();
}
