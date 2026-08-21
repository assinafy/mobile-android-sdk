# Gson 2.14 ships its own full-mode R8 rules. Assinafy's reflectively serialized DTO fields use
# @SerializedName, so no broad SDK keep rule is needed and consumers retain normal shrinking.
