package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;
import com.hlag.sourceviewer.domain.port.outgoing.SettingsRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSettingsRepository
        implements SettingsRepository, PanacheRepositoryBase<AppSetting, String> {

    @Override
    public Optional<AppSetting> findByKey(String key) {
        return findByIdOptional(key);
    }

    @Override
    public List<AppSetting> findAllSettings() {
        return listAll();
    }

    @Override
    @Transactional
    public void upsert(String key, String value) {
        var existing = findByIdOptional(key);
        if (existing.isPresent()) {
            existing.get().setValue(value);
        } else {
            persist(new AppSetting(key, value));
        }
    }
}
