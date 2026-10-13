package com.fastapi.codegen.generator

import com.fastapi.codegen.db.DatabaseConnector
import com.fastapi.codegen.model.ColumnInfo
import com.fastapi.codegen.model.TableInfo
import java.io.File
import java.sql.Types

class CodeGenerator {

    enum class ResponseStyle {
        CUSTOM_WRAPPER,
        STANDARD
    }

    enum class WebFramework {
        FASTAPI,
        FLASK,
        DJANGO_REST,
        SANIC,
        STARLETTE
    }

    data class ProjectContext(
        val responseStyle: ResponseStyle,
        val responseManagerClass: String? = null,
        val responseManagerImport: String? = null,
        val baseResponseClass: String? = null,
        val baseResponseImport: String? = null,
        val usesAsync: Boolean = false,
        val ormType: String = "sqlalchemy",
        val framework: WebFramework = WebFramework.FASTAPI
    )

    companion object {
        fun detectProjectContext(projectPath: String): ProjectContext {
            var responseStyle = ResponseStyle.STANDARD
            var responseManagerClass: String? = null
            var responseManagerImport: String? = null
            var baseResponseClass: String? = null
            var baseResponseImport: String? = null
            var usesAsync = false
            var ormType = "sqlalchemy"
            var framework = WebFramework.FASTAPI

            // 检测 Web 框架
            val requirementsFile = File(projectPath, "requirements.txt")
            val pyprojectFile = File(projectPath, "pyproject.toml")
            var requirementsContent = ""

            if (requirementsFile.exists()) {
                requirementsContent = requirementsFile.readText().lowercase()
            } else if (pyprojectFile.exists()) {
                requirementsContent = pyprojectFile.readText().lowercase()
            }

            framework = when {
                "fastapi" in requirementsContent -> WebFramework.FASTAPI
                "flask" in requirementsContent && "fastapi" !in requirementsContent -> WebFramework.FLASK
                "django" in requirementsContent && "fastapi" !in requirementsContent -> WebFramework.DJANGO_REST
                "sanic" in requirementsContent -> WebFramework.SANIC
                "starlette" in requirementsContent && "fastapi" !in requirementsContent -> WebFramework.STARLETTE
                else -> WebFramework.FASTAPI
            }

            // 检测响应封装
            val responseFiles = listOf(
                File(projectPath, "utils/response.py"),
                File(projectPath, "utils/result.py"),
                File(projectPath, "common/response.py"),
                File(projectPath, "core/response.py"),
                File(projectPath, "lib/response.py")
            )

            for (file in responseFiles) {
                if (file.exists()) {
                    val content = file.readText()
                    if ("ResponseManager" in content || "class.*Response" in content) {
                        responseStyle = ResponseStyle.CUSTOM_WRAPPER
                        responseManagerClass = "ResponseManager"
                        responseManagerImport = "utils.response"
                        if ("BaseResponse" in content) {
                            baseResponseClass = "BaseResponse"
                            baseResponseImport = "utils.response"
                        }
                        break
                    }
                }
            }

            // 检测是否使用异步
            val serviceFiles = File(projectPath, "services").walkTopDown()
                .filter { it.isFile && it.name.endsWith(".py") }
                .toList()

            for (file in serviceFiles) {
                val content = file.readText()
                if ("async def" in content || "await " in content) {
                    usesAsync = true
                    break
                }
            }

            // 检测 ORM 类型
            val modelFiles = File(projectPath, "models").walkTopDown()
                .filter { it.isFile && it.name.endsWith(".py") }
                .toList()

            for (file in modelFiles) {
                val content = file.readText()
                when {
                    "from sqlmodel" in content -> { ormType = "sqlmodel"; break }
                    "from tortoise" in content -> { ormType = "tortoise"; usesAsync = true; break }
                    "from peewee" in content -> { ormType = "peewee"; break }
                    "from django.db" in content -> { ormType = "django"; break }
                    "from piccolo" in content -> { ormType = "piccolo"; usesAsync = true; break }
                }
            }

            // 根据框架调整异步设置
            if (framework == WebFramework.SANIC || framework == WebFramework.STARLETTE) {
                usesAsync = true
            }

            return ProjectContext(
                responseStyle = responseStyle,
                responseManagerClass = responseManagerClass,
                responseManagerImport = responseManagerImport,
                baseResponseClass = baseResponseClass,
                baseResponseImport = baseResponseImport,
                usesAsync = usesAsync,
                ormType = ormType,
                framework = framework
            )
        }
    }

    private var context: ProjectContext = ProjectContext(ResponseStyle.STANDARD)

    fun setContext(ctx: ProjectContext) {
        context = ctx
    }

