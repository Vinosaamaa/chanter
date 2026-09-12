terraform {
  required_version = ">= 1.9, < 2.0"
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "9.1.0"
    }
  }
}

# Credentials come from the operator's OCI profile, never from repository files.
provider "oci" {
  region = var.region
}

resource "oci_core_vcn" "chanter" {
  compartment_id = var.compartment_id
  cidr_block     = "10.243.0.0/16"
  display_name   = "chanter-free"
  dns_label      = "chanter"
}

resource "oci_core_internet_gateway" "chanter" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.chanter.id
  display_name   = "chanter-free"
  enabled        = true
}

resource "oci_core_route_table" "chanter" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.chanter.id
  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.chanter.id
  }
}

resource "oci_core_security_list" "chanter" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.chanter.id
  display_name   = "chanter-public-edge-only"
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }
  ingress_security_rules {
    protocol = "6"
    source   = var.admin_cidr
    tcp_options {
      min = 22
      max = 22
    }
  }
  dynamic "ingress_security_rules" {
    for_each = toset([80, 443, 7881])
    content {
      protocol = "6"
      source   = "0.0.0.0/0"
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }
  ingress_security_rules {
    protocol = "17"
    source   = "0.0.0.0/0"
    udp_options {
      min = 7882
      max = 7882
    }
  }
}

resource "oci_core_subnet" "chanter" {
  compartment_id    = var.compartment_id
  vcn_id            = oci_core_vcn.chanter.id
  cidr_block        = "10.243.1.0/24"
  dns_label         = "edge"
  route_table_id    = oci_core_route_table.chanter.id
  security_list_ids = [oci_core_security_list.chanter.id]
}

resource "oci_core_instance" "chanter" {
  compartment_id       = var.compartment_id
  availability_domain  = var.availability_domain
  display_name         = "chanter-free"
  shape                = "VM.Standard.A1.Flex"
  preserve_boot_volume = true
  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }
  create_vnic_details {
    subnet_id        = oci_core_subnet.chanter.id
    assign_public_ip = true
    hostname_label   = "chanter"
  }
  source_details {
    source_type             = "image"
    source_id               = var.ubuntu_arm64_image_id
    boot_volume_size_in_gbs = 100
  }
  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(file("${path.module}/bootstrap.sh"))
  }
  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = var.confirm_always_free_only
      error_message = "Verify an unupgraded Always Free account, home region and unused 2 OCPU/12 GB/100 GB capacity before applying. Never switch to a paid shape or upgrade on capacity errors."
    }
  }
}

output "public_ip" {
  value = oci_core_instance.chanter.public_ip
}
