Controller coverage lists (IAM Phase 0.8), checked by `ControllerAccessCoverageTest`.

* `controllers-without-preauthorize.txt` – controllers that deliberately have no `@PreAuthorize` (public sign-in, signing
  links, portal logins, exception advice). Adding a controller here is a security decision: say why in the pull request.
* `controllers-without-module-guard.txt` – KNOWN DEBT: controllers that never call `FeatureGuard`, so a tenant that has
  not bought the module can still reach them. The list may only shrink. The test fails when a controller is not guarded
  and not on this list, and also when a listed controller has since been fixed (remove it from the list).
