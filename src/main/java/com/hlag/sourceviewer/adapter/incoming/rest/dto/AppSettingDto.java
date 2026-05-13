package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * DTO representing a single application setting.
 *
 * @param key         the unique setting key (e.g. {@code "scan.max-parallel-jobs"})
 * @param value       the current value as a string; masked as {@code "****"} for secret settings
 * @param description human-readable description of what the setting controls
 * @param secret      {@code true} when this setting holds a credential that is masked in responses
 */
public record AppSettingDto(String key, String value, String description, boolean secret) {
}
