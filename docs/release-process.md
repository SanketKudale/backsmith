# Release process

1. Update the changelog and every Maven module version.
2. Run `./mvnw clean verify`.
3. Verify CLI help and doctor commands.
4. Generate layered and hexagonal samples and compile them.
5. Create and push an annotated semantic-version tag matching the Maven version:

   ```shell
   git tag -a vX.Y.Z -m "Backsmith X.Y.Z"
   git push origin vX.Y.Z
   ```

6. Monitor the `release` GitHub Actions workflow.
7. Download an archive and verify it against `SHA256SUMS`.

The workflow runs `clean verify`, builds the shaded executable JAR, packages ZIP and TAR.GZ distributions, generates SHA-256 checksums, and creates the public GitHub Release. A manual workflow run against an existing tag safely replaces release assets, which supports repairing an interrupted publication.

Release publication uses only the repository-scoped GitHub token. No personal access token or external paid service is required.

Automated Maven Central, Homebrew, Scoop, and SDKMAN publication is not implemented in 0.1.0. Those channels require namespace ownership, supporting repositories, or vendor onboarding and should be added after the CLI surface stabilizes.
