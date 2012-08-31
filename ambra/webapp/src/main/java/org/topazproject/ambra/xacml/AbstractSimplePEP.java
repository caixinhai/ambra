/* $HeadURL::                                                                            $
 * $Id$
 *
 * Copyright (c) 2006-2010 by Public Library of Science
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
package org.topazproject.ambra.xacml;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.xacml.PDP;
import com.sun.xacml.attr.AnyURIAttribute;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.ctx.Attribute;
import com.sun.xacml.ctx.RequestCtx;
import com.sun.xacml.ctx.Subject;

/**
 * A superclass for simple XACML PEP's.
 *
 * @author Pradeep Krishnan
 * @author Ronald Tschalär
 */
public abstract class AbstractSimplePEP extends DenyBiasedPEP {
  /**
   * Resource URI that represents any resource.
   *
   * Allows PEP.checkAccess(myAction, ANY_RESOURCE),
   * i.e. check to see if the user can perform myAction against any resource.
   */
  public static final URI ANY_RESOURCE = URI.create("dummy:dummy");

  private static Map         envAttrsMap         = new HashMap();
  private static Map         actionAttrsMap      = new HashMap();
  private static Map         knownObligationsMap = new HashMap();

  /**
   * The real subject attributes are provided to the PDP via the attribute finders. However
   * the sun xacml api expects a non-empty set of subject attributes to be present in the PEP
   * Request. So we dummy up one here.
   */
  private static Attribute dummyAttr =
    new Attribute(URI.create("ambra:xacml:dummy"), null, null, new StringAttribute("dummy"));
  private static Set dummySubjAttrs =
    Collections.singleton(new Subject(URI.create("ambra:xacml:dummy"),
                                      Collections.singleton(dummyAttr)));

  /** 
   * Initialize the list of supported actions and their obligations. 
   * 
   * @param clazz       the subclass for which the actions and obligations are being initialized.
   *                    Note that {@link #evaluate evaluate} uses <code>getClass()</code> to look
   *                    up these actions and obligations.
   * @param actions     the list of known/supported actions
   * @param obligations the obligations for the actions. May be null in which case no action has any
   *                    obligations; otherwise it must an array of the same length as
   *                    <var>actions</var>. Each element of the array lists the obligations for the
   *                    action at the same index in the <var>actions</var> array; an element may be
   *                    null to indicate no obligations for that action.
   */
  protected synchronized static void init(Class clazz, String[] actions, String[][] obligations) {
    envAttrsMap.put(clazz, Collections.EMPTY_SET);

    Map aa = new HashMap();
    for (int idx = 0; idx < actions.length; idx++)
      aa.put(actions[idx], Util.createActionAttrs(actions[idx]));
    actionAttrsMap.put(clazz, aa);

    if (obligations == null)
      obligations = new String[actions.length][0];

    Map ko = new HashMap();
    for (int idx = 0; idx < actions.length; idx++)
      ko.put(actions[idx], toURISet(obligations[idx]));
    knownObligationsMap.put(clazz, ko);
  }

  private static final Set toURISet(String[] uris) {
    if (uris == null || uris.length == 0)
      return Collections.EMPTY_SET;

    Set res = new HashSet();
    for (int idx = 0; idx < uris.length; idx++)
      res.add(URI.create(uris[idx]));
    return res;
  }

  private synchronized static Set envAttrs(Class clazz) {
    return (Set) envAttrsMap.get(clazz);
  }

  private synchronized static Map actionAttrs(Class clazz) {
    return (Map) actionAttrsMap.get(clazz);
  }

  private synchronized static Map knownObligations(Class clazz) {
    return (Map) knownObligationsMap.get(clazz);
  }

  /**
   * Creates a new PEP object.
   *
   * @param  pdp       The pdp to use for evaluating requests
   */
  protected AbstractSimplePEP(PDP pdp) {
    super(pdp);
  }

  /**
   * Creates a new PEP object with the same pdp and subject Attrs.
   *
   * @param  pep the pep to copy from
   */
  protected AbstractSimplePEP(AbstractSimplePEP pep) {
    super(pep.getPdp());
  }

  /** 
   * Check if the requested operation is allowed on the given resources.
   * 
   * @param action        the operation being attempted
   * @param resourceAttrs the resources being operated on
   * @return the set of XACML obligations that need to be satisfied
   * @throws SecurityException if the operation is not permitted
   */
  protected Set checkAccess(String action, Set resourceAttrs) throws SecurityException {
    Set actionAttrs = (Set) actionAttrs(getClass()).get(action);
    if (actionAttrs == null)
      throw new SecurityException("Unknown action '" + action + "' for PEP");

    return evaluate(new RequestCtx(dummySubjAttrs, resourceAttrs, actionAttrs,
                    envAttrs(getClass())), (Set) knownObligations(getClass()).get(action));
  }

  /** 
   * Check if the requested operation is allowed on the given resource.
   * 
   * @param action   the operation being attempted
   * @param resource the resource being operated on
   * @return the set of XACML obligations that need to be satisfied
   * @throws SecurityException if the operation is not permitted
   */
  public Set checkAccess(String action, URI resource) throws SecurityException {
    Set resourceAttrs = new HashSet();
    resourceAttrs.add(new Attribute(Util.RESOURCE_ID, null, null, new AnyURIAttribute(resource)));

    return checkAccess(action, resourceAttrs);
  }
}