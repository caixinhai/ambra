/* $HeadURL::                                                                                      $
 * $Id$
 *
 * Copyright (c) 2007-2010 by Public Library of Science
 * http://plos.org
 * http://ambraproject.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.topazproject.ambra.it.pages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.topazproject.ambra.it.jwebunit.AmbraWebTester;

/**
 * An abstract base class for Ambra pages.
 *
 * @author Pradeep Krishnan
 */
public abstract class AbstractPage {
  private static final Log    log         = LogFactory.getLog(AbstractPage.class);

  protected final AmbraWebTester tester;
  protected final String url;
  protected final String journal;
  public static final String J_PONE = "pone";
  public static final String J_CT = "ct";

  public AbstractPage(AmbraWebTester tester, String journal, String url) {
    this.tester = tester;
    this.journal = journal;
    this.url = url;
  }

  public String getJournal() {
    return journal;
  }

  public String getUrl() {
    return url;
  }

  public AmbraWebTester getTester() {
    return tester;
  }


  public void beginAt() {
    log.debug("New browser to " + url + " ...");
    tester.beginAt(url);
  }

  public void gotoPage() {
    log.debug("Going to " + url + " ...");
    tester.gotoPage(url);
    verifyPage();
  }

  public abstract void verifyPage();


  public boolean isLoginPage() {
    return "Dummy-SSO Login".equals(tester.getDialog().getPageTitle());
  }

}