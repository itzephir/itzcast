# Contributing to itzcast

Thank you for helping improve itzcast. Contributions are accepted under the
Apache License 2.0, the itzcast Individual Contributor License Agreement, and
the Developer Certificate of Origin 1.1.

## Before opening a pull request

1. Read [CLA.md](CLA.md) and [DCO](DCO).
2. Make focused changes that preserve the module boundaries described in
   [AGENTS.md](AGENTS.md).
3. Run `./gradlew check :app:compileKotlin`.
4. Sign off every commit with your real name and an email address you control.

The easiest way to add the required DCO trailer is:

```bash
git commit -s
```

The commit message must end with a trailer matching the commit author:

```text
Signed-off-by: Your Name <you@example.com>
```

Signing off certifies the statements in [DCO](DCO). It is not the same as GPG
or SSH commit signing. If a commit is missing the trailer, amend or rebase it
before asking for review.

## CLA acceptance

On your first pull request, the CLA Assistant will ask you to post this exact
comment:

```text
I have read the CLA Document and I hereby sign the CLA
```

The CLA lets you keep ownership of your contribution while giving the project
the copyright and patent permissions needed to maintain, distribute, defend,
and, if necessary, relicense it. If you contribute as part of your employment,
make sure you are authorized to grant those permissions.

## Pull request checks

Pull requests must pass:

- the Kotlin test and compilation matrix on macOS and Linux;
- the DCO check for every commit;
- the one-time CLA check for every contributor.

Please do not include credentials, local settings, build output, or files from
`~/.itzcast`.

## Developing against a local calkt build

itzcast uses the released calkt artifact from Maven Central by default. After
publishing a calkt snapshot to Maven Local, enable the repository and select
that version explicitly:

```bash
../calkt/gradlew publishToMavenLocal
./gradlew check :app:compileKotlin \
    -PuseMavenLocal=true \
    -PcalktVersion=0.0.2-SNAPSHOT
```

`calktVersion` may be set to any locally published version. Without
`useMavenLocal=true`, Maven Local is not consulted.
