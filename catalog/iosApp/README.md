# Kompound Catalog – iOS

The Xcode project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) (the generated `KompoundCatalogApp.xcodeproj` is committed for convenience; regenerate after changing `project.yml`).

```bash
brew install xcodegen
cd catalog/iosApp && xcodegen generate
open KompoundCatalogApp.xcodeproj      # run on a simulator
```

Build step runs `./gradlew :catalog:shared:embedAndSignAppleFrameworkForXcode`, which produces the `KompoundCatalog` framework from `:catalog:shared`.
Simulator builds need no Apple account. For a device set `DEVELOPMENT_TEAM` (needs an Apple Developer account).

Command line build (`ARCHS=arm64` because only Apple Silicon simulator and device targets are configured in Gradle):

```bash
xcodebuild -project KompoundCatalogApp.xcodeproj -scheme KompoundCatalogApp \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 build
```
