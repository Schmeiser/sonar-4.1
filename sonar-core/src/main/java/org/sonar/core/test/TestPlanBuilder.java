/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.core.test;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import org.sonar.api.test.MutableTestPlan;
import org.sonar.core.component.ComponentWrapper;
import org.sonar.core.component.PerspectiveBuilder;
import org.sonar.core.graph.GraphUtil;

public class TestPlanBuilder extends PerspectiveBuilder<MutableTestPlan> {

  public TestPlanBuilder() {
    super(MutableTestPlan.class);
  }

  @Override
  public MutableTestPlan load(ComponentWrapper<?> componentWrapper) {
    Vertex planVertex = GraphUtil.singleAdjacent(componentWrapper.element(), Direction.OUT, "testplan");
    if (planVertex != null) {
      return componentWrapper.graph().wrap(planVertex, DefaultTestPlan.class);
    }
    return null;
  }

  @Override
  public MutableTestPlan create(ComponentWrapper<?> componentWrapper) {
    return componentWrapper.graph().createVertex(componentWrapper, DefaultTestPlan.class, "testplan");
  }
}