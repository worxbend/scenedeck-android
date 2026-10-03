output "workload_identity_provider" {
  description = "Set the GitHub variable PLAY_WORKLOAD_IDENTITY_PROVIDER to this value."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "service_account_email" {
  description = "Invite this email in Play Console; set PLAY_SERVICE_ACCOUNT_EMAIL in GitHub."
  value       = google_service_account.publisher.email
}
