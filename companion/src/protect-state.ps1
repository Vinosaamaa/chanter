param([Parameter(Mandatory = $true)][string]$StatePath, [string]$CreatedEntry)
$ErrorActionPreference = 'Stop'
try {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    if (-not [System.IO.Directory]::Exists($StatePath)) {
        $security = New-Object System.Security.AccessControl.DirectorySecurity
        $security.SetOwner($identity)
        $security.SetAccessRuleProtection($true, $false)
        $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $security.AddAccessRule($rule)
        [System.IO.Directory]::CreateDirectory($StatePath, $security) | Out-Null
    }
    $items = @((Get-Item -LiteralPath $StatePath -Force))
    # Only callers that just created an entry exclusively may select this path.
    # Elevated Windows tokens can default new-object ownership to Administrators.
    # Keep current-user ownership without accepting or repairing existing foreign owners.
    if ($CreatedEntry) {
        if ($CreatedEntry -notin @('state.sqlite', 'app', 'configuration.json', 'start.ps1', 'provider', 'runs')) { throw 'Unexpected entry' }
        $created = Get-Item -LiteralPath (Join-Path $StatePath $CreatedEntry) -Force
        $items += @($created)
        if ($created.PSIsContainer) { $items += @(Get-ChildItem -LiteralPath $created.FullName -Force -Recurse) }
    } else {
    $items += @(Get-ChildItem -LiteralPath $StatePath -Force | Where-Object { $_.Name -like 'state.sqlite*' -or $_.Name -in @('configuration.json', 'app', 'provider', 'runs', 'start.ps1') })
    }
    foreach ($item in $items) {
        if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw 'Unsafe state' }
        $acl = $item.GetAccessControl()
        if ($item -eq $items[0] -and -not $acl.AreAccessRulesProtected) { throw 'Inherited state access' }
        if (($item -eq $items[0] -or -not $CreatedEntry) -and $acl.GetOwner([System.Security.Principal.SecurityIdentifier]).Value -ne $identity.Value) { throw 'Unsafe owner' }
        $rules = @($acl.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))
        if ($rules.Count -eq 0) { throw 'Missing access' }
        foreach ($rule in $rules) {
            if ($rule.IdentityReference.Value -ne $identity.Value -or $rule.AccessControlType -ne 'Allow' -or $rule.FileSystemRights -ne 'FullControl') { throw 'Unsafe access' }
        }
        if ($CreatedEntry -and $item -ne $items[0]) {
            $acl.SetOwner($identity)
            $item.SetAccessControl($acl)
        }
    }
    [Console]::Out.Write('protected')
} catch {
    # Never emit local paths, user identifiers, or native exceptions.
    [Console]::Error.Write('NATIVE_STATE_UNPROTECTED')
    exit 1
}
