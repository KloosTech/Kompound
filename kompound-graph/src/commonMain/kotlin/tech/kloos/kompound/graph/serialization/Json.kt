package tech.kloos.kompound.graph.serialization

import tech.kloos.kompound.json.KJsonException

// The JSON tree lives in :kompound (tech.kloos.kompound.json) so theme import can use it too; these names keep graph code and callers unchanged.
public typealias JsonValue = tech.kloos.kompound.json.JsonValue
public typealias JsonNull = tech.kloos.kompound.json.JsonNull
public typealias JsonBool = tech.kloos.kompound.json.JsonBool
public typealias JsonNumber = tech.kloos.kompound.json.JsonNumber
public typealias JsonString = tech.kloos.kompound.json.JsonString
public typealias JsonArray = tech.kloos.kompound.json.JsonArray
public typealias JsonObject = tech.kloos.kompound.json.JsonObject

/** Something is wrong with a JSON text or with the graph it describes. */
public typealias GraphJsonException = KJsonException

/** Builds a [JsonObject] from pairs; `null` values are left out. */
public fun jsonObjectOf(vararg fields: Pair<String, JsonValue?>): JsonObject = tech.kloos.kompound.json.jsonObjectOf(*fields)
