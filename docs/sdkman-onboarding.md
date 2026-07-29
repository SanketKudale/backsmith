# SDKMAN candidate onboarding

SDKMAN publication requires vendor approval and credentials issued by SDKMAN. This repository cannot self-enroll without that external approval.

## Candidate information

- Candidate: `backsmith`
- Display name: Backsmith
- Vendor: Backsmith
- Homepage: https://sanketkudale.github.io/backsmith/
- Source: https://github.com/SanketKudale/backsmith
- License: Apache-2.0
- Description: Deterministic and conflict-aware Java CLI for generating Spring Boot backend foundations.
- Distribution type: universal ZIP
- Java requirement: Java 21 or newer
- Release URL pattern: `https://github.com/SanketKudale/backsmith/releases/download/v<VERSION>/backsmith.zip`
- Checksum source: `https://github.com/SanketKudale/backsmith/releases/download/v<VERSION>/SHA256SUMS`

## Maintainer action

1. Complete SDKMAN vendor onboarding at https://sdkman.io/vendors/.
2. Request the `backsmith` candidate name.
3. Store the issued consumer key and token as GitHub Actions secrets.
4. Enable the SDKMAN release step only after those credentials exist.

The GitHub Release archives already match SDKMAN's universal distribution model and include launchers for Unix and Windows.
