# Contributing

Thanks for contributing to `PDF-StitchUI`.

## Workflow

- Fork the repository or create a feature branch.
- Open a pull request for every change.
- Do not commit directly to `main`.
- Keep the Maven build passing before requesting review.

## Reviews

- Every pull request should be reviewed before merge.
- The intended reviewers are the repository owners and maintainers.
- Update `.github/CODEOWNERS` with the correct GitHub usernames before relying on automatic reviewer assignment.

## Local Build

```powershell
.\mvnw.cmd verify
```

This project expects `verify` to stay green, which includes:

- unit tests
- JaCoCo coverage generation
- the 90% core-logic coverage gate

## Quality Gate

- GitHub CI runs `./mvnw -B verify` on pull requests and pushes to `main`.
- SonarQube Cloud analysis is wired in [.github/workflows/sonar.yml](.github/workflows/sonar.yml).
- SonarQube Cloud requires repository configuration before it will run:
  - GitHub variables `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION`
  - GitHub secret `SONAR_TOKEN`

## Scope

- Keep pull requests focused.
- Call out any UI behavior changes with screenshots or a short screen recording when practical.
- Document follow-up work rather than hiding it inside TODO comments.

## Branding

- Treat [branding/](branding/) as the source of truth for visual identity work.
- The current selected direction is `Tile Sequence / Balanced`.
- Keep committed brand briefs, decisions, and selected concept assets under `branding/`.
- Keep scratch branding work in the ignored folders described by [.gitignore](.gitignore).
