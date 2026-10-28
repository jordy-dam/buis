# Libraries ship their own consumer rules (Room, Hilt, OkHttp, Tink, Media3, Coil, kotlinx.serialization).
# Only what they can't know about goes here.

# Navigation routes are @Serializable and looked up by their serializer; the enum inside PlayerRoute
# is reached by name only (lint: MissingKeepAnnotation).
-keep enum nl.jordy.iptv.ui.PlayerRoute$Kind { *; }

# The FFmpeg audio decoder is created by reflection by Media3's renderer factory and calls into JNI.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Room stores ContentType by name.
-keep enum nl.jordy.iptv.data.xtream.ContentType { *; }
