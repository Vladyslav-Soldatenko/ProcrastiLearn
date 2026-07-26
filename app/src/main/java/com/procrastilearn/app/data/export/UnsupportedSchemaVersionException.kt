package com.procrastilearn.app.data.export

class UnsupportedSchemaVersionException(
    val schemaVersion: Int,
) : IllegalArgumentException(
        "Export schema version $schemaVersion is newer than the supported version $CURRENT_SCHEMA_VERSION.",
    )
