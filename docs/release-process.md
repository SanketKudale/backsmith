# Release process

1. Update the changelog and version.
2. Run `./mvnw clean verify`.
3. verify CLI help and doctor commands.
4. generate layered and hexagonal samples and compile them.
5. tag `vX.Y.Z` and create checksums for release artifacts.

Automated Maven Central publishing is not implemented in 0.1.0.
