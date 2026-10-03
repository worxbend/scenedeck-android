# Google Play publishing infrastructure

This Terraform configuration enables the publishing APIs and creates a publishing
service account and GitHub Workload Identity Federation trust in an **existing**
Google Cloud project. It creates no service-account keys, compute services, or
billing resources. The Android package is `com.worxbend.scenedeck`.

Trust is restricted to the numeric GitHub repository and owner IDs, the
`google-play` job environment, and `.github/workflows/release.yml`. Play permissions
are granted separately in Play Console; Google Cloud IAM alone cannot grant them.
No project-wide IAM role is assigned to the publishing service account.

## Deploy once

Install Terraform >= 1.6 (tested with 1.13.5), Google Cloud CLI, and GitHub CLI.
The Google provider is locked in `.terraform.lock.hcl`. Keep the lock file in Git.
Use a Cloud identity allowed to enable APIs, manage service accounts and federation,
and update IAM on the publishing service account. Enable the Service Usage API on
the existing project before running this configuration.

```bash
gcloud auth application-default login
cp infra/google-play/terraform.tfvars.example infra/google-play/terraform.tfvars
```

Set `project_id` in the copied file to the actual Google Cloud project ID, which
is different from a Play app name or developer account ID. The example contains
the current IDs for `worxbend/scenedeck-android`; for a different repository, obtain
them with:

```bash
gh api repos/OWNER/REPOSITORY --jq '{repository_id: .id, owner_id: .owner.id}'
```

From the repository root:

```bash
terraform -chdir=infra/google-play init
terraform -chdir=infra/google-play plan -out=publishing.tfplan
terraform -chdir=infra/google-play apply publishing.tfplan
gh variable set PLAY_WORKLOAD_IDENTITY_PROVIDER --body "$(terraform -chdir=infra/google-play output -raw workload_identity_provider)"
gh variable set PLAY_SERVICE_ACCOUNT_EMAIL --body "$(terraform -chdir=infra/google-play output -raw service_account_email)"
```

Back up the Terraform state or configure your own remote backend before applying.
State, plans, and local variable files are ignored by Git. The state contains
resource identifiers, but no generated private keys. Authentication propagation
can take several minutes after provisioning.

If you have already used `gcloud auth login`, the repository helper can use that
login directly, without a second browser login or a token file:

```bash
python3 scripts/play-infra.py plan -out=publishing.tfplan
python3 scripts/play-infra.py apply publishing.tfplan
```

It obtains a temporary token internally and passes it to Terraform's environment.
The token is not printed or written to a credentials file. Terraform must be on PATH;
`SCENEDECK_TERRAFORM_BINARY` can select another installed binary.

## Remaining Play Console setup

1. Finish developer identity/device verification and create the app, if needed.
2. Invite the `service_account_email` output in **Users and permissions**. Limit it
   to SceneDeck and grant app-information editing and testing-release permissions.
   Add production-release permission when you intend to publish publicly.
3. Upload the first signed AAB manually and enroll in Play App Signing. Subsequent
   bundles and store listings can use the publishing API.
4. Complete the policy declarations, privacy-policy link, reviewer access,
   foreground-service declaration, and required closed testing/production-access
   application. Those remain Play Console tasks for this pipeline.

See [RELEASING.md](../../docs/RELEASING.md) for signing and release commands.
The Play publishing job uses temporary OIDC credentials when the provider variable
is set. The existing `PLAY_SERVICE_ACCOUNT_JSON` secret is only a fallback when
the provider variable is empty; it is not needed for this configuration.

Official references: [GitHub authentication](https://github.com/google-github-actions/auth),
[Play API access](https://developers.google.com/android-publisher/getting_started),
[initial upload](https://developers.google.com/android-publisher/edits).
