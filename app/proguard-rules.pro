# ─────────────────────────────────────────────────────────────────────────────
# ExpenseX ProGuard / R8 rules (optimized — see docs/CHANGES.md, Skill #4 r8-analyzer)
#
# Guideline: libraries ship their own consumer ProGuard rules (Firebase BOM,
# Play Services, OkHttp, Retrofit, Room, Credential Manager). We only keep what
# R8 cannot infer from the app's own bytecode: reflection targets and
# serialization models.
# ─────────────────────────────────────────────────────────────────────────────

# --- App data models (Moshi codegen + Firestore POJO reflection) ---
# Firestore deserializes these via reflection; Moshi adapters are KSP-generated.
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

# --- Room ---
# Room's generated implementation classes are referenced reflectively by name.
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(...); }
# (Room ships consumer rules — no -dontwarn needed here anymore.)

# --- Retrofit ---
# Only annotated service interface methods need keeping; Retrofit ships the rest.
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Kotlinx Serialization (Navigation 3 routes are @Serializable) ---
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.example.ui.navigation.** {
    *** INSTANCE;
}
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions

# ── REMOVED (previously over-broad / redundant) ──────────────────────────────
#  -keep class com.google.firebase.** { *; }        → Firebase BOM consumer rules
#  -keep class com.google.android.gms.** { *; }     → Play Services consumer rules
#  -dontwarn com.google.firebase.**, com.google.android.gms.**,
#    okhttp3.**, retrofit2.**, androidx.room.**     → covered by library rules
#  duplicate SourceFile/LineNumberTable blocks      → consolidated below
# ─────────────────────────────────────────────────────────────────────────────

# Preserve line numbers once, for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
