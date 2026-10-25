# Test source-inspection audit

This directory must test application behavior, not inspect Kotlin production source text. The following checks were removed because they asserted source layout or coding style instead of exercising the app.

| Removed test | Requirement it purported to cover | Behavior coverage retained |
|---|---|---|
| `CopyCatalogueTest.uiCopyIsResourceBackedAndLegacyResourcesAreUsed` | UI copy should render from resources; referenced resource IDs should exist; legacy resources should be used and obsolete card resources absent. | Android resource references are compile-time checked. User-visible labels and actions are exercised by `SetupScreenBehaviorTest.firstRunSetupRendersAndCompletesInsideItsFullScreenFlowDialog`, `SetupScreenBehaviorTest.voiceRowOpensTheListAndReportsTheChosenVoice`, and `CardPresentationTest.derivesHintStatusAndPrimaryActionForEachCardState`. The static rule that every UI string must be implemented a particular way is intentionally not retained as a behavior test. |
| `SendStateTest.eachRecoveryReasonIsAssignedAtItsOutcomeScenario` | A recovery outcome should prevent unsafe completion and expose a recovery status. | `SendCoordinatorTest.duplicateReturnsDuplicateWithoutInserting` and `SendCoordinatorTest.mediaFailureCarriesOperationAndStageAndStopsBeforeInsert` exercise the outcomes and durable phase changes; `SubmissionRecoveryDecisionTest.mismatchedDestinationNeedsReviewAndOnlyVerifiedDestinationCompletes` and `SubmissionRecoveryDecisionTest.missingOrMalformedRecoveryEvidenceNeverCompletes` exercise recovery decisions; `CardPresentationTest.derivesHintStatusAndPrimaryActionForEachCardState` checks the resulting visible card status and action behavior. The removed test only searched nearby source substrings; it did not execute these paths. |
| `SendStateTest.recoveryAffordancesDoNotBranchOnDisplayCopy` | Recovery actions should be driven by state, not by parsing display text. | `CardPresentationTest.derivesHintStatusAndPrimaryActionForEachCardState` exercises a recovery action label and verifies the user-visible action and enabled state, as well as the locked recovery state. |

Legitimate file-content tests are retained because they read application data files, not production source:

- `AssetPolicyAndroidTest.bundledNhkIndexResolvesHeadwordWithoutNetwork` reads the bundled pronunciation index asset and verifies that real headwords resolve to recording filenames.
- `BundledDictionaryTest.installUnpacksOnceAndReplacesAnOlderDictionary` reads the installed dictionary's `version` marker to verify the installer wrote the expected data version. Its other file checks validate unpacked dictionary contents and sizes.
