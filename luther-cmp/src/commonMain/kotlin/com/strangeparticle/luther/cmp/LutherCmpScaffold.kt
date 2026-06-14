package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.LutherVersion

// Placeholder anchoring the luther-cmp module during the initial scaffold (Phase 1).
// The real Compose Multiplatform UI is moved in here in Phase 2. Referencing
// LutherVersion confirms the api(project(":luther-core")) link and that the generated
// version constant is visible across the module boundary.
internal val lutherCmpScaffoldVersion: String = LutherVersion.VERSION
