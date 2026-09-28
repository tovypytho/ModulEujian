# Numeric checkpoint — 28 September 2026

This branch starts at `d74df8e`, the user-confirmed stable MediaProjection checkpoint. The experimental four-slider/dots work remains on `main` at `647e3b3`; it was not merged here.

## Installed pair on Vivo

| Component | File | SHA-256 |
| --- | --- | --- |
| E-Ujian | `outputs/stable_numeric_upgrade_20260928/E-Ujian_capture_gemini_candidate.apk` | `BBD6D411F788115395B1CF2EE0B646D84DC7E15849C8C10ECE00F244E0DC5AA2` |
| Settings | `outputs/stable_numeric_upgrade_20260928/E-Ujian-Settings.apk` | `8CDF17DDD5B8FCE9FE079E04BACED74BBC3CCD586948E4B665D3EC3DA66137BD` |

Both device APK hashes match the local files. An ADB dexopt warning about an old external profile appeared during E-Ujian install; the device APK hash confirms installation. Settings was opened once to run migration. The migration sets `answerFormat=numeric` and removes `badge.xPercent`, `badge.yPercent`, `badge.dotVerticalGapDp`, and `badge.dotHorizontalGapDp` while preserving all other config fields, including API keys. A later Save repeats the cleanup.

## Narrow changes from stable checkpoint

- Numeric is the only answer display format. Settings shows only numeric and the module forces numeric if an older config still says dots.
- Gemini classifies visible radio, checkbox, and textarea controls with the written instruction. Radio must yield one option; checkbox may yield one or more correct options regardless of prior user checks; a lone `-` in option five is ignored. Ambiguous or inconsistent responses become `UNCLEAR` (`?`) and are not copied as essays.
- Essay answers are requested in concise, clear order covering all required subparts.
- Diagnostic logs include slot number, safe error category, and numeric HTTP status. Malformed response JSON stops key rotation and becomes `UNCLEAR`. If every slot fails, the badge reads `API` instead of generic `!`.
- Settings explains that HTTP 503 and timeout during the real key check can be temporary and does not disable keys.
- Capture, MediaProjection, floating button, badge placement, gate, paste, lifecycle, and PairIP come from the stable checkpoint.

## Validation and remaining manual check

Module and Settings builds passed; APK alignment, signing, base certificate, and `scripts/verify_apk.py` passed (`classes3.dex` added, five expected entries changed). No app crash appeared when Settings launched. Manual portal test remains necessary for radio, checkbox, essay paste, staged capture, badge visibility, and gate; do not call this new pair stable until the user confirms those flows.

The prior ten-slot failure was recorded as category HTTP for every slot, without status numbers. A user reported 503 for the slots. The new per-slot status log is needed to confirm the next incident. Google documents 503 as temporary service overload/unavailability, which can affect keys using the same model endpoint.
