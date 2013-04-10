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

package org.sonar.core.source;

import org.junit.Before;
import org.junit.Test;
import org.sonar.core.persistence.AbstractDaoTestCase;

import static org.fest.assertions.Assertions.assertThat;
import static org.sonar.core.source.HtmlTextWrapper.LF_END_OF_LINE;

public class SyntaxHighlighterTest extends AbstractDaoTestCase {

  @Before
  public void setUpDatasets() {
    setupData("shared");
  }

  @Test
  public void should_highlight_source_with_html() throws Exception {

    SyntaxHighlighter highlighter = new SyntaxHighlighter(getMyBatis());

    String highlightedSource = highlighter.getHighlightedSourceAsHtml(11L);

    assertThat(highlightedSource).isEqualTo(
            "<tr><td><span class=\"cppd\">/*</span></td></tr>" + LF_END_OF_LINE +
            "<tr><td><span class=\"cppd\"> * Header</span></td></tr>" + LF_END_OF_LINE +
            "<tr><td><span class=\"cppd\"> */</span></td></tr>" + LF_END_OF_LINE +
            "<tr><td></td></tr>" + LF_END_OF_LINE +
            "<tr><td><span class=\"k\">public </span><span class=\"k\">class </span>HelloWorld {</td></tr>" + LF_END_OF_LINE +
            "<tr><td>}</td></tr>"
      );
  }

}
