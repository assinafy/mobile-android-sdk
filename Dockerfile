ARG ANDROID_BUILD_PLATFORM=linux/amd64
FROM --platform=$ANDROID_BUILD_PLATFORM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV GRADLE_OPTS=-Dorg.gradle.vfs.watch=false
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME} && \
    cd ${ANDROID_HOME} && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mkdir -p cmdline-tools/latest && \
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true && \
    rm cmdline-tools.zip

# Find the correct JAVA_HOME and install Android SDK in the same layer
RUN export JAVA_HOME=$(ls -d /usr/lib/jvm/java-17-* 2>/dev/null | head -1) && \
    echo "Using JAVA_HOME=$JAVA_HOME" && \
    yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true && \
    /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

WORKDIR /app
COPY . .
RUN chmod +x gradlew

CMD ["./gradlew", ":sdk:build", "--no-daemon"]