    fun generateModel(table: TableInfo): String {
        val imports = mutableSetOf<String>()
        imports.add("from sqlalchemy import Column")

        val saTypes = mutableSetOf<String>()
        table.columns.forEach { col ->
            saTypes.add(DatabaseConnector.sqlTypeToSQLAlchemy(col.sqlType, col.typeName))
        }
        if (saTypes.isNotEmpty()) {
            imports.add("from sqlalchemy import ${saTypes.joinToString(", ")}")
        }
        imports.add("from database import Base")

        val sb = StringBuilder()
        sb.appendLine(imports.sorted().joinToString("\n"))
        sb.appendLine()
        sb.appendLine()

        table.comment?.let { sb.appendLine("\"\"\"$it\"\"\"") }
        sb.appendLine("class ${table.className}(Base):")
        sb.appendLine("    __tablename__ = \"${table.name}\"")
        sb.appendLine()

        for (col in table.columns) {
            val saType = DatabaseConnector.sqlTypeToSQLAlchemy(col.sqlType, col.typeName)
            val parts = mutableListOf<String>()
            parts.add(saType)
            if (col.isPrimaryKey) parts.add("primary_key=True")
            if (!col.nullable && !col.isPrimaryKey) parts.add("nullable=False")
            col.defaultValue?.let { parts.add("default=$it") }
            col.comment?.let { parts.add("comment=\"$it\"") }
            sb.appendLine("    ${col.name} = Column(${parts.joinToString(", ")})")
        }

        return sb.toString()
    }

    fun generateSchema(table: TableInfo): String {
        val sb = StringBuilder()
        sb.appendLine("from pydantic import BaseModel")
        sb.appendLine("from typing import Optional, List")
        sb.appendLine("from datetime import datetime, date")
        sb.appendLine()
        sb.appendLine()

        val pyTypeMap = mapOf(
            Types.INTEGER to "int",
            Types.SMALLINT to "int",
            Types.TINYINT to "int",
            Types.BIGINT to "int",
            Types.FLOAT to "float",
            Types.REAL to "float",
            Types.DOUBLE to "float",
            Types.DECIMAL to "float",
            Types.NUMERIC to "float",
            Types.VARCHAR to "str",
            Types.CHAR to "str",
            Types.LONGVARCHAR to "str",
            Types.NVARCHAR to "str",
            Types.NCHAR to "str",
            Types.CLOB to "str",
            Types.NCLOB to "str",
            Types.BOOLEAN to "bool",
            Types.BIT to "bool",
            Types.DATE to "date",
            Types.TIMESTAMP to "datetime",
            Types.TIMESTAMP_WITH_TIMEZONE to "datetime",
            Types.TIME to "datetime",
            Types.BLOB to "bytes",
            Types.BINARY to "bytes",
            Types.VARBINARY to "bytes",
            Types.LONGVARBINARY to "bytes",
            Types.OTHER to "dict"
        )

        fun getPyType(col: ColumnInfo): String {
            return pyTypeMap[col.sqlType] ?: "str"
        }

        // Base schema
        sb.appendLine("class ${table.className}Base(BaseModel):")
        val nonPkColumns = table.columns.filter { !it.isPrimaryKey }
        if (nonPkColumns.isEmpty()) {
            sb.appendLine("    pass")
        } else {
            for (col in nonPkColumns) {
                val pyType = getPyType(col)
                if (col.nullable) {
                    sb.appendLine("    ${col.name}: Optional[$pyType] = None")
                } else {
                    sb.appendLine("    ${col.name}: $pyType")
                }
            }
        }
        sb.appendLine()

        // Create schema
        sb.appendLine("class ${table.className}Create(${table.className}Base):")
        sb.appendLine("    pass")
        sb.appendLine()

        // Update schema
        sb.appendLine("class ${table.className}Update(${table.className}Base):")
        val pk = table.primaryKey
        if (pk != null) {
            val pkPyType = getPyType(pk)
            sb.appendLine("    ${pk.name}: $pkPyType")
        }
        sb.appendLine()

        // InDB/Response schema
        val responseClassName = if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) "InDB" else "Response"
        sb.appendLine("class ${table.className}$responseClassName(${table.className}Base):")
        if (pk != null) {
            val pkPyType = getPyType(pk)
            sb.appendLine("    ${pk.name}: $pkPyType")
        }
        sb.appendLine()
        sb.appendLine("    class Config:")
        sb.appendLine("        from_attributes = True")

