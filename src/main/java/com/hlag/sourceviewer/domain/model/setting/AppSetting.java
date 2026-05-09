package com.hlag.sourceviewer.domain.model.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A runtime-configurable application setting stored in the database.
 *
 * <p>Settings are simple key-value pairs. All values are stored as strings;
 * the application interprets them into the appropriate type (int, boolean, etc.).</p>
 */
@Entity
@Table(name = "setting")
public class AppSetting {

    @Id
    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    protected AppSetting() {}

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String key() { return key; }
    public String value() { return value; }

    public void setValue(String value) { this.value = value; }
}
