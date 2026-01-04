package com.vernont.api

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// Instant -> OffsetDateTime (default UTC)
fun Instant.toOffsetDateTime(zone: ZoneId = ZoneOffset.UTC): OffsetDateTime =
    OffsetDateTime.ofInstant(this, zone)

// OffsetDateTime -> Instant
fun OffsetDateTime.toInstantUtc(): Instant =
    this.toInstant()