        return sb.toString()
    }

    fun generateService(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"
        val isAsync = context.usesAsync

        val sb = StringBuilder()
        sb.appendLine("from models.$snakeName import $className")
        sb.appendLine("from typing import List, Optional")

        if (isAsync) {
            sb.appendLine("from tortoise.exceptions import DoesNotExist")
        } else {
            sb.appendLine("from sqlalchemy.orm import Session")
        }
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("class ${className}Service:")
        sb.appendLine("    \"\"\"")
        table.comment?.let { sb.appendLine("    $it 服务类") } ?: sb.appendLine("    ${className} 服务类")
        sb.appendLine("    \"\"\"")
        sb.appendLine()

        val asyncKw = if (isAsync) "async " else ""
        val awaitKw = if (isAsync) "await " else ""

        // get_all
        sb.appendLine("    @staticmethod")
        sb.appendLine("    ${asyncKw}def get_all_${snakeName}s() -> List[$className]:")
        sb.appendLine("        \"\"\"获取所有${table.comment ?: className}\"\"\"")
        if (isAsync) {
            sb.appendLine("        items = ${awaitKw}$className.all()")
        } else {
            sb.appendLine("        # TODO: 实现获取所有${table.comment ?: className}")
            sb.appendLine("        pass")
        }
        sb.appendLine("        return items")
        sb.appendLine()

        // get_by_id
        sb.appendLine("    @staticmethod")
        sb.appendLine("    ${asyncKw}def get_${snakeName}_by_id($pkName: int) -> Optional[$className]:")
        sb.appendLine("        \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        if (isAsync) {
            sb.appendLine("        try:")
            sb.appendLine("            item = ${awaitKw}$className.get($pkName=$pkName)")
            sb.appendLine("            return item")
            sb.appendLine("        except DoesNotExist:")
            sb.appendLine("            return None")
        } else {
            sb.appendLine("        # TODO: 实现根据ID获取${table.comment ?: className}")
            sb.appendLine("        pass")
        }
        sb.appendLine()

        // create
        sb.appendLine("    @staticmethod")
        sb.appendLine("    ${asyncKw}def create_${snakeName}(item_data: dict) -> $className:")
        sb.appendLine("        \"\"\"创建${table.comment ?: className}\"\"\"")
        if (isAsync) {
            sb.appendLine("        item = ${awaitKw}$className.create(**item_data)")
            sb.appendLine("        return item")
        } else {
            sb.appendLine("        # TODO: 实现创建${table.comment ?: className}")
            sb.appendLine("        pass")
        }
        sb.appendLine()

        // update
        sb.appendLine("    @staticmethod")
        sb.appendLine("    ${asyncKw}def update_${snakeName}($pkName: int, update_data: dict) -> bool:")
        sb.appendLine("        \"\"\"更新${table.comment ?: className}\"\"\"")
        if (isAsync) {
            sb.appendLine("        try:")
            sb.appendLine("            item = ${awaitKw}$className.get($pkName=$pkName)")
            sb.appendLine("            ${awaitKw}item.update_from_dict(update_data).save()")
            sb.appendLine("            return True")
            sb.appendLine("        except DoesNotExist:")
            sb.appendLine("            return False")
        } else {
            sb.appendLine("        # TODO: 实现更新${table.comment ?: className}")
            sb.appendLine("        pass")
        }
        sb.appendLine()

        // delete
        sb.appendLine("    @staticmethod")
        sb.appendLine("    ${asyncKw}def delete_${snakeName}($pkName: int) -> bool:")
        sb.appendLine("        \"\"\"删除${table.comment ?: className}\"\"\"")
        if (isAsync) {
            sb.appendLine("        try:")
            sb.appendLine("            item = ${awaitKw}$className.get($pkName=$pkName)")
            sb.appendLine("            ${awaitKw}item.delete()")
            sb.appendLine("            return True")
            sb.appendLine("        except DoesNotExist:")
            sb.appendLine("            return False")
        } else {
            sb.appendLine("        # TODO: 实现删除${table.comment ?: className}")
            sb.appendLine("        pass")
        }

        return sb.toString()
    }

    fun generateRouter(table: TableInfo): String {
        return when (context.framework) {
            WebFramework.FASTAPI -> generateFastAPIRouter(table)
            WebFramework.FLASK -> generateFlaskRouter(table)
            WebFramework.DJANGO_REST -> generateDjangoRestRouter(table)
            WebFramework.SANIC -> generateSanicRouter(table)
            WebFramework.STARLETTE -> generateStarletteRouter(table)
        }
    }

    private fun generateFastAPIRouter(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"
        val isAsync = context.usesAsync
        val responseClassName = if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) "InDB" else "Response"

        val sb = StringBuilder()
        sb.appendLine("from fastapi import APIRouter")

        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER && context.responseManagerImport != null) {
            sb.appendLine("from ${context.responseManagerImport} import ${context.responseManagerClass}")
        } else {
            sb.appendLine("from fastapi import HTTPException")
        }

        sb.appendLine("from services.${snakeName}_service import ${className}Service")
        sb.appendLine("from schemas.${snakeName} import ${className}Create, ${className}Update, ${className}$responseClassName")
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("router = APIRouter(prefix=\"/v1/$snakeName\", tags=[\"${table.comment ?: className}\"])")
        sb.appendLine()

        val asyncKw = if (isAsync) "async " else ""
        val awaitKw = if (isAsync) "await " else ""

        // Create
        sb.appendLine("@router.post(\"/create\")")
        sb.appendLine("${asyncKw}def create_${snakeName}(item: ${className}Create):")
        sb.appendLine("    \"\"\"创建${table.comment ?: className}\"\"\"")
        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) {
            sb.appendLine("    try:")
            sb.appendLine("        item_data = item.model_dump()")
            sb.appendLine("        service_item = ${awaitKw}${className}Service.create_${snakeName}(item_data)")
            sb.appendLine("        return ${context.responseManagerClass}.success(")
            sb.appendLine("            data=${className}$responseClassName(**service_item.to_dict()),")
            sb.appendLine("            msg=\"创建${table.comment ?: className}成功\"")
            sb.appendLine("        )")
            sb.appendLine("    except Exception as e:")
            sb.appendLine("        return ${context.responseManagerClass}.error(")
            sb.appendLine("            msg=f\"创建${table.comment ?: className}失败: {str(e)}\",")
            sb.appendLine("            status_code=400")
            sb.appendLine("        )")
        } else {
            sb.appendLine("    try:")
            sb.appendLine("        item_data = item.model_dump()")
            sb.appendLine("        service_item = ${awaitKw}${className}Service.create_${snakeName}(item_data)")
            sb.appendLine("        return ${className}$responseClassName(**service_item.to_dict())")
            sb.appendLine("    except Exception as e:")
            sb.appendLine("        raise HTTPException(status_code=400, detail=f\"创建${table.comment ?: className}失败: {str(e)}\")")
        }
        sb.appendLine()

        // Query by ID
        sb.appendLine("@router.post(\"/queryById\")")
        sb.appendLine("${asyncKw}def get_${snakeName}_by_id($pkName: int):")
        sb.appendLine("    \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) {
            sb.appendLine("    item = ${awaitKw}${className}Service.get_${snakeName}_by_id($pkName)")
            sb.appendLine("    if not item:")
            sb.appendLine("        return ${context.responseManagerClass}.not_found(msg=\"${table.comment ?: className}不存在\")")
            sb.appendLine("    return ${context.responseManagerClass}.success(")
            sb.appendLine("        data=${className}$responseClassName(**item.to_dict()),")
            sb.appendLine("        msg=\"查询成功\"")
            sb.appendLine("    )")
        } else {
            sb.appendLine("    item = ${awaitKw}${className}Service.get_${snakeName}_by_id($pkName)")
            sb.appendLine("    if not item:")
            sb.appendLine("        raise HTTPException(status_code=404, detail=\"${table.comment ?: className}不存在\")")
            sb.appendLine("    return ${className}$responseClassName(**item.to_dict())")
        }
        sb.appendLine()

        // Query all
        sb.appendLine("@router.post(\"/queryAll\")")
        sb.appendLine("${asyncKw}def get_all_${snakeName}s():")
        sb.appendLine("    \"\"\"获取所有${table.comment ?: className}\"\"\"")
        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) {
            sb.appendLine("    items = ${awaitKw}${className}Service.get_all_${snakeName}s()")
            sb.appendLine("    return ${context.responseManagerClass}.success(")
            sb.appendLine("        data=[${className}$responseClassName(**item.to_dict()) for item in items],")
            sb.appendLine("        msg=\"查询成功\"")
            sb.appendLine("    )")
        } else {
            sb.appendLine("    items = ${awaitKw}${className}Service.get_all_${snakeName}s()")
            sb.appendLine("    return [${className}$responseClassName(**item.to_dict()) for item in items]")
        }
        sb.appendLine()

        // Update
        sb.appendLine("@router.post(\"/update\")")
        sb.appendLine("${asyncKw}def update_${snakeName}(item: ${className}Update):")
        sb.appendLine("    \"\"\"更新${table.comment ?: className}\"\"\"")
        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) {
            sb.appendLine("    try:")
            sb.appendLine("        item_id = item.${pkName}")
            sb.appendLine("        if item_id is None:")
            sb.appendLine("            return ${context.responseManagerClass}.error(msg=\"${table.comment ?: className}ID不能为空\", status_code=400)")
            sb.appendLine("        update_data = item.model_dump(exclude_unset=True)")
            sb.appendLine("        update_data.pop('${pkName}', None)")
            sb.appendLine("        success = ${awaitKw}${className}Service.update_${snakeName}(item_id, update_data)")
            sb.appendLine("        if not success:")
            sb.appendLine("            return ${context.responseManagerClass}.not_found(msg=\"${table.comment ?: className}不存在\")")
            sb.appendLine("        service_item = ${awaitKw}${className}Service.get_${snakeName}_by_id(item_id)")
            sb.appendLine("        return ${context.responseManagerClass}.success(")
            sb.appendLine("            data=${className}$responseClassName(**service_item.to_dict()),")
            sb.appendLine("            msg=\"更新成功\"")
            sb.appendLine("        )")
            sb.appendLine("    except Exception as e:")
            sb.appendLine("        return ${context.responseManagerClass}.error(")
            sb.appendLine("            msg=f\"更新${table.comment ?: className}失败: {str(e)}\",")
            sb.appendLine("            status_code=400")
            sb.appendLine("        )")
        } else {
            sb.appendLine("    try:")
            sb.appendLine("        item_id = item.${pkName}")
            sb.appendLine("        if item_id is None:")
            sb.appendLine("            raise HTTPException(status_code=400, detail=\"${table.comment ?: className}ID不能为空\")")
            sb.appendLine("        update_data = item.model_dump(exclude_unset=True)")
            sb.appendLine("        update_data.pop('${pkName}', None)")
            sb.appendLine("        success = ${awaitKw}${className}Service.update_${snakeName}(item_id, update_data)")
            sb.appendLine("        if not success:")
            sb.appendLine("            raise HTTPException(status_code=404, detail=\"${table.comment ?: className}不存在\")")
            sb.appendLine("        service_item = ${awaitKw}${className}Service.get_${snakeName}_by_id(item_id)")
            sb.appendLine("        return ${className}$responseClassName(**service_item.to_dict())")
            sb.appendLine("    except Exception as e:")
            sb.appendLine("        raise HTTPException(status_code=400, detail=f\"更新${table.comment ?: className}失败: {str(e)}\")")
        }
        sb.appendLine()

        // Delete
        sb.appendLine("@router.post(\"/delete\")")
        sb.appendLine("${asyncKw}def delete_${snakeName}($pkName: int):")
        sb.appendLine("    \"\"\"删除${table.comment ?: className}\"\"\"")
        if (context.responseStyle == ResponseStyle.CUSTOM_WRAPPER) {
            sb.appendLine("    success = ${awaitKw}${className}Service.delete_${snakeName}($pkName)")
            sb.appendLine("    if not success:")
            sb.appendLine("        return ${context.responseManagerClass}.not_found(msg=\"${table.comment ?: className}不存在\")")
            sb.appendLine("    return ${context.responseManagerClass}.success(msg=\"删除成功\")")
        } else {
            sb.appendLine("    success = ${awaitKw}${className}Service.delete_${snakeName}($pkName)")
            sb.appendLine("    if not success:")
            sb.appendLine("        raise HTTPException(status_code=404, detail=\"${table.comment ?: className}不存在\")")
            sb.appendLine("    return {\"message\": \"删除成功\"}")
        }

        return sb.toString()
    }

    private fun generateFlaskRouter(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"

        val sb = StringBuilder()
        sb.appendLine("from flask import Blueprint, request, jsonify")
        sb.appendLine("from services.${snakeName}_service import ${className}Service")
        sb.appendLine("from schemas.${snakeName} import ${className}Create, ${className}Update, ${className}Response")
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("${snakeName}_bp = Blueprint('$snakeName', __name__, url_prefix='/v1/$snakeName')")
        sb.appendLine()

        // Create
        sb.appendLine("@${snakeName}_bp.route('/create', methods=['POST'])")
        sb.appendLine("def create_${snakeName}():")
        sb.appendLine("    \"\"\"创建${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = request.get_json()")
        sb.appendLine("        service_item = ${className}Service.create_${snakeName}(item_data)")
        sb.appendLine("        return jsonify({\"code\": 10000, \"msg\": \"创建${table.comment ?: className}成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()}), 200")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return jsonify({\"code\": -1, \"msg\": f\"创建${table.comment ?: className}失败: {str(e)}\"}), 400")
        sb.appendLine()

        // Query by ID
        sb.appendLine("@${snakeName}_bp.route('/queryById', methods=['POST'])")
        sb.appendLine("def get_${snakeName}_by_id():")
        sb.appendLine("    \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        sb.appendLine("    data = request.get_json()")
        sb.appendLine("    $pkName = data.get('$pkName')")
        sb.appendLine("    item = ${className}Service.get_${snakeName}_by_id($pkName)")
        sb.appendLine("    if not item:")
        sb.appendLine("        return jsonify({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}), 200")
        sb.appendLine("    return jsonify({\"code\": 10000, \"msg\": \"查询成功\", \"data\": ${className}Response(**item.to_dict()).model_dump()}), 200")
        sb.appendLine()

        // Query all
        sb.appendLine("@${snakeName}_bp.route('/queryAll', methods=['POST'])")
        sb.appendLine("def get_all_${snakeName}s():")
        sb.appendLine("    \"\"\"获取所有${table.comment ?: className}\"\"\"")
        sb.appendLine("    items = ${className}Service.get_all_${snakeName}s()")
        sb.appendLine("    return jsonify({\"code\": 10000, \"msg\": \"查询成功\", \"data\": [${className}Response(**item.to_dict()).model_dump() for item in items]}), 200")
        sb.appendLine()

        // Update
        sb.appendLine("@${snakeName}_bp.route('/update', methods=['POST'])")
        sb.appendLine("def update_${snakeName}():")
        sb.appendLine("    \"\"\"更新${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = request.get_json()")
        sb.appendLine("        item_id = item_data.get('$pkName')")
        sb.appendLine("        if item_id is None:")
        sb.appendLine("            return jsonify({\"code\": -1, \"msg\": \"${table.comment ?: className}ID不能为空\"}), 400")
        sb.appendLine("        update_data = {k: v for k, v in item_data.items() if v is not None and k != '$pkName'}")
        sb.appendLine("        success = ${className}Service.update_${snakeName}(item_id, update_data)")
        sb.appendLine("        if not success:")
        sb.appendLine("            return jsonify({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}), 200")
        sb.appendLine("        service_item = ${className}Service.get_${snakeName}_by_id(item_id)")
        sb.appendLine("        return jsonify({\"code\": 10000, \"msg\": \"更新成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()}), 200")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return jsonify({\"code\": -1, \"msg\": f\"更新${table.comment ?: className}失败: {str(e)}\"}), 400")
        sb.appendLine()

        // Delete
        sb.appendLine("@${snakeName}_bp.route('/delete', methods=['POST'])")
        sb.appendLine("def delete_${snakeName}():")
        sb.appendLine("    \"\"\"删除${table.comment ?: className}\"\"\"")
        sb.appendLine("    data = request.get_json()")
        sb.appendLine("    $pkName = data.get('$pkName')")
        sb.appendLine("    success = ${className}Service.delete_${snakeName}($pkName)")
        sb.appendLine("    if not success:")
        sb.appendLine("        return jsonify({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}), 200")
        sb.appendLine("    return jsonify({\"code\": 10000, \"msg\": \"删除成功\"}), 200")

        return sb.toString()
    }

    private fun generateDjangoRestRouter(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"

        val sb = StringBuilder()
        sb.appendLine("from rest_framework import viewsets, status")
        sb.appendLine("from rest_framework.response import Response")
        sb.appendLine("from rest_framework.decorators import action")
        sb.appendLine("from services.${snakeName}_service import ${className}Service")
        sb.appendLine("from schemas.${snakeName} import ${className}Create, ${className}Update, ${className}Response")
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("class ${className}ViewSet(viewsets.ViewSet):")
        sb.appendLine("    \"\"\"${table.comment ?: className}视图集\"\"\"")
        sb.appendLine()

        // Create
        sb.appendLine("    def create(self, request):")
        sb.appendLine("        \"\"\"创建${table.comment ?: className}\"\"\"")
        sb.appendLine("        try:")
        sb.appendLine("            item_data = request.data")
        sb.appendLine("            service_item = ${className}Service.create_${snakeName}(item_data)")
        sb.appendLine("            return Response({\"code\": 10000, \"msg\": \"创建${table.comment ?: className}成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()}, status=status.HTTP_200_OK)")
        sb.appendLine("        except Exception as e:")
        sb.appendLine("            return Response({\"code\": -1, \"msg\": f\"创建${table.comment ?: className}失败: {str(e)}\"}, status=status.HTTP_400_BAD_REQUEST)")
        sb.appendLine()

        // Query by ID
        sb.appendLine("    @action(detail=False, methods=['post'])")
        sb.appendLine("    def queryById(self, request):")
        sb.appendLine("        \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        sb.appendLine("        $pkName = request.data.get('$pkName')")
        sb.appendLine("        item = ${className}Service.get_${snakeName}_by_id($pkName)")
        sb.appendLine("        if not item:")
        sb.appendLine("            return Response({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}, status=status.HTTP_200_OK)")
        sb.appendLine("        return Response({\"code\": 10000, \"msg\": \"查询成功\", \"data\": ${className}Response(**item.to_dict()).model_dump()}, status=status.HTTP_200_OK)")
        sb.appendLine()

        // Query all
        sb.appendLine("    @action(detail=False, methods=['post'])")
        sb.appendLine("    def queryAll(self, request):")
        sb.appendLine("        \"\"\"获取所有${table.comment ?: className}\"\"\"")
        sb.appendLine("        items = ${className}Service.get_all_${snakeName}s()")
        sb.appendLine("        return Response({\"code\": 10000, \"msg\": \"查询成功\", \"data\": [${className}Response(**item.to_dict()).model_dump() for item in items]}, status=status.HTTP_200_OK)")
        sb.appendLine()

        // Update
        sb.appendLine("    @action(detail=False, methods=['post'])")
        sb.appendLine("    def update(self, request):")
        sb.appendLine("        \"\"\"更新${table.comment ?: className}\"\"\"")
        sb.appendLine("        try:")
        sb.appendLine("            item_data = request.data")
        sb.appendLine("            item_id = item_data.get('$pkName')")
        sb.appendLine("            if item_id is None:")
        sb.appendLine("                return Response({\"code\": -1, \"msg\": \"${table.comment ?: className}ID不能为空\"}, status=status.HTTP_400_BAD_REQUEST)")
        sb.appendLine("            update_data = {k: v for k, v in item_data.items() if v is not None and k != '$pkName'}")
        sb.appendLine("            success = ${className}Service.update_${snakeName}(item_id, update_data)")
        sb.appendLine("            if not success:")
        sb.appendLine("                return Response({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}, status=status.HTTP_200_OK)")
        sb.appendLine("            service_item = ${className}Service.get_${snakeName}_by_id(item_id)")
        sb.appendLine("            return Response({\"code\": 10000, \"msg\": \"更新成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()}, status=status.HTTP_200_OK)")
        sb.appendLine("        except Exception as e:")
        sb.appendLine("            return Response({\"code\": -1, \"msg\": f\"更新${table.comment ?: className}失败: {str(e)}\"}, status=status.HTTP_400_BAD_REQUEST)")
        sb.appendLine()

        // Delete
        sb.appendLine("    @action(detail=False, methods=['post'])")
        sb.appendLine("    def delete(self, request):")
        sb.appendLine("        \"\"\"删除${table.comment ?: className}\"\"\"")
        sb.appendLine("        $pkName = request.data.get('$pkName')")
        sb.appendLine("        success = ${className}Service.delete_${snakeName}($pkName)")
        sb.appendLine("        if not success:")
        sb.appendLine("            return Response({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"}, status=status.HTTP_200_OK)")
        sb.appendLine("        return Response({\"code\": 10000, \"msg\": \"删除成功\"}, status=status.HTTP_200_OK)")

        return sb.toString()
    }

    private fun generateSanicRouter(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"

        val sb = StringBuilder()
        sb.appendLine("from sanic import Blueprint, json")
        sb.appendLine("from services.${snakeName}_service import ${className}Service")
        sb.appendLine("from schemas.${snakeName} import ${className}Create, ${className}Update, ${className}Response")
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("${snakeName}_bp = Blueprint('$snakeName', url_prefix='/v1/$snakeName')")
        sb.appendLine()

        // Create
        sb.appendLine("@${snakeName}_bp.post('/create')")
        sb.appendLine("async def create_${snakeName}(request):")
        sb.appendLine("    \"\"\"创建${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = request.json")
        sb.appendLine("        service_item = await ${className}Service.create_${snakeName}(item_data)")
        sb.appendLine("        return json({\"code\": 10000, \"msg\": \"创建${table.comment ?: className}成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()})")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return json({\"code\": -1, \"msg\": f\"创建${table.comment ?: className}失败: {str(e)}\"}, status=400)")
        sb.appendLine()

        // Query by ID
        sb.appendLine("@${snakeName}_bp.post('/queryById')")
        sb.appendLine("async def get_${snakeName}_by_id(request):")
        sb.appendLine("    \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        sb.appendLine("    $pkName = request.json.get('$pkName')")
        sb.appendLine("    item = await ${className}Service.get_${snakeName}_by_id($pkName)")
        sb.appendLine("    if not item:")
        sb.appendLine("        return json({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("    return json({\"code\": 10000, \"msg\": \"查询成功\", \"data\": ${className}Response(**item.to_dict()).model_dump()})")
        sb.appendLine()

        // Query all
        sb.appendLine("@${snakeName}_bp.post('/queryAll')")
        sb.appendLine("async def get_all_${snakeName}s(request):")
        sb.appendLine("    \"\"\"获取所有${table.comment ?: className}\"\"\"")
        sb.appendLine("    items = await ${className}Service.get_all_${snakeName}s()")
        sb.appendLine("    return json({\"code\": 10000, \"msg\": \"查询成功\", \"data\": [${className}Response(**item.to_dict()).model_dump() for item in items]})")
        sb.appendLine()

        // Update
        sb.appendLine("@${snakeName}_bp.post('/update')")
        sb.appendLine("async def update_${snakeName}(request):")
        sb.appendLine("    \"\"\"更新${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = request.json")
        sb.appendLine("        item_id = item_data.get('$pkName')")
        sb.appendLine("        if item_id is None:")
        sb.appendLine("            return json({\"code\": -1, \"msg\": \"${table.comment ?: className}ID不能为空\"}, status=400)")
        sb.appendLine("        update_data = {k: v for k, v in item_data.items() if v is not None and k != '$pkName'}")
        sb.appendLine("        success = await ${className}Service.update_${snakeName}(item_id, update_data)")
        sb.appendLine("        if not success:")
        sb.appendLine("            return json({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("        service_item = await ${className}Service.get_${snakeName}_by_id(item_id)")
        sb.appendLine("        return json({\"code\": 10000, \"msg\": \"更新成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()})")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return json({\"code\": -1, \"msg\": f\"更新${table.comment ?: className}失败: {str(e)}\"}, status=400)")
        sb.appendLine()

        // Delete
        sb.appendLine("@${snakeName}_bp.post('/delete')")
        sb.appendLine("async def delete_${snakeName}(request):")
        sb.appendLine("    \"\"\"删除${table.comment ?: className}\"\"\"")
        sb.appendLine("    $pkName = request.json.get('$pkName')")
        sb.appendLine("    success = await ${className}Service.delete_${snakeName}($pkName)")
        sb.appendLine("    if not success:")
        sb.appendLine("        return json({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("    return json({\"code\": 10000, \"msg\": \"删除成功\"})")

        return sb.toString()
    }

    private fun generateStarletteRouter(table: TableInfo): String {
        val className = table.className
        val snakeName = table.snakeName
        val pk = table.primaryKey
        val pkName = pk?.name ?: "id"

        val sb = StringBuilder()
        sb.appendLine("from starlette.routing import Route")
        sb.appendLine("from starlette.responses import JSONResponse")
        sb.appendLine("from services.${snakeName}_service import ${className}Service")
        sb.appendLine("from schemas.${snakeName} import ${className}Create, ${className}Update, ${className}Response")
        sb.appendLine()
        sb.appendLine()

        // Create
        sb.appendLine("async def create_${snakeName}(request):")
        sb.appendLine("    \"\"\"创建${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = await request.json()")
        sb.appendLine("        service_item = await ${className}Service.create_${snakeName}(item_data)")
        sb.appendLine("        return JSONResponse({\"code\": 10000, \"msg\": \"创建${table.comment ?: className}成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()})")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return JSONResponse({\"code\": -1, \"msg\": f\"创建${table.comment ?: className}失败: {str(e)}\"}, status_code=400)")
        sb.appendLine()

        // Query by ID
        sb.appendLine("async def get_${snakeName}_by_id(request):")
        sb.appendLine("    \"\"\"根据ID获取${table.comment ?: className}\"\"\"")
        sb.appendLine("    data = await request.json()")
        sb.appendLine("    $pkName = data.get('$pkName')")
        sb.appendLine("    item = await ${className}Service.get_${snakeName}_by_id($pkName)")
        sb.appendLine("    if not item:")
        sb.appendLine("        return JSONResponse({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("    return JSONResponse({\"code\": 10000, \"msg\": \"查询成功\", \"data\": ${className}Response(**item.to_dict()).model_dump()})")
        sb.appendLine()

        // Query all
        sb.appendLine("async def get_all_${snakeName}s(request):")
        sb.appendLine("    \"\"\"获取所有${table.comment ?: className}\"\"\"")
        sb.appendLine("    items = await ${className}Service.get_all_${snakeName}s()")
        sb.appendLine("    return JSONResponse({\"code\": 10000, \"msg\": \"查询成功\", \"data\": [${className}Response(**item.to_dict()).model_dump() for item in items]})")
        sb.appendLine()

        // Update
        sb.appendLine("async def update_${snakeName}(request):")
        sb.appendLine("    \"\"\"更新${table.comment ?: className}\"\"\"")
        sb.appendLine("    try:")
        sb.appendLine("        item_data = await request.json()")
        sb.appendLine("        item_id = item_data.get('$pkName')")
        sb.appendLine("        if item_id is None:")
        sb.appendLine("            return JSONResponse({\"code\": -1, \"msg\": \"${table.comment ?: className}ID不能为空\"}, status_code=400)")
        sb.appendLine("        update_data = {k: v for k, v in item_data.items() if v is not None and k != '$pkName'}")
        sb.appendLine("        success = await ${className}Service.update_${snakeName}(item_id, update_data)")
        sb.appendLine("        if not success:")
        sb.appendLine("            return JSONResponse({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("        service_item = await ${className}Service.get_${snakeName}_by_id(item_id)")
        sb.appendLine("        return JSONResponse({\"code\": 10000, \"msg\": \"更新成功\", \"data\": ${className}Response(**service_item.to_dict()).model_dump()})")
        sb.appendLine("    except Exception as e:")
        sb.appendLine("        return JSONResponse({\"code\": -1, \"msg\": f\"更新${table.comment ?: className}失败: {str(e)}\"}, status_code=400)")
        sb.appendLine()

        // Delete
        sb.appendLine("async def delete_${snakeName}(request):")
        sb.appendLine("    \"\"\"删除${table.comment ?: className}\"\"\"")
        sb.appendLine("    data = await request.json()")
        sb.appendLine("    $pkName = data.get('$pkName')")
        sb.appendLine("    success = await ${className}Service.delete_${snakeName}($pkName)")
        sb.appendLine("    if not success:")
        sb.appendLine("        return JSONResponse({\"code\": 60021, \"msg\": \"${table.comment ?: className}不存在\"})")
        sb.appendLine("    return JSONResponse({\"code\": 10000, \"msg\": \"删除成功\"})")
        sb.appendLine()

        // Routes
        sb.appendLine("routes = [")
        sb.appendLine("    Route('/create', create_${snakeName}, methods=['POST']),")
        sb.appendLine("    Route('/queryById', get_${snakeName}_by_id, methods=['POST']),")
        sb.appendLine("    Route('/queryAll', get_all_${snakeName}s, methods=['POST']),")
        sb.appendLine("    Route('/update', update_${snakeName}, methods=['POST']),")
        sb.appendLine("    Route('/delete', delete_${snakeName}, methods=['POST']),")
        sb.appendLine("]")

        return sb.toString()
    }
}
