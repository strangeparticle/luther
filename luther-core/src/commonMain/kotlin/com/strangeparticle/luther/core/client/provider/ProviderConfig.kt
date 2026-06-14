package com.strangeparticle.luther.core.client.provider

/**
 * Marker for a provider's strongly-typed configuration. luther's generic layer
 * (LutherSettings, the catalog, the session) carries this opaquely and never
 * inspects fields; each provider casts it to its own concrete type. This is the
 * typed seam between the host's settings and luther — not JSON, not a string bag.
 */
interface ProviderConfig
