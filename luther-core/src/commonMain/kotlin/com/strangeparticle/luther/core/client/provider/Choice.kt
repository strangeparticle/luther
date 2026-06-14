package com.strangeparticle.luther.core.client.provider

/** A selectable option surfaced by [LutherProviderCatalog]: [valueId] is the persisted/used value,
 *  [displayLabel] is shown to the user. Replaces the old Springboard `DropDownOption` at the seam. */
data class Choice(val valueId: String, val displayLabel: String)
