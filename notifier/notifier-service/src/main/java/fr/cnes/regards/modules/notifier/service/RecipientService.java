/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.notifier.service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.modules.notifier.dao.IRuleRepository;
import fr.cnes.regards.modules.notifier.domain.Rule;
import fr.cnes.regards.modules.notifier.plugin.IRecipientNotifier;

/**
 * Implementation of recipient service
 * @author Kevin Marchois
 *
 */
@Service
@MultitenantTransactional
public class RecipientService implements IRecipientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientService.class);

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IRuleRepository ruleRepo;

    @Override
    public Set<PluginConfiguration> getRecipients() {
        return getRecipients(null);
    }

    @Override
    public Set<PluginConfiguration> getRecipients(Collection<String> businessIds) {
        Set<PluginConfiguration> recipients = Sets.newHashSet();
        if ((businessIds == null) || businessIds.isEmpty()) {
            recipients.addAll(pluginService.getPluginConfigurationsByType(IRecipientNotifier.class));
        } else {
            recipients = businessIds.stream().map((id) -> {
                try {
                    return pluginService.getPluginConfiguration(id);
                } catch (EntityNotFoundException e) {
                    return null;
                }
            }).filter(conf -> conf != null).collect(Collectors.toSet());
        }
        return recipients;
    }

    @Override
    public PluginConfiguration createOrUpdateRecipient(@Valid PluginConfiguration recipientPluginConf)
            throws ModuleException {
        if (recipientPluginConf.getId() == null) {
            return pluginService.savePluginConfiguration(recipientPluginConf);
        } else {
            return pluginService.updatePluginConfiguration(recipientPluginConf);
        }
    }

    @Override
    public void deleteRecipient(String id) throws ModuleException {
        // Check  if a rule is associated to the recipient first
        for (Rule rule : ruleRepo.findByRecipientsBusinessId(id)) {
            // Remove  recipient to delete
            rule.setRecipients(rule.getRecipients().stream().filter(c -> !c.getBusinessId().equals(id))
                    .collect(Collectors.toSet()));
        }
        pluginService.deletePluginConfiguration(id);
    }

    @Override
    public void deleteAll(Collection<String> deletionErrors) {
        for (PluginConfiguration conf : pluginService.getPluginConfigurationsByType(IRecipientNotifier.class)) {
            try {
                pluginService.deletePluginConfiguration(conf.getBusinessId());
            } catch (ModuleException e) {
                deletionErrors.add(String.format("Error deleting rule configuration %s : %s", conf, e.getMessage()));
                LOGGER.error(e.getMessage(), e);
            }
        }

    }
}
