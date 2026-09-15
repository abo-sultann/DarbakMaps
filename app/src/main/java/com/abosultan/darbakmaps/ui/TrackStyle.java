package com.abosultan.darbakmaps.ui;

/** High-contrast off-road track styling inspired by Garmin handheld readability. */
public final class TrackStyle {
    private TrackStyle() {}

    // Garmin-like magenta is intentionally distinct from roads, wadis and saved-place icons.
    public static final int ACTIVE_TRACK = 0xFFFF00A8;
    public static final int ACTIVE_TRACK_OUTLINE = 0xCC4A0032;
    public static final float ACTIVE_TRACK_WIDTH_DP = 5.0f;
    public static final float ACTIVE_TRACK_OUTLINE_WIDTH_DP = 8.0f;
}
