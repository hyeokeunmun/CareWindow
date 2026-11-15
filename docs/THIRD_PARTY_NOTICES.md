# Third-party notices

- `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are from Gradle v8.10.2. Their Apache License 2.0 notices are retained. See `GRADLE-LICENSE.txt` and https://github.com/gradle/gradle/tree/v8.10.2.
- The original CareWindow Kotlin source was written for this project. No license for public redistribution of the project's original source is chosen here; choose one with the project owner before publishing.
- PLEOS SDK binaries and upstream sample source files are not redistributed in this source archive. Gradle resolves the pinned SDKs from the official PLEOS Maven repository. Respect the SDK/provider terms that apply to your use and distribution.
- Source-verification downloads use Kotlin/JUnit and supporting artifacts from Maven Central plus Android API stubs from Android Open Source Project official repositories. Those files are not packaged in this archive. The verification manifest records exact hashes and origins.
- Official sample repositories and SDK source artifacts were inspected to establish signatures, lifecycle and API behavior. Their links and inspected revisions are in `SOURCES.md`.

- The separately provided debug APK includes runtime dependencies resolved from the pinned official PLEOS repository and public Maven repositories. It is a private development artifact, not evidence of authorization for public store redistribution or of passing PLEOS review.
- Robolectric 4.14.1 is a test-only dependency and is not packaged as an app runtime dependency.
