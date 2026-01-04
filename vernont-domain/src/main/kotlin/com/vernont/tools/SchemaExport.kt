package com.vernont.tools

import jakarta.persistence.Entity
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import java.nio.file.Files
import java.nio.file.Path

object SchemaExporterTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val outputFile = args.getOrNull(0)
            ?: "nexus-api/src/main/resources/db/migration/V1__baseline.sql"
        val dialect = args.getOrNull(1) ?: "org.hibernate.dialect.PostgreSQLDialect"

        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        }

        val outputPath = Path.of(outputFile)
        val tempPath = Files.createTempFile("schema-export", ".sql")

        val registry = StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.DIALECT, dialect)
            .applySetting(AvailableSettings.HBM2DDL_DELIMITER, ";")
            .applySetting(AvailableSettings.FORMAT_SQL, "true")
            .build()

        val metadataSources = MetadataSources(registry)
        scanner.findCandidateComponents("com.vernont.domain").forEach { bean ->
            val className = bean.beanClassName ?: return@forEach
            metadataSources.addAnnotatedClass(Class.forName(className))
        }

        val metadata = metadataSources.buildMetadata()

        val commands = org.hibernate.tool.schema.internal.SchemaCreatorImpl(registry)
            .generateCreationCommands(metadata, true)
            .joinToString(";\n", postfix = ";\n")
        Files.writeString(tempPath, commands)

        val rendered = postProcessSchema(tempPath)
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, rendered)
    }

    private fun postProcessSchema(tempPath: Path): String {
        val statements = Files.readString(tempPath)
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val output = StringBuilder()
        for (stmt in statements) {
            val normalized = stmt.replace(Regex("\\s+"), " ").trim()
            when {
                normalized.startsWith("drop ", ignoreCase = true) -> {
                    continue
                }
                normalized.startsWith("create table ", ignoreCase = true) -> {
                    output.append(stmt.replaceFirst(Regex("(?i)create table"), "CREATE TABLE IF NOT EXISTS"))
                        .append(";\n")
                }
                normalized.startsWith("create index ", ignoreCase = true) -> {
                    output.append(stmt.replaceFirst(Regex("(?i)create index"), "CREATE INDEX IF NOT EXISTS"))
                        .append(";\n")
                }
                normalized.startsWith("create sequence ", ignoreCase = true) -> {
                    output.append(stmt.replaceFirst(Regex("(?i)create sequence"), "CREATE SEQUENCE IF NOT EXISTS"))
                        .append(";\n")
                }
                normalized.startsWith("alter table ", ignoreCase = true) &&
                    normalized.contains(" add constraint ", ignoreCase = true) -> {
                    val constraintName = Regex("(?i)add constraint ([^ ]+)").find(normalized)?.groupValues?.get(1)
                    if (constraintName != null) {
                        output.append(
                            """
                            DO $$
                            BEGIN
                                IF NOT EXISTS (
                                    SELECT 1 FROM pg_constraint WHERE conname = '${constraintName}'
                                ) THEN
                                    $stmt;
                                END IF;
                            END $$;
                            """.trimIndent()
                        ).append("\n")
                    } else {
                        output.append(stmt).append(";\n")
                    }
                }
                else -> {
                    output.append(stmt).append(";\n")
                }
            }
        }
        return output.toString()
    }
}
