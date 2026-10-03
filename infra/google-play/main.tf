terraform {
  required_version = ">= 1.6, < 2.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }
}

provider "google" {
  project = var.project_id
}

data "google_project" "publishing" {
  project_id = var.project_id
}

resource "google_project_service" "publishing" {
  for_each = toset([
    "androidpublisher.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
  ])
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_service_account" "publisher" {
  account_id   = "scenedeck-play-publisher"
  display_name = "SceneDeck Google Play publisher"
  depends_on   = [google_project_service.publishing]
}

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "scenedeck-github"
  display_name              = "SceneDeck GitHub releases"
  depends_on                = [google_project_service.publishing]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository_id" = "assertion.repository_id"
  }
  # Numeric IDs prevent a renamed/deleted repository from granting access to a new owner.
  # Match the release job's environment and workflow, including manual runs and tags.
  attribute_condition = join(" && ", [
    "assertion.repository_id == '${var.github_repository_id}'",
    "assertion.repository_owner_id == '${var.github_owner_id}'",
    "assertion.sub == 'repo:${var.github_repository}:environment:google-play'",
    "assertion.workflow_ref.startsWith('${var.github_repository}/.github/workflows/release.yml@')",
  ])
  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "github" {
  service_account_id = google_service_account.publisher.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository_id/${var.github_repository_id}"
}
