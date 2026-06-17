# Enums — ProGuard strips values()/valueOf() as "unreachable", but Enum.valueOf() needs them at runtime
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.serialization — keep serializer infrastructure and all @Serializable classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their generated $serializer companions
-keep,includedescriptorclasses class automatl.juras.** implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class automatl.juras.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class automatl.juras.**$$serializer { *; }

# kaml (YAML) — uses reflection to find serializers
-keep class com.charleskorn.kaml.** { *; }
-dontwarn com.charleskorn.kaml.**
