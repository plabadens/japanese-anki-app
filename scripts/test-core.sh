#!/usr/bin/env bash
set -euo pipefail

if ! command -v gradle >/dev/null; then
  echo "Run this from the Nix development shell (nix develop)." >&2
  exit 1
fi

gradle_path="$(readlink -f "$(command -v gradle)")"
gradle_root="$(dirname "$(dirname "$gradle_path")")"
gradle_lib="$gradle_root/libexec/gradle/lib"
mkdir -p build
classes="$(mktemp -d build/core-tests.XXXXXX)"
trap 'rm -rf "$classes"' EXIT

# The Gradle distribution already carries Kotlin and JUnit; avoid resolving
# Android Gradle Plugin artifacts just to test the platform-independent core.
unset JAVA_TOOL_OPTIONS || true
runtime_cp="$gradle_lib/kotlin-stdlib-2.0.21.jar:$gradle_lib/annotations-24.0.1.jar:$gradle_lib/junit-4.13.2.jar:$gradle_lib/hamcrest-core-1.3.jar"
java -cp "$gradle_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$runtime_cp" -d "$classes" \
  app/src/main/java/dev/pierre/japaneseanki/TargetSpan.kt \
  app/src/main/java/dev/pierre/japaneseanki/AnkiFieldContract.kt \
  app/src/main/java/dev/pierre/japaneseanki/AnkiFieldSerializer.kt \
  app/src/main/java/dev/pierre/japaneseanki/ManualNoteDraft.kt \
  app/src/main/java/dev/pierre/japaneseanki/PitchAccent.kt \
  app/src/main/java/dev/pierre/japaneseanki/AjtPitchHtml.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/MiniJson.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/SentenceSpeech.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/GoogleSpeech.kt \
  app/src/main/java/dev/pierre/japaneseanki/JapaneseMorphology.kt \
  app/src/main/java/dev/pierre/japaneseanki/OcrCandidateSelection.kt \
  app/src/test/java/dev/pierre/japaneseanki/TargetSpanTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/AnkiFieldContractTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/AnkiFieldSerializerTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/ManualNoteDraftTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/PitchAccentTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/speech/SpeechTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/JapaneseMorphologyTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/OcrCandidateSelectionTest.kt

java -cp "$classes:$runtime_cp" org.junit.runner.JUnitCore \
  dev.pierre.japaneseanki.TargetSpanTest \
  dev.pierre.japaneseanki.AnkiFieldContractTest \
  dev.pierre.japaneseanki.AnkiFieldSerializerTest \
  dev.pierre.japaneseanki.ManualNoteDraftTest \
  dev.pierre.japaneseanki.PitchAccentTest \
  dev.pierre.japaneseanki.speech.SpeechHintBuilderTest \
  dev.pierre.japaneseanki.speech.GoogleSpeechProviderTest \
  dev.pierre.japaneseanki.JapaneseMorphologyTest \
  dev.pierre.japaneseanki.OcrCandidateSelectionTest
