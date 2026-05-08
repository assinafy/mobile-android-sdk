# Assinafy SDK ProGuard Rules

# Keep Gson generic signatures and runtime annotations used by @SerializedName.
-keepattributes Signature,RuntimeVisibleAnnotations

# Gson internals may reference platform-specific APIs.
-dontwarn sun.misc.**

# Keep DTOs that Gson creates and populates reflectively.
-keep class com.assinafy.sdk.models.** { *; }
-keep class com.assinafy.sdk.request.** { *; }
