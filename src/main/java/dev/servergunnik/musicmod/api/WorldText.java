package dev.servergunnik.musicmod.api;

import java.util.List;

/** A floating text in the world (backed by a client-only text display entity). */
public interface WorldText {
    void setText(List<Span> spans);

    void setPos(double x, double y, double z);

    void setScale(float scale);

    /** 0..255 */
    void setOpacity(int opacity);

    void setBackground(int argb);

    boolean alive();

    void remove();
}
