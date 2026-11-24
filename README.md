# MYFix — Pont natif `libfodfix` & Diagnostic FOD GSI

Application Android (`MYFix`) et bibliothèque native C/C++ (`libfodfix.so`, `arm64-v8a`) conçues pour diagnostiquer en direct les interactions `system ↔ HAL ↔ FOD` et déployer le pont natif sur un GSI Android 13–15 sans module Magisk.

## Fonctionnalités

- **Diagnostic** : Charge `/system/lib64/libfodfix.so` via `dlopen`, exécute le test natif et lance l'écoute en direct des événements `[HAL]` et `[FOD]`. La console n'affiche que la sortie de `libfodfix`.
- **Diagnostic without deploy** : Exécute le binaire actif (importé en priorité, sinon intégré) sans modifier `/system`, puis active le monitoring live et les contrôles **Tester empreinte (Live)** / **Paramètres empreinte**.
- **Importer** : Ouvre le gestionnaire de fichiers Android pour importer un binaire `libfodfix.so` (`arm64-v8a`, vérifié ELF64) qui devient prioritaire pour les tests, **Deploy** et **Export in sdcard**.
- **Deploy** : Déploie le binaire actif dans `/system/lib64/libfodfix.so`, `fodfix.rc` (non bloquant, anti-bootloop) dans `/system/etc/init/` et les fichiers SELinux de référence dans `/system/etc/selinux/myfix/`.
- **Export in sdcard** : Enregistre dans `/sdcard/MYFix/` le binaire `lib64/libfodfix.so`, les politiques SELinux (`fodfix.te`, `fodfix_file_contexts`, `plat_sepolicy_fodfix.cil`), `init/fodfix.rc` et les scripts `install_gsi.sh` / `uninstall_gsi.sh`.
- **Paramètres & Mode d'emploi** : Accessible depuis l'icône engrenage pour consulter le guide d'utilisation complet et gérer le binaire actif.

## Compilation GitHub Actions

Le workflow `.github/workflows/android-build.yml` compile automatiquement :
- `MYFix-debug-apk` (contenant `lib/arm64-v8a/libfodfix.so`)
- `libfodfix-arm64-gsi-bundle` (le `.so` autonome + SELinux + `init` + scripts)

Compilation locale avec NDK & CMake :
```bash
gradle assembleDebug -PMYFIX_NATIVE_BUILD=true
```
