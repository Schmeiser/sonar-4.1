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

package org.sonar.server.rule;

import com.google.common.collect.Lists;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.io.BytesStream;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.sonar.api.database.DatabaseSession;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleParam;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.core.i18n.RuleI18nManager;
import org.sonar.jpa.session.DatabaseSessionFactory;
import org.sonar.server.search.SearchIndex;
import org.sonar.server.search.SearchQuery;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fill search index with rules
 * @since 4.1
 */
public class RuleRegistry {

  /**
   *
   */
  private static final String PARAM_NAMEORKEY = "nameOrKey";
  private static final String PARAM_STATUS = "status";
  private static final String INDEX_RULES = "rules";
  private static final String TYPE_RULE = "rule";

  private SearchIndex searchIndex;
  private DatabaseSessionFactory sessionFactory;
  private RuleI18nManager ruleI18nManager;

  public RuleRegistry(SearchIndex searchIndex, DatabaseSessionFactory sessionFactory, RuleI18nManager ruleI18nManager) {
    this.searchIndex = searchIndex;
    this.sessionFactory = sessionFactory;
    this.ruleI18nManager = ruleI18nManager;
  }

  public void start() {
    searchIndex.addMappingFromClasspath(INDEX_RULES, TYPE_RULE, "/com/sonar/search/rule_mapping.json");
  }

  public void bulkRegisterRules() {
    TimeProfiler profiler = new TimeProfiler();
    DatabaseSession session = sessionFactory.getSession();
    profiler.start("Rebuilding rules index");

    List<Rule> rules = session.getResults(Rule.class);

    try {
      bulkIndex(rules);
    } catch(IOException ioe) {
      throw new IllegalStateException("Unable to index rules", ioe);
    } finally {
      profiler.stop();
    }
  }

  /**
   * <p>Find rule IDs matching the given criteria.</p>
   * @param query <p>A collection of (optional) criteria with the following meaning:
   * <ul>
   *  <li><em>nameOrKey</em>: will be used as a query string over the "name" field</li>
   *  <li><em>&lt;anyField&gt;</em>: will be used to match the given field against the passed value(s);
   *  mutiple values must be separated by the '<code>|</code>' (vertical bar) character</li>
   * </ul>
   * </p>
   * @return
   */
  public List<Integer> findIds(Map<String, String> query) {
    Map<String, String> params = Maps.newHashMap(query);

    SearchQuery searchQuery = SearchQuery.create();
    searchQuery.index(INDEX_RULES).type(TYPE_RULE).scrollSize(500);

    if (params.containsKey(PARAM_NAMEORKEY)) {
      searchQuery.searchString(params.remove(PARAM_NAMEORKEY));
    }
    if (! params.containsKey(PARAM_STATUS)) {
      searchQuery.notField(PARAM_STATUS, Rule.STATUS_REMOVED);
    }

    for(Map.Entry<String, String> param: params.entrySet()) {
      searchQuery.field(param.getKey(), param.getValue().split("\\|"));
    }

    try {
      List<Integer> result = Lists.newArrayList();
      for(String docId: searchIndex.findDocumentIds(searchQuery)) {
        result.add(Integer.parseInt(docId));
      }
      return result;
    } catch(ElasticSearchException searchException) {
      throw new IllegalArgumentException("Unable to perform search, please check query", searchException);
    }
  }

  /**
   * Create or update definition of rule identified by <code>ruleId</code>
   * @param ruleId
   */
  public void saveOrUpdate(int ruleId) {
    DatabaseSession session = sessionFactory.getSession();
    Rule rule = session.getEntity(Rule.class, ruleId);
    try {
      searchIndex.putSynchronous(INDEX_RULES, TYPE_RULE, Integer.toString(rule.getId()), ruleDocument(rule));
    } catch(IOException ioexception) {
      throw new IllegalStateException("Unable to index rule with id="+ruleId, ioexception);
    }
  }

  private void bulkIndex(List<Rule> rules) throws IOException {
    String[] ids = new String[rules.size()];
    BytesStream[] docs = new BytesStream[rules.size()];
    int index = 0;
    for (Rule rule: rules) {
      ids[index] = rule.getId().toString();
      docs[index] = ruleDocument(rule);
      index ++;
    }
    searchIndex.bulkIndex(INDEX_RULES, TYPE_RULE, ids, docs);
  }

  private XContentBuilder ruleDocument(Rule rule) throws IOException {
    XContentBuilder document = XContentFactory.jsonBuilder()
        .startObject()
        .field("id", rule.getId())
        .field("key", rule.getKey())
        .field("language", rule.getLanguage())
        .field("name", ruleI18nManager.getName(rule, Locale.getDefault()))
        .field("description", ruleI18nManager.getDescription(rule.getRepositoryKey(), rule.getKey(), Locale.getDefault()))
        .field("parentKey", rule.getParent() == null ? null : rule.getParent().getKey())
        .field("repositoryKey", rule.getRepositoryKey())
        .field("severity", rule.getSeverity())
        .field("status", rule.getStatus())
        .field("createdAt", rule.getCreatedAt())
        .field("updatedAt", rule.getUpdatedAt());
    if(!rule.getParams().isEmpty()) {
      document.startArray("params");
      for (RuleParam param: rule.getParams()) {
        document.startObject()
          .field("key", param.getKey())
          .field("type", param.getType())
          .field("defaultValue", param.getDefaultValue())
          .field("description", param.getDescription())
          .endObject();
      }
      document.endArray();
    }
    document.endObject();
    return document;
  }
}
