/*
 * $HeadURL$
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

package org.ambraproject.service.crossref;

import java.util.List;

/**
 * <a href="http://www.crossref.org/">CrossRef</a> lookup service.
 *
 * @author Dragisa Krsmanovic
 */
public interface CrossRefLookupService {

  /**
   * Find a DOI for an article based on the passed in parameters.  If multiple articles are found that match, one is
   * arbitrarily selected.
   *
   * @param title Article title
   * @param author Author name
   * @param journal the journal
   * @param volume the volume
   * @param pages the pages text
   * @return DOI of an article that matches, or null if no match is found
   * @throws Exception
   */
  public String findDoi(String title, String author, String journal, String volume, String pages) throws Exception;

  /**
   * Find article based on title and first author.
   *
   * @param title Article title
   * @param author Author name
   * @param journal the journal
   * @param volume the volume
   * @param pages the pages text
   * @return List of articles that match criteria. Empty string if none is found.
   * @see CrossRefArticle
   * @throws Exception When an error is encountered
   */
  public List<CrossRefArticle> findArticles(String title, String author, String journal, String volume, String pages) throws Exception;
}
