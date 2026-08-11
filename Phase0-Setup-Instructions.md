# Phase 0 — Setup Instructions

## 1. File placement

Copy `ArchitectureTest.java` to:

```
platform/src/test/java/za/co/handyflow/platform/ArchitectureTest.java
```

If `platform/src/test/java` doesn't exist yet (Q12's answer suggested `src/test/java` "doesn't fully exist yet" project-wide), create the directory path first — Maven's default test source root, no `pom.xml` change needed for the path itself.

## 2. pom.xml dependency

Add to `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

If the project isn't already importing the Spring Modulith BOM (likely it is, since `@ApplicationModule` and `DomainEvent`'s Modulith integration are already in use — check for an existing `spring-modulith-bom` or `spring-modulith-starter-jpa`/`spring-modulith-events-api` entry first), add the BOM to `<dependencyManagement>` so the version resolves automatically:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version><!-- match whatever version the existing spring-modulith
                          dependencies in pom.xml already use --></version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Check the existing Modulith version first** rather than guessing a number — `DomainEvent`'s Javadoc and every `package-info.java`'s `@ApplicationModule` usage confirm Modulith is already a dependency somewhere in `pom.xml`; `spring-modulith-starter-test` needs to match that version exactly to avoid classpath conflicts.

For the PlantUML diagram generation in `writeModuleDocumentation()` (optional, not part of the CI gate), also add:

```xml
<dependency>
    <groupId>net.sourceforge.plantuml</groupId>
    <artifactId>plantuml</artifactId>
    <scope>test</scope>
</dependency>
```

## 3. First run — what to expect

```
mvn test -Dtest=ArchitectureTest#moduleBoundariesAreRespected
```

**This will fail on first run.** The expected failure is `recruiter` importing `hr.application.internal.HrService` (Section 15.2). Modulith's error output will name the exact source class, target class, and which module's `allowedDependencies` was violated — that failure report is itself useful as a precise bug ticket, more precise than the discovery document's description of it.

If other, previously-unfound violations of this same class (direct cross-module Java imports, not JDBC) surface in the failure output, that's the audit in Section 15 finding more instances than search-based discovery could — expected and valuable, not a sign anything is wrong with the test.

## 4. CI wiring

Add `moduleBoundariesAreRespected` to whatever gate currently runs on PRs (or, if no test gate currently runs in CI — plausible given Q12's answer — this may be the first test-based CI gate for the backend, in which case wiring CI to run `mvn test` at all is a slightly bigger, but closely related, prerequisite task worth confirming isn't also missing).

## 5. Sequencing with Phase 1

Land this test **before** starting the Recruiter/Marketing/Expenses fixes, but expect it to be red (failing) between landing it and finishing the Recruiter fix — that's fine for a short-lived branch/PR sequence, but don't merge this test to a shared branch in a failing state if that would block other people's unrelated PRs. Options: merge with the Recruiter fix in the same PR (test + fix together, always green on merge), or merge the test first with a documented, temporary `@Disabled` on the one known-failing assertion and a tracked follow-up to remove it — the second option is weaker (a disabled test provides no protection) but may suit your branch/review workflow better. Your call on which fits the team's process.
