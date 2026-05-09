package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * DTO representing a single application setting.
 *
 * @param key         the unique setting key (e.g. {@code "scan.max-parallel-jobs"})
 * @param value       the current value as a string
 * @param description human-readable description of what the setting controls
 */
public record AppSettingDto(String key, String value, String description) {
}
