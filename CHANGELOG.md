# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-13

### Added

- Initial release
- Multi-framework support: FastAPI, Flask, Django REST Framework, Sanic, Starlette
- Multi-ORM support: SQLAlchemy, SQLModel, Tortoise ORM, Peewee, Django ORM, Piccolo
- Automatic project context detection (ORM type, framework, response patterns)
- ToolWindow-based UI with granular generation target selection
- Database schema sync (MySQL, PostgreSQL)
- Python model file parsing
- i18n support (Chinese/English auto-detection)
- Custom response pattern detection and adaptation
- Relative path display for model files
- Left-aligned UI components for better readability
- Async/sync code generation based on project detection
