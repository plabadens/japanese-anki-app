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

# The Gradle distribution already carries Kotlin; avoid resolving Android
# Gradle Plugin artifacts just to test the platform-independent core. Its
# Kotlin version moves with Gradle, so discover it instead of pinning a
# version that the next upgrade will silently invalidate.
#
# Gradle 9 no longer ships JUnit or Hamcrest in its lib directory, so those
# come from the project's own dependency cache. JUnit is a declared test
# dependency, so the jars are present after any unit-test run.
unset JAVA_TOOL_OPTIONS || true

newest_match() {
  local pattern="$1"
  local matches
  shopt -s nullglob
  matches=( $pattern )
  shopt -u nullglob
  (( ${#matches[@]} )) || return 0
  printf '%s\n' "${matches[@]}" | sort -V | tail -n 1
}

# Find a Gradle module cache holding the project's test dependencies. The
# build and this gate can run with different HOME values, so try the explicit
# override, the usual per-user location, then walk up from the checkout: the
# cache is usually an ancestor directory's .gradle.
module_cache=""
for candidate in "${TEST_CORE_GRADLE_USER_HOME:-}" "${GRADLE_USER_HOME:-}" "$HOME/.gradle"; do
  [[ -n "$candidate" && -d "$candidate/caches/modules-2" ]] || continue
  module_cache="$candidate/caches/modules-2/files-2.1"
  break
done
if [[ -z "$module_cache" ]]; then
  walk="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
  while [[ "$walk" != "/" && -n "$walk" ]]; do
    if [[ -d "$walk/.gradle/caches/modules-2/files-2.1" ]]; then
      module_cache="$walk/.gradle/caches/modules-2/files-2.1"
      break
    fi
    walk="$(dirname "$walk")"
  done
fi

stdlib="$(newest_match "$gradle_lib/kotlin-stdlib-*.jar")"
annotations="$(newest_match "$gradle_lib/annotations-*.jar")"
junit="$(newest_match "${module_cache:-/nonexistent}/junit/junit/*/*/junit-*.jar")"
hamcrest="$(newest_match "${module_cache:-/nonexistent}/org.hamcrest/hamcrest-core/*/*/hamcrest-core-*.jar")"

for required in "$stdlib" "$annotations" "$junit" "$hamcrest"; do
  if [[ -z "$required" || ! -f "$required" ]]; then
    echo "test-core: cannot assemble the test classpath." >&2
    echo "  kotlin-stdlib: ${stdlib:-missing} (from $gradle_lib)" >&2
    echo "  annotations:   ${annotations:-missing} (from $gradle_lib)" >&2
    echo "  junit:         ${junit:-missing} (from $module_cache)" >&2
    echo "  hamcrest:      ${hamcrest:-missing} (from $module_cache)" >&2
    echo "If JUnit or Hamcrest is missing, run one Gradle unit-test task to populate the cache." >&2
    exit 1
  fi
done

runtime_cp="$stdlib:$annotations:$junit:$hamcrest"
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
