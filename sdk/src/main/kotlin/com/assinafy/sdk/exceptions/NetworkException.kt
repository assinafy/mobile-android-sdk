package com.assinafy.sdk.exceptions

class NetworkException(message: String, cause: Throwable? = null) : AssinafyException(message, emptyMap(), cause)
