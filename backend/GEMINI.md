# Roamly Project Mandates

This file establishes the foundational rules for all AI agents working on the Roamly project.

## Core Rules
1. **Documentation First:** Before performing any task, read the relevant documents in `document/`.
2. **Context Awareness:** Consult `AGENTS.MD` at the project root for the current project state and context.
3. **Spring Boot Excellence:** Adhere to Spring Boot 3+ and Java 21 best practices. Use constructor injection and record-based DTOs.
4. **Vertical Slicing:** Implement features as complete vertical slices (end-to-end functionality) rather than horizontal layers.

## Configuration
- ECC rules are located in `.gemini/rules/`.
- Project-level skills are located in `.gemini/skills/`.
