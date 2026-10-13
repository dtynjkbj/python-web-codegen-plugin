package com.fastapi.codegen.model

data class ColumnInfo(
    val name: String,
    val typeName: String,
    val sqlType: Int,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val defaultValue: String?,
    val comment: String?
)

data class TableInfo(
    val name: String,
    val comment: String?,
    val columns: List<ColumnInfo>,
    val sourceFile: String? = null
) {
    val primaryKey: ColumnInfo?
        get() = columns.find { it.isPrimaryKey }

    val className: String
        get() = name.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    val snakeName: String
        get() = name
}
