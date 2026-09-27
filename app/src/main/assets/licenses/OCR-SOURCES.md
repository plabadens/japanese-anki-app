Japanese OCR provenance
=======================

Tesseract4Android: cz.adaptech.tesseract4android:tesseract4android:4.9.0
Upstream: https://github.com/adaptech-cz/Tesseract4Android/tree/15c534717b1cb58261b58d4e4c1200c7f81f668c
License: Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0)
Dependency provides the Android Tesseract native engine; app ABI filters remain arm64-v8a and x86_64.

Models are official tessdata_fast traineddata, fetched by Nix and packaged as Android assets:

* jpn.traineddata: https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/80d92b7db61cb6ddc519049990be34e4c913566f/jpn.traineddata
  Commit: 80d92b7db61cb6ddc519049990be34e4c913566f
  SHA-256 SRI: sha256-H13pI20uhfX99LPFAPLUkm+NlEnyj1OURy2ejYO5G00=
  License: Apache-2.0
* jpn_vert.traineddata: https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/923915d4ced2a7235221788285785a29c4a42d4a/jpn_vert.traineddata
  Commit: 923915d4ced2a7235221788285785a29c4a42d4a
  SHA-256 SRI: sha256-vx4mQJVGkXl+LcFPOFM+YBtZ7jeVhpiuDwuB3G8Jxxs=
  License: Apache-2.0

Gradle dependency resolution uses JitPack from dependencyResolutionManagement only. Models are never fetched by the app; the service copies verified packaged assets to private filesDir/tessdata. Candidate choice is the non-empty output with the higher mean block confidence (horizontal on ties).
