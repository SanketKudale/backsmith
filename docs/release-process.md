# Release process

1. Update the changelog and every Maven module version.
2. Run `./mvnw clean verify`.
3. Verify CLI help and doctor commands.
4. Generate and verify all seven architecture samples and the full production feature stack.
5. Create and push an annotated semantic-version tag matching the Maven version:

   ```shell
   git tag -a vX.Y.Z -m "Backsmith X.Y.Z"
   git push origin vX.Y.Z
   ```

6. Monitor the `release` GitHub Actions workflow.
7. Download an archive and verify it against `SHA256SUMS`.

The workflow runs `clean verify`, builds the shaded executable JAR, packages ZIP and TAR.GZ distributions, generates SHA-256 checksums, and creates the public GitHub Release. A manual workflow run against an existing tag safely replaces release assets, which supports repairing an interrupted publication.

When Maven Central username, password, private key, and passphrase secrets are configured, the same workflow signs sources, Javadocs, and binaries and publishes all library modules through the Central Publishing Portal. GitHub Release publication remains available without those optional credentials.

Homebrew and Scoop repositories are synchronized by their release workflows, and the JBang catalog points to the public release asset. SDKMAN still requires external vendor onboarding; see `sdkman-onboarding.md`.
