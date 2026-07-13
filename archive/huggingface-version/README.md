# Hugging Face version (archived)

This folder is a **reference snapshot** of the old app version that sent
screenshots to a Hugging Face server (`https://cpdodia-screentrack-api.hf.space`)
for analysis. It was replaced by the **on-device** version (ML Kit OCR + local
classification, nothing leaves the phone).

- These files are a copy of the last committed state **before** the on-device
  migration. The full version also lives permanently in git history.
- This folder is at the project root, **outside `app/src/`**, so it is NOT
  compiled into the build. It's for reference only.
- Key HF-specific file: `FrameUploader.kt` (the screenshot uploader).

Do not edit files here expecting them to affect the app. The live app is under
`/app/src/`.
