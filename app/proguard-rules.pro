# ── Atlas Peak — reglas R8/ProGuard (release). SPEC.md §9 Fase 13 ──────────────

# Kotlinx Serialization: conservar serializadores generados
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
# Mantener clases @Serializable y sus companions
-if @kotlinx.serialization.Serializable class **
-keep,allowobfuscation,allowshrinking class <1> { *; }

# Room: las entities/DAOs se generan por KSP; conservar lo necesario
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.zetetic.** { *; }
-keep interface net.zetetic.** { *; }

# Retrofit + OkHttp (solo Drive REST v3)
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface * extends retrofit2.Call
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Hilt: generado; normalmente no requiere reglas extra, pero conservar componentes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Health Connect / Credentials / Maps: confían en sus propios consumer rules.
# Si aparece un warning de stripping, documentarlo aquí y en BUGS.md.

# Conservar nombres de modelos de dominio si se serializan a JSON para backup/export
-keep class com.atlaspeak.domain.model.** { *; }
