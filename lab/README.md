# Disposable Stone Statues staging

This directory targets only the existing disposable
`bluemap-sophisticated-staging` host. It does not describe or authorize a
production deployment. The workload must remain at zero replicas while local
artifacts are uploaded or while the PVC is mounted by `stone-artifact-loader`.

The accepted inputs are:

- production add-on: 286,752 bytes, SHA-256
  `9800484109aa7e571f393aa96b069e6248186c35103b6e26fd9dad920e4efcd4`;
- gallery: 3,153 bytes, SHA-256
  `1d8193b783eb1d30dd347896cc433e6de72549a472d800ff7484b16a17d04121`;
- operator-local bundle: 99,408,820 bytes, SHA-256
  `edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa`.

The 2026-08-16 controlled run completed exact-resource preflight, gallery
verification (`8` checked, `0` failures), and a forced fresh render with zero
pod restarts. The owner accepted the four supported statues plus both controls;
the unknown master and orphan proxy remained invisible. This disposable
staging evidence may now be replaced by the next add-on cycle after the exact
release identities are durably recorded.

The bundle remains under `/data/.bluemap-sophisticated-staging`; it is never
copied into BlueMap's pack directory or webroot. The deployment installs the
production add-on JAR in `config/bluemap/packs` and selects the bundle by its
exact path and SHA through environment variables. Five `.zip`-named symbolic
links in that pack directory expose the original exact winner JARs in
descending client-attested priority: CCC compatibility, All the Mons, Z-A
Mega, Mega Showdown, and Cobblemon. BlueMap sorts that directory in reverse;
the aliases therefore prevent its otherwise unordered mod-folder scan from
shadowing the exported winners. They neither copy nor redistribute mod bytes,
and their `.zip` suffix keeps them outside BlueMap add-on and exact-mod-JAR
detection.

## Controlled sequence

Use kubeconfig `/root/.kube/guenter-cloud`, context `guenter.cloud`, and
namespace `bluemap-sophisticated-staging` for every command.

1. Scale `deployment/minecraft` to zero and wait until its pod is deleted.
2. Delete any prior `stone-artifact-loader` Pod with `--ignore-not-found
   --wait`, then apply `kubernetes/config.yaml`, `kubernetes/pins.yaml`, and
   `kubernetes/artifact-loader.yaml`; wait for `READY_FOR_UPLOAD`.
3. Copy the exact add-on, gallery, and bundle to `.part` names beneath
   `/data/.bluemap-sophisticated-staging`, atomically rename them, and create
   `.stone-upload-complete`.
4. Require `LOCAL_ARTIFACTS_VERIFIED`, then delete `stone-artifact-loader` and
   wait for its pod to disappear.
5. Apply `kubernetes/deployment-patch.yaml` only with the exact strategic-
   merge command below. It is intentionally not a standalone Deployment and
   must never be passed to `kubectl apply`. The patch itself keeps the
   workload at zero replicas.
6. Apply `kubernetes/ingress-patch.yaml`, inspect the resulting Deployment,
   ConfigMaps, and Ingress, then scale to one. Require the init hashes and a
   clean startup without an add-on failure message. Confirm in BlueMap's debug
   log that the five `zz-*` roots appear first and in the documented order.
   This checkpoint has no positive pre-render activation log; do not infer an
   ACTIVE runtime merely from startup silence.
7. Build and verify the eight-cell gallery, force a clean map render, inspect
   `https://bluemap-stone-statues.guenter.cloud/`, and return the deployment
   to zero when review ends.
   The fresh render is the first positive end-to-end activation evidence.

The exact deployment snapshot inspected before creating this patch had
SHA-256
`c3fd77a4319ac48a2fe22ec25c4f9beba10e6025c39c05e74c8e92f960a80150`.
It was internally stale: its Rechiseled init referenced a map key absent from
the live ConfigMap. Do not restart that unpatched revision.

Rollback is fail-closed: scale the workload to zero, then apply
`kubernetes/rollback-deployment-patch.yaml`. It removes both bundle variables
and replaces the artifact installer with a narrowly scoped cleanup of the
Stone add-on JAR, so a later start cannot silently reinstall it. The cleanup
target is recoverable from the private immutable upload directory. No world
schema or migration is owned by the add-on.

```bash
kubectl --kubeconfig /root/.kube/guenter-cloud --context guenter.cloud \
  -n bluemap-sophisticated-staging patch deployment minecraft \
  --type=strategic --patch-file kubernetes/deployment-patch.yaml

kubectl --kubeconfig /root/.kube/guenter-cloud --context guenter.cloud \
  -n bluemap-sophisticated-staging patch ingress \
  bluemap-sophisticated-review-public --type=strategic \
  --patch-file kubernetes/ingress-patch.yaml

# Fail-closed rollback, after scaling to zero:
kubectl --kubeconfig /root/.kube/guenter-cloud --context guenter.cloud \
  -n bluemap-sophisticated-staging patch deployment minecraft \
  --type=strategic --patch-file kubernetes/rollback-deployment-patch.yaml
```
