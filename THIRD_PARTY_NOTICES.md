# Third-party notices

This repository includes or depends on third-party software. The project's own
license does not replace the licenses below.

## @okxconnect/ui 1.9.1

- Vendored file: `app/src/main/assets/wallet/okx-ui.js`
- Upstream package: `@okxconnect/ui@1.9.1`
- Upstream file: `dist/okxconnect_ui.min.js`
- Vendored file SHA-256:
  `3a29c09bb1bf70d9d62bbd7b7d78f32e9f1cb61d749120ed1e88bc5c5ad9712a`
- License: Apache-2.0
- License copy: `third_party/okxconnect-ui/LICENSE`
- Modifications: none; the vendored file is byte-identical to the npm dist file.
- Upstream package: https://www.npmjs.com/package/@okxconnect/ui/v/1.9.1
- Upstream tarball: https://registry.npmjs.org/@okxconnect/ui/-/ui-1.9.1.tgz
- The upstream package does not declare a public source repository or NOTICE file.

## @okxconnect/core 1.9.1

- Included inside the `@okxconnect/ui` bundle; package metadata embedded in the
  bundle declares `@okxconnect/core@1.9.1`.
- License: MIT (per upstream package metadata)
- License text: `third_party/okxconnect-core/LICENSE-MIT.txt`
- Upstream package: https://www.npmjs.com/package/@okxconnect/core/v/1.9.1
- The upstream package does not declare a public source repository.

## Other dependencies

JavaScript dependencies bundled into `okx-ui.js` (for example `core-js`,
`@stablelib/*`, `bs58`, `cross-fetch`, and others) retain their own upstream
licenses. Before publishing a source repository or redistributing a modified
bundle, verify and include the applicable notices for those dependencies.
