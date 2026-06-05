# Assinafy SDK — consumer ProGuard / R8 rules.
#
# These rules are packaged into the published AAR (via `consumerProguardFiles` in
# build.gradle.kts) so they are applied during the CONSUMER app's R8 pass. Every model and
# request DTO is created and populated reflectively by Gson, so without these keeps R8 would
# rename/strip fields in minified release builds, producing silently-null fields or
# JsonSyntaxException at runtime.

# Keep Gson generic signatures, inner-class metadata, and the annotations @SerializedName relies on.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*

# Gson internals may reference platform-specific APIs not present at runtime.
-dontwarn sun.misc.**

# Keep DTOs that Gson creates and populates reflectively, including their @SerializedName fields.
-keep class com.assinafy.sdk.models.** { *; }
-keep class com.assinafy.sdk.request.** { *; }
-keepclassmembers,allowobfuscation class com.assinafy.sdk.models.**,com.assinafy.sdk.request.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson type-token / type-adapter machinery used by ResponseHandler.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
