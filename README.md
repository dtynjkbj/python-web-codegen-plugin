# Python Web CodeGen Plugin

[English](#english) | [中文](#中文)

---

## English

A powerful PyCharm plugin that generates CRUD code for **multiple Python web frameworks** from database schemas or existing Python models.

### Supported Frameworks

- **FastAPI** - Modern async framework with automatic OpenAPI docs
- **Flask** - Lightweight and flexible microframework
- **Django REST Framework** - Powerful toolkit for building Web APIs
- **Sanic** - Async-first framework for high performance
- **Starlette** - Lightweight ASGI framework

### Supported ORMs

- **SQLAlchemy** - The most popular Python ORM
- **SQLModel** - SQL databases in Python, designed for simplicity and compatibility with FastAPI
- **Tortoise ORM** - Easy-to-use asyncio ORM inspired by Django
- **Peewee** - Simple and expressive ORM for Python
- **Django ORM** - Django's built-in ORM
- **Piccolo** - A modern, high performance ORM and query builder

### Features

- **Multi-Framework Support**: Generate code for FastAPI, Flask, Django REST, Sanic, or Starlette
- **Multi-ORM Support**: Parse and generate models for 6 different ORM types
- **Smart Detection**: Automatically detects your project's ORM, framework, and response patterns
- **Granular Generation**: Choose what to generate - Models, Schemas, Services, or Routers
- **Database Sync**: Sync database schema directly to Python models
- **i18n Support**: Automatic Chinese/English detection based on PyCharm language
- **Custom Response Patterns**: Detects and adapts to your project's response wrapper style
- **Async Support**: Detects and generates async or sync code based on your project

### Installation

1. Download the latest release from [Releases](https://github.com/yourusername/python-web-codegen-plugin/releases)
2. Open PyCharm → Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select the downloaded `.zip` file
4. Restart PyCharm

### Usage

1. Open your Python project in PyCharm
2. Go to **Tools → 生成 Python Web 代码** or open the **Python Web CodeGen** tool window (right sidebar)
3. Select generation mode:
   - **From Python Models**: Parse existing `.py` model files
   - **From Database**: Connect to database and generate from schema
4. Select target tables/models
5. Choose what to generate (Models/Schemas/Services/Routers)
6. Click **Generate**

The plugin will automatically detect:
- Your project's framework (FastAPI/Flask/Django REST/Sanic/Starlette)
- Your ORM type (SQLAlchemy/Tortoise/etc.)
- Your response pattern (custom wrapper or standard)
- Whether your project uses async

### Generated Code Structure

```
your_project/
├── models/          # ORM models (SQLAlchemy/Tortoise/Peewee/etc.)
├── schemas/         # Pydantic schemas (Base/Create/Update/Response)
├── services/        # Business logic layer
└── routers/         # API routes/endpoints
```

### Framework-Specific Features

#### FastAPI
- Async/sync endpoint generation
- Dependency injection with `Depends()`
- Automatic response models
- OpenAPI-compatible schemas

#### Flask
- Blueprint-based routing
- Request/response handling
- SQLAlchemy session management

#### Django REST Framework
- Serializer generation
- ViewSet/APIView patterns
- QuerySet optimization

#### Sanic
- Async endpoint generation
- Blueprint support
- Request/response handling

#### Starlette
- ASGI endpoint generation
- Route definitions
- Request/response handling

### Supported Databases

- MySQL
- PostgreSQL

### Requirements

- PyCharm 2024.1 or later
- Java 21+

### Building from Source

```bash
./gradlew buildPlugin
```

The plugin zip will be in `build/distributions/`

---

## 中文

一个强大的 PyCharm 插件，可以从数据库表结构或现有 Python 模型文件为**多个 Python Web 框架**生成 CRUD 代码。

### 支持的框架

- **FastAPI** - 现代异步框架，自动生成 OpenAPI 文档
- **Flask** - 轻量灵活的微框架
- **Django REST Framework** - 强大的 Web API 构建工具包
- **Sanic** - 异步优先的高性能框架
- **Starlette** - 轻量级 ASGI 框架

### 支持的 ORM

- **SQLAlchemy** - 最流行的 Python ORM
- **SQLModel** - 简单易用，与 FastAPI 完美兼容
- **Tortoise ORM** - 受 Django 启发的异步 ORM
- **Peewee** - 简单表达式的 ORM
- **Django ORM** - Django 内置 ORM
- **Piccolo** - 现代化高性能 ORM 和查询构建器

### 功能特性

- **多框架支持**：为 FastAPI、Flask、Django REST、Sanic 或 Starlette 生成代码
- **多 ORM 支持**：解析和生成 6 种不同 ORM 类型的模型
- **智能检测**：自动检测项目的 ORM、框架和响应模式
- **细粒度生成**：选择生成内容 - 模型、模式、服务或路由
- **数据库同步**：直接从数据库表结构同步到 Python 模型
- **国际化支持**：根据 PyCharm 语言自动检测中文/英文
- **自定义响应模式**：检测并适配项目的响应封装风格
- **异步支持**：根据项目检测并生成异步或同步代码

### 安装方法

1. 从 [Releases](https://github.com/yourusername/python-web-codegen-plugin/releases) 下载最新版本
2. 打开 PyCharm → 设置 → 插件 → ⚙️ → 从磁盘安装插件
3. 选择下载的 `.zip` 文件
4. 重启 PyCharm

### 使用方法

1. 在 PyCharm 中打开 Python 项目
2. 进入 **工具 → 生成 Python Web 代码** 或打开 **Python Web CodeGen** 工具窗口（右侧边栏）
3. 选择生成模式：
   - **从 Python 模型**：解析现有的 `.py` 模型文件
   - **从数据库**：连接数据库并从表结构生成
4. 选择目标表/模型
5. 选择要生成的内容（模型/模式/服务/路由）
6. 点击 **生成**

插件会自动检测：
- 你的项目框架（FastAPI/Flask/Django REST/Sanic/Starlette）
- 你的 ORM 类型（SQLAlchemy/Tortoise 等）
- 你的响应模式（自定义封装或标准）
- 你的项目是否使用异步

### 生成的代码结构

```
your_project/
├── models/          # ORM 模型（SQLAlchemy/Tortoise/Peewee 等）
├── schemas/         # Pydantic 模式（Base/Create/Update/Response）
├── services/        # 业务逻辑层
└── routers/         # API 路由/端点
```

### 框架特定功能

#### FastAPI
- 异步/同步端点生成
- 使用 `Depends()` 的依赖注入
- 自动响应模型
- OpenAPI 兼容的模式

#### Flask
- 基于 Blueprint 的路由
- 请求/响应处理
- SQLAlchemy 会话管理

#### Django REST Framework
- 序列化器生成
- ViewSet/APIView 模式
- QuerySet 优化

#### Sanic
- 异步端点生成
- Blueprint 支持
- 请求/响应处理

#### Starlette
- ASGI 端点生成
- 路由定义
- 请求/响应处理

### 支持的数据库

- MySQL
- PostgreSQL

### 系统要求

- PyCharm 2024.1 或更高版本
- Java 21+

### 从源码构建

```bash
./gradlew buildPlugin
```

插件 zip 文件将位于 `build/distributions/` 目录

---

## License

MIT License - see [LICENSE](LICENSE) file for details
