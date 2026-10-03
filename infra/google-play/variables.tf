variable "project_id" {
  description = "Existing Google Cloud project with Service Usage API enabled."
  type        = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id))
    error_message = "Use a Google Cloud project ID, not its display name or project number."
  }
}

variable "github_repository" {
  description = "GitHub owner/repository allowed to publish."
  type        = string
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Use owner/repository."
  }
}

variable "github_repository_id" {
  description = "Immutable numeric repository ID from gh api repos/OWNER/REPO --jq .id."
  type        = string
  validation {
    condition     = can(regex("^[0-9]+$", var.github_repository_id))
    error_message = "Use the numeric repository ID."
  }
}

variable "github_owner_id" {
  description = "Immutable numeric owner ID from gh api repos/OWNER/REPO --jq .owner.id."
  type        = string
  validation {
    condition     = can(regex("^[0-9]+$", var.github_owner_id))
    error_message = "Use the numeric owner ID."
  }
}
