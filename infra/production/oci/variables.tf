variable "region" {
  type        = string
  description = "The account's home region; Always Free compute must be provisioned there."
}
variable "compartment_id" {
  type = string
}
variable "availability_domain" {
  type = string
}
variable "ubuntu_arm64_image_id" {
  type        = string
  description = "Exact immutable OCID of an official Canonical Ubuntu 24.04 ARM64 image in the home region."
  validation {
    condition     = startswith(var.ubuntu_arm64_image_id, "ocid1.image.")
    error_message = "Supply the verified regional image OCID, not a latest-image selector."
  }
}
variable "admin_cidr" {
  type = string
  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && endswith(var.admin_cidr, "/32")
    error_message = "SSH must be restricted to the operator's single public IPv4 address (/32)."
  }
}
variable "ssh_public_key" {
  type        = string
  description = "An SSH public key only. Never supply a private key."
  validation {
    condition     = can(regex("^ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI[A-Za-z0-9+/]{43}( [^\\r\\n]+)?$", trimspace(var.ssh_public_key)))
    error_message = "Supply one complete OpenSSH Ed25519 public key with its encoded 32-byte key, optionally followed by a comment."
  }
}
variable "confirm_always_free_only" {
  type        = bool
  default     = false
  description = "Human confirmation of free-account and unused-capacity evidence. Does not authorize upgrades or payments."
}
