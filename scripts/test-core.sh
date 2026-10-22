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
  app/src/main/java/dev/pierre/japaneseanki/SentenceReadings.kt \
  app/src/main/java/dev/pierre/japaneseanki/VocabularyReadings.kt \
  app/src/main/java/dev/pierre/japaneseanki/DraftAudioIdentity.kt \
  app/src/main/java/dev/pierre/japaneseanki/AudioProvider.kt \
  app/src/main/java/dev/pierre/japaneseanki/AudioGeneration.kt \
  app/src/main/java/dev/pierre/japaneseanki/DraftAudioStore.kt \
  app/src/main/java/dev/pierre/japaneseanki/DraftAudioController.kt \
  app/src/main/java/dev/pierre/japaneseanki/ReviewAudioGenerationCoordinator.kt \
  app/src/main/java/dev/pierre/japaneseanki/Nhk2016PronunciationSource.kt \
  app/src/main/java/dev/pierre/japaneseanki/PitchAccent.kt \
  app/src/main/java/dev/pierre/japaneseanki/AjtPitchHtml.kt \
  app/src/main/java/dev/pierre/japaneseanki/GoogleApiError.kt \
  app/src/main/java/dev/pierre/japaneseanki/TranslationService.kt \
  app/src/main/java/dev/pierre/japaneseanki/GoogleHttp.kt \
  app/src/main/java/dev/pierre/japaneseanki/HtmlEntityDecoder.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/MiniJson.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/SentenceSpeech.kt \
  app/src/main/java/dev/pierre/japaneseanki/speech/GoogleSpeech.kt \
  app/src/main/java/dev/pierre/japaneseanki/JapaneseMorphology.kt \
  app/src/main/java/dev/pierre/japaneseanki/OcrCandidateSelection.kt \
  app/src/test/java/dev/pierre/japaneseanki/TargetSpanTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/AnkiFieldContractTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/AnkiFieldSerializerTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/ManualNoteDraftTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/SentenceReadingsTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/VocabularyReadingsTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/DraftAudioIdentityTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/Nhk2016PronunciationSourceTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/IndexHeadwordsTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/AudioFixtures.kt \
  app/src/test/java/dev/pierre/japaneseanki/AudioReliabilityTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/DraftAudioStoreTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/DraftAudioControllerTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/PitchAccentTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/speech/SpeechTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/speech/ReadingHintCorrectionTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/JapaneseMorphologyTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/OcrCandidateSelectionTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/TranslationServiceTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/GoogleHttpTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/CopyCatalogueTest.kt

java -cp "$classes:$runtime_cp" org.junit.runner.JUnitCore \
  dev.pierre.japaneseanki.TargetSpanTest \
  dev.pierre.japaneseanki.AnkiFieldContractTest \
  dev.pierre.japaneseanki.AnkiFieldSerializerTest \
  dev.pierre.japaneseanki.ManualNoteDraftTest \
  dev.pierre.japaneseanki.SentenceReadingsTest \
  dev.pierre.japaneseanki.VocabularyReadingsTest \
  dev.pierre.japaneseanki.DraftAudioIdentityTest \
  dev.pierre.japaneseanki.Nhk2016PronunciationSourceTest \
  dev.pierre.japaneseanki.IndexHeadwordsTest \
  dev.pierre.japaneseanki.AudioReliabilityTest \
  dev.pierre.japaneseanki.DraftAudioStoreTest \
  dev.pierre.japaneseanki.DraftAudioControllerTest \
  dev.pierre.japaneseanki.PitchAccentTest \
  dev.pierre.japaneseanki.speech.ReadingHintCorrectionTest \
  dev.pierre.japaneseanki.speech.SpeechHintBuilderTest \
  dev.pierre.japaneseanki.speech.GoogleSpeechProviderTest \
  dev.pierre.japaneseanki.JapaneseMorphologyTest \
  dev.pierre.japaneseanki.OcrCandidateSelectionTest \
  dev.pierre.japaneseanki.TranslationServiceTest \
  dev.pierre.japaneseanki.GoogleHttpTest \
  dev.pierre.japaneseanki.CopyCatalogueTest
