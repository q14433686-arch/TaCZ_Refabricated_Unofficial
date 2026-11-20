# SimpleBedrockModel particle and Molang port

Source: https://github.com/MCModderAnchor/SimpleBedrockModel
Revision: 71ad526ac103a23df0f9a3f5959216168b89d741 (1.21.1-dev).
Copyright: the SimpleBedrockModel contributors; original author attribution remains in source.
License: LGPL-3.0, reproduced in SimpleBedrockModel-LGPL-3.0.txt, together with GPL-3.0 in the repository LICENSE.

The relocated Molang compiler and particle data/runtime code in
`src/main/java/com/tacz/guns/client/particle/bedrock/` is derived from that revision.
Changes: package relocation, Minecraft 26.3 Identifier naming, Fabric resource/lifecycle
integration, scoped runtime ownership and bounded resource/event work. The host renderer
and event adapters are maintained in this repository. No default gun-pack artwork was copied.
Source for these modifications is distributed with this repository under its GPL-3.0 license;
the original library's LGPL notices and rights are preserved.
