/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.bootstrap;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.Plugin;
import org.sonar.api.config.Settings;
import org.sonar.api.platform.PluginMetadata;
import org.sonar.api.platform.PluginRepository;
import org.sonar.api.utils.TempFolder;
import org.sonar.core.plugins.PluginClassloaders;
import org.sonar.core.plugins.PluginInstaller;
import org.sonar.core.plugins.RemotePlugin;

import java.io.File;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchPluginRepository implements PluginRepository {

  private static final Logger LOG = LoggerFactory.getLogger(BatchPluginRepository.class);
  private static final String CORE_PLUGIN = "core";
  private static final String ENGLISH_PACK_PLUGIN = "l10nen";

  private PluginDownloader pluginDownloader;
  private Map<String, Plugin> pluginsByKey;
  private Map<String, PluginMetadata> metadataByKey;
  private Settings settings;
  private PluginClassloaders classLoaders;
  private TempFolder tempDirectories;
  private final AnalysisMode analysisMode;

  public BatchPluginRepository(PluginDownloader pluginDownloader, TempFolder tempDirectories, Settings settings, AnalysisMode analysisMode) {
    this.pluginDownloader = pluginDownloader;
    this.tempDirectories = tempDirectories;
    this.settings = settings;
    this.analysisMode = analysisMode;
  }

  public void start() {
    LOG.info("Install plugins");
    doStart(pluginDownloader.downloadPluginIndex());
  }

  void doStart(List<RemotePlugin> remotePlugins) {
    PluginFilter filter = new PluginFilter(settings, analysisMode);
    PluginInstaller extractor = new PluginInstaller();
    metadataByKey = Maps.newHashMap();
    for (RemotePlugin remote : remotePlugins) {
      if (filter.accepts(remote.getKey())) {
        List<File> pluginFiles = pluginDownloader.downloadPlugin(remote);
        List<File> extensionFiles = pluginFiles.subList(1, pluginFiles.size());
        File targetDir = tempDirectories.newDir("plugins/" + remote.getKey());
        LOG.debug("Installing plugin {} into {}", remote.getKey(), targetDir.getAbsolutePath());
        PluginMetadata metadata = extractor.install(pluginFiles.get(0), remote.isCore(), extensionFiles, targetDir);
        if (StringUtils.isBlank(metadata.getBasePlugin()) || filter.accepts(metadata.getBasePlugin())) {
          metadataByKey.put(metadata.getKey(), metadata);
        } else {
          LOG.debug("Excluded plugin: " + metadata.getKey());
        }
      }
    }
    classLoaders = new PluginClassloaders(Thread.currentThread().getContextClassLoader());
    pluginsByKey = classLoaders.init(metadataByKey.values());
  }

  public void stop() {
    if (classLoaders != null) {
      classLoaders.clean();
      classLoaders = null;
    }
  }

  public Plugin getPlugin(String key) {
    return pluginsByKey.get(key);
  }

  public Collection<PluginMetadata> getMetadata() {
    return metadataByKey.values();
  }

  public PluginMetadata getMetadata(String pluginKey) {
    return metadataByKey.get(pluginKey);
  }

  public Map<PluginMetadata, Plugin> getPluginsByMetadata() {
    Map<PluginMetadata, Plugin> result = Maps.newHashMap();
    for (Map.Entry<String, PluginMetadata> entry : metadataByKey.entrySet()) {
      String pluginKey = entry.getKey();
      PluginMetadata metadata = entry.getValue();
      result.put(metadata, pluginsByKey.get(pluginKey));
    }
    return result;
  }

  static class PluginFilter {
    private static final String PROPERTY_IS_DEPRECATED_MSG = "Property {0} is deprecated. Please use {1} instead.";
    Set<String> whites = Sets.newHashSet(), blacks = Sets.newHashSet();

    PluginFilter(Settings settings, AnalysisMode mode) {
      if (settings.hasKey(CoreProperties.BATCH_INCLUDE_PLUGINS)) {
        whites.addAll(Arrays.asList(settings.getStringArray(CoreProperties.BATCH_INCLUDE_PLUGINS)));
      }
      if (settings.hasKey(CoreProperties.BATCH_EXCLUDE_PLUGINS)) {
        blacks.addAll(Arrays.asList(settings.getStringArray(CoreProperties.BATCH_EXCLUDE_PLUGINS)));
      }
      if (mode.isPreview()) {
        // These default values are not supported by Settings because the class CorePlugin
        // is not loaded yet.
        if (settings.hasKey(CoreProperties.DRY_RUN_INCLUDE_PLUGINS)) {
          LOG.warn(MessageFormat.format(PROPERTY_IS_DEPRECATED_MSG, CoreProperties.DRY_RUN_INCLUDE_PLUGINS, CoreProperties.PREVIEW_INCLUDE_PLUGINS));
          whites.addAll(propertyValues(settings,
            CoreProperties.DRY_RUN_INCLUDE_PLUGINS, CoreProperties.PREVIEW_INCLUDE_PLUGINS_DEFAULT_VALUE));
        } else {
          whites.addAll(propertyValues(settings,
            CoreProperties.PREVIEW_INCLUDE_PLUGINS, CoreProperties.PREVIEW_INCLUDE_PLUGINS_DEFAULT_VALUE));
        }
        if (settings.hasKey(CoreProperties.DRY_RUN_EXCLUDE_PLUGINS)) {
          LOG.warn(MessageFormat.format(PROPERTY_IS_DEPRECATED_MSG, CoreProperties.DRY_RUN_EXCLUDE_PLUGINS, CoreProperties.PREVIEW_EXCLUDE_PLUGINS));
          blacks.addAll(propertyValues(settings,
            CoreProperties.DRY_RUN_EXCLUDE_PLUGINS, CoreProperties.PREVIEW_EXCLUDE_PLUGINS_DEFAULT_VALUE));
        } else {
          blacks.addAll(propertyValues(settings,
            CoreProperties.PREVIEW_EXCLUDE_PLUGINS, CoreProperties.PREVIEW_EXCLUDE_PLUGINS_DEFAULT_VALUE));
        }
      }
      if (!whites.isEmpty()) {
        LOG.info("Include plugins: " + Joiner.on(", ").join(whites));
      }
      if (!blacks.isEmpty()) {
        LOG.info("Exclude plugins: " + Joiner.on(", ").join(blacks));
      }
    }

    static List<String> propertyValues(Settings settings, String key, String defaultValue) {
      String s = StringUtils.defaultIfEmpty(settings.getString(key), defaultValue);
      return Arrays.asList(StringUtils.split(s, ","));
    }

    boolean accepts(String pluginKey) {
      if (CORE_PLUGIN.equals(pluginKey) || ENGLISH_PACK_PLUGIN.equals(pluginKey)) {
        return true;
      }
      if (!whites.isEmpty()) {
        return whites.contains(pluginKey);
      }
      return blacks.isEmpty() || !blacks.contains(pluginKey);
    }
  }
}
