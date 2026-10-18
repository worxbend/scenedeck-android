# Libraries ship their own consumer rules (Hilt, Room, kotlinx.serialization, Ktor, Glance).
# Keep app-specific rules here and document why each one is needed.

# Readable crash reports from user-submitted stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
