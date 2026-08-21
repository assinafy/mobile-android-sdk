FROM eclipse-temurin:25.0.3_9-jdk-noble@sha256:e94f1dc880339ab3884b69176b79c8dc4124b722e059c7ff7f0bf53b603a46f8

ARG ANDROID_COMMAND_LINE_TOOLS_VERSION=16111833
ARG ANDROID_COMMAND_LINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_OPTS=-Dorg.gradle.vfs.watch=false \
    PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        openjdk-17-jdk-headless \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl --fail --location --silent --show-error \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_LINE_TOOLS_VERSION}_latest.zip" \
        --output /tmp/android-command-line-tools.zip \
    && echo "${ANDROID_COMMAND_LINE_TOOLS_SHA256}  /tmp/android-command-line-tools.zip" | sha256sum --check --strict \
    && unzip -q /tmp/android-command-line-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/android-command-line-tools.zip

RUN android sdk install \
        "build-tools/36.0.0" \
        "platform-tools" \
        "platforms/android-36"

WORKDIR /app
COPY . .
RUN chmod +x gradlew

CMD ["./gradlew", ":sdk:build", "--no-daemon"]
