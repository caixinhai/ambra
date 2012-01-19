/* $HeadURL::                                                                            $
 * $Id$
 *
 * Copyright (c) 2007-2008 by Topaz, Inc.
 * http://topazproject.org
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
package org.topazproject.otm.criterion;

import org.topazproject.otm.Criteria;
import org.topazproject.otm.OtmException;
import org.topazproject.otm.annotations.Entity;

/**
 * A criterion for a "greater than" operation on a field value.
 *
 * @author Pradeep Krishnan
 */
@Entity(types = {Criterion.RDF_TYPE + "/gt"})
public class GTCriterion extends AbstractComparisonCriterion {
  /**
   * Creates a new GTCriterion object.
   */
  public GTCriterion() {
  }

  /**
   * Creates a new GTCriterion object.
   *
   * @param name field/predicate name
   * @param value field/predicate value
   */
  public GTCriterion(String name, Object value) {
    super(name, value);
  }

  /*
   * inherited javadoc
   */
  public String toQuery(Criteria criteria, String subjectVar, String varPrefix, QL ql)
                throws OtmException {
    return toQuery(criteria, subjectVar, varPrefix, "gt", ql);
  }
}
