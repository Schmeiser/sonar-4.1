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
package org.sonar.core.technicaldebt;

import com.google.common.base.Objects;
import org.sonar.api.BatchExtension;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.internal.WorkDayDuration;

/**
 * Computes the remediation cost based on the quality and analysis models.
 */
public class TechnicalDebtCalculator implements BatchExtension {

  private final TechnicalDebtConverter converter;
  private TechnicalDebtModel technicalDebtModel;

  public TechnicalDebtCalculator(TechnicalDebtModel technicalDebtModel, TechnicalDebtConverter converter) {
    this.technicalDebtModel = technicalDebtModel;
    this.converter = converter;
  }

  public WorkDayDuration calculTechnicalDebt(Issue issue) {
    TechnicalDebtRequirement requirement = technicalDebtModel.getRequirementByRule(issue.ruleKey().repository(), issue.ruleKey().rule());
    if (requirement != null) {
      return converter.fromMinutes(calculTechnicalDebt(requirement, issue));
    }
    return null;
  }

  private long calculTechnicalDebt(TechnicalDebtRequirement requirement, Issue issue) {
    long effortToFix = Objects.firstNonNull(issue.effortToFix(), 1L).longValue();

    WorkUnit factorUnit = requirement.getRemediationFactor();
    long factor = factorUnit != null ? converter.toMinutes(factorUnit) : 0L;

    WorkUnit offsetUnit = requirement.getOffset();
    long offset = offsetUnit != null ? converter.toMinutes(offsetUnit) : 0L;

    return effortToFix * factor + offset;
  }
}
