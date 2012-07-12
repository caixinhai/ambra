/*
 * $HeadURL$
 * $Id$
 *
 * Copyright (c) 2006-$today.year by Public Library of Science
 * http://plos.org
 * http://ambraproject.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.ambraproject.article.service;

import org.ambraproject.ApplicationException;
import org.ambraproject.models.ArticleAsset;
import org.ambraproject.model.UserProfileInfo;
import org.ambraproject.model.article.ArticleInfo;
import org.ambraproject.model.article.ArticleType;
import org.ambraproject.model.article.CitationInfo;
import org.ambraproject.model.article.RelatedArticleInfo;
import org.ambraproject.models.Article;
import org.ambraproject.models.ArticleAuthor;
import org.ambraproject.models.ArticleRelationship;
import org.ambraproject.models.Category;
import org.ambraproject.models.UserProfile;
import org.ambraproject.models.UserRole.Permission;
import org.ambraproject.models.Issue;
import org.ambraproject.models.Journal;
import org.ambraproject.models.Volume;
import org.ambraproject.permission.service.PermissionsService;
import org.ambraproject.service.HibernateServiceImpl;
import org.ambraproject.views.ArticleCategory;
import org.ambraproject.views.AssetView;
import org.ambraproject.views.JournalView;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Joe Osowski
 */

public class ArticleServiceImpl extends HibernateServiceImpl implements ArticleService {
  private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

  private PermissionsService permissionsService;

  /**
   * Determines if the articleURI is of type researchArticle
   *
   * @param article The article object
   * @return True if the article is a research article
   * @throws org.ambraproject.ApplicationException
   *                                  if there was a problem talking to the OTM
   * @throws NoSuchArticleIdException When the article does not exist
   */
  public boolean isResearchArticle(final Article article)
      throws ApplicationException, NoSuchArticleIdException {
    // resolve article type and supported properties
    ArticleType articleType = ArticleType.getDefaultArticleType();

    for (String artTypeUri : article.getTypes()) {
      if (ArticleType.getKnownArticleTypeForURI(URI.create(artTypeUri)) != null) {
        articleType = ArticleType.getKnownArticleTypeForURI(URI.create(artTypeUri));
        break;
      }
    }

    if (articleType == null) {
      throw new ApplicationException("Unable to resolve article type for: " + article.getDoi());
    }

    return ArticleType.isResearchArticle(articleType);
  }

  /**
   * Determines if the articleURI is of type researchArticle
   *
   * @param articleInfo The articleInfo Object
   * @return True if the article is a research article
   * @throws org.ambraproject.ApplicationException
   *                                  if there was a problem talking to the OTM
   * @throws NoSuchArticleIdException When the article does not exist
   */
  public boolean isResearchArticle(final ArticleInfo articleInfo)
      throws ApplicationException, NoSuchArticleIdException {
    ArticleType articleType = ArticleType.getDefaultArticleType();

    for (String artTypeUri : articleInfo.getTypes()) {
      if (ArticleType.getKnownArticleTypeForURI(URI.create(artTypeUri)) != null) {
        articleType = ArticleType.getKnownArticleTypeForURI(URI.create(artTypeUri));
        break;
      }
    }

    if (articleType == null) {
      throw new ApplicationException("Unable to resolve article type for: " + articleInfo.getDoi());
    }

    return ArticleType.isResearchArticle(articleType);
  }

  /**
   * Change an articles state.
   *
   * @param articleDoi uri
   * @param authId the authorization ID of the current user
   * @param state   state
   *
   * @throws NoSuchArticleIdException NoSuchArticleIdException
   */
  @Transactional(rollbackFor = {Throwable.class})
  public void setState(final String articleDoi, final String authId, final int state) throws NoSuchArticleIdException {
    permissionsService.checkPermission(Permission.INGEST_ARTICLE, authId);

    List articles = hibernateTemplate.findByCriteria(DetachedCriteria.forClass(Article.class)
          .add(Restrictions.eq("doi", articleDoi)));

    if (articles.size() == 0) {
      throw new NoSuchArticleIdException(articleDoi);
    }

    Article a = (Article)articles.get(0);
    a.setState(state);

    //Remove relationships if this article is being disabled
    //they will be created on re-ingest if necessary, but if both articles in a reciprocal relationship are
    //disabled and have the relationships removed from xml, we want to the relationships to be gone when both are reingested
    if (state == Article.STATE_DISABLED) {
      a.getRelatedArticles().clear();
    }

    hibernateTemplate.update(a);

    //log whenever someone disables or unpublishes an article
    if ((state == Article.STATE_UNPUBLISHED ||
          state == Article.STATE_DISABLED)
        && log.isInfoEnabled()) {
      DetachedCriteria criteria = DetachedCriteria.forClass(UserProfile.class)
          .setProjection(Projections.property("displayName"))
          .add(Restrictions.eq("authId",authId));
      String userName = (String) hibernateTemplate.findByCriteria(criteria, 0, 1).get(0);
      userName = userName == null ? "UNKNOWN" : userName;
      log.info("User '{}' {} the article {}",
          new String[] {userName, state == Article.STATE_DISABLED ?
            "disabled" : "unpublished", articleDoi});
    }
  }

  /**
   * Get the ids of all articles satisfying the given criteria.
   * <p/>
   * This method calls <code>getArticles(...)</code> then parses the Article IDs from that List.
   * <p/>
   *
   * @param params
   * @return the (possibly empty) list of article ids.
   * @throws java.text.ParseException if any of the dates could not be parsed
   */
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<String> getArticleDOIs(final ArticleServiceSearchParameters params) throws ParseException {

    List<Article> articles = getArticles(params);
    List<String> articleIds = new ArrayList<String>(articles.size());

    for (Article article : articles) {
      articleIds.add(article.getDoi());
    }

    return articleIds;
  }

  /**
   * Get all of the articles satisfying the given criteria.

   * @param params
   *
   * @return all of the articles satisfying the given criteria (possibly null) Key is the Article DOI.  Value is the
   *         Article itself.
   */
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<Article> getArticles(final ArticleServiceSearchParameters params) {

    return (List<Article>) this.hibernateTemplate.execute(new HibernateCallback() {
      public Object doInHibernate(Session session) throws HibernateException, SQLException {
        Criteria query = session.createCriteria(Article.class);

        if (params.getStates() != null && params.getStates().length > 0) {
          List<Integer> statesList = new ArrayList<Integer>(params.getStates().length);
          for (int state : params.getStates()) {
            statesList.add(state);
          }
          query.add(Restrictions.in("state", statesList));
        }
        if (params.geteIssn() != null) {
          query.add(Restrictions.eq("eIssn", params.geteIssn()));
        }
        if (params.getOrderField() != null) {
          if (params.isOrderAscending()) {
            query.addOrder(Order.asc(params.getOrderField()));
          } else {
            query.addOrder(Order.desc(params.getOrderField()));
          }
        }

        if (params.getStartDate() != null) {
          query.add(Restrictions.ge("date", params.getStartDate()));
        }
        if (params.getEndDate() != null) {
          query.add(Restrictions.le("date", params.getEndDate()));
        }

        if (params.getMaxResults() > 0) {
          query.setMaxResults(params.getMaxResults());
        }
        //Filter the results post-db access since the kind of restriction we want isn't easily representable in sql -
        //we want only articles such that the list of full names of the author list contains ALL the names in the given
        //author array.  This is NOT an 'in' restriction or an 'equals' restriction
        List<Article> queryResults = query.list();
        List<Article> filteredResults = new ArrayList<Article>(queryResults.size());

        for (Article article : queryResults) {
          if (matchesAuthorFilter(params.getAuthors(), article.getAuthors())
              && matchesCategoriesFilter(params.getCategories(), article.getCategories())) {
            filteredResults.add(article);
          }
        }

        return filteredResults;
      }
    });
  }

  /**
   * Get an Article by ID.
   *
   * @param articleID ID of Article to get.
   * @param authId the authorization ID of the current user
   * @return Article with specified URI or null if not found.
   * @throws NoSuchArticleIdException NoSuchArticleIdException
   */
  @Transactional(readOnly = true, noRollbackFor = {SecurityException.class})
  public Article getArticle(final Long articleID, final String authId) throws NoSuchArticleIdException
  {
    // sanity check parms
    if (articleID == null)
      throw new IllegalArgumentException("articleID == null");

    Article article = (Article)hibernateTemplate.load(Article.class, articleID);

    if (article == null) {
      throw new NoSuchArticleIdException(String.valueOf(articleID));
    }

    checkArticleState(article, authId);

    return article;
  }


  /**
   * Get an Article by URI.
   *
   * @param articleDoi URI of Article to get.
   * @param authId the authorization ID of the current user
   * @return Article with specified URI or null if not found.
   * @throws NoSuchArticleIdException NoSuchArticleIdException
   */
  @Transactional(readOnly = true, noRollbackFor = {SecurityException.class})
  @SuppressWarnings("unchecked")
  public Article getArticle(final String articleDoi, final String authId) throws NoSuchArticleIdException {
    // sanity check parms
    if (articleDoi == null)
      throw new IllegalArgumentException("articleDoi == null");

    List<Article> articles = hibernateTemplate.findByCriteria(
        DetachedCriteria.forClass(Article.class)
            .add(Restrictions.eq("doi", articleDoi)));

    if (articles.size() == 0) {
      throw new NoSuchArticleIdException(articleDoi);
    }

    checkArticleState(articles.get(0), authId);

    return articles.get(0);
  }

  private void checkArticleState(Article article, String authId) throws NoSuchArticleIdException {
    //If the article is unpublished, it should not be returned if the user is not an admin
    if (article.getState() == Article.STATE_UNPUBLISHED) {
      try {
        permissionsService.checkPermission(Permission.VIEW_UNPUBBED_ARTICLES, authId);
      } catch(SecurityException se) {
        throw new NoSuchArticleIdException(article.getDoi());
      }
    }

    //If the article is disabled, don't display it ever
    if (article.getState() == Article.STATE_DISABLED) {
      throw new NoSuchArticleIdException(article.getDoi());
    }
  }

  /**
   * Get articles based on a list of Article id's.
   *
   * If an article is requested that the user does not have access to, it will not be returned
   *
   * @param articleDois list of article doi's
   * @param authId the authorization ID of the current user
   * @return <code>List&lt;Article&gt;</code> of articles requested
   * @throws NoSuchArticleIdException NoSuchArticleIdException
   */
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<Article> getArticles(List<String> articleDois, final String authId) {
    // sanity check parms
    if (articleDois == null)
      throw new IllegalArgumentException("articleDois == null");

    List<Article> articles = hibernateTemplate.findByCriteria(
      DetachedCriteria.forClass(Article.class)
        .add(Restrictions.in("doi", articleDois)));

    for(int a = 0; a < articles.size(); a++) {
      try {
        checkArticleState(articles.get(a), authId);
      } catch(NoSuchArticleIdException ex) {
        articles.remove(a);
      }
    }

    //Make sure the list of returned articles is in the same order as the requesting list.
    List<Article> articlesSorted = new ArrayList<Article>();

    for(String doi : articleDois) {
      for(Article article : articles) {
        if(article.getDoi().equals(doi)) {
          articlesSorted.add(article);
        }
      }
    }

    return articlesSorted;
  }

  /**
   * Get a List of all of the Journal/Volume/Issue combinations that contain the <code>articleURI</code> which was
   * passed in. Each primary List element contains a secondary List of six Strings which are, in order: <ul>
   * <li><strong>Element 0: </strong> Journal URI</li> <li><strong>Element 1: </strong> Journal key</li>
   * <li><strong>Element 2: </strong> Volume URI</li> <li><strong>Element 3: </strong> Volume name</li>
   * <li><strong>Element 4: </strong> Issue URI</li> <li><strong>Element 5: </strong> Issue name</li> </ul> A Journal
   * might have multiple Volumes, any of which might have multiple Issues that contain the <code>articleURI</code>. The
   * primary List will always contain one element for each Issue that contains the <code>articleURI</code>.
   *
   * @param articleDoi Article DOI that is contained in the Journal/Volume/Issue combinations which will be returned
   * @return All of the Journal/Volume/Issue combinations which contain the articleURI passed in
   */
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<List<String>> getArticleIssues(final String articleDoi) {
    return (List<List<String>>) hibernateTemplate.execute(new HibernateCallback() {
      public Object doInHibernate(Session session) throws HibernateException, SQLException {
        List<Object[]> articleIssues = session.createSQLQuery(
            "select {j.*}, {v.*}, {i.*} " +
              "from issueArticleList ial " +
              "join issue i on ial.issueID = i.issueID " +
              "join volume v on i.volumeID = v.volumeID " +
              "join journal j on v.journalID = j.journalID " +
              "where ial.doi = :articleURI " +
              "order by i.created desc ")
            .addEntity("j", Journal.class)
            .addEntity("v", Volume.class)
            .addEntity("i", Issue.class)
            .setString("articleURI", articleDoi)
            .list();

        List<List<String>> finalResults = new ArrayList<List<String>>(articleIssues.size());
        for (Object[] row : articleIssues) {
          Journal journal = (Journal) row[0];
          Volume volume = (Volume) row[1];
          Issue issue = (Issue) row[2];

          List<String> secondaryList = new ArrayList<String>();
          secondaryList.add(journal.getID().toString());  // Journal ID
          secondaryList.add(journal.getJournalKey());     // Journal Key
          secondaryList.add(volume.getVolumeUri());       // Volume URI
          secondaryList.add(volume.getDisplayName());     // Volume name
          secondaryList.add(issue.getIssueUri());         // Issue URI
          secondaryList.add(issue.getDisplayName());      // Issue name
          finalResults.add(secondaryList);
        }

        return finalResults;
      }
    });
  }

  /**
   * Get the articleInfo object for an article
   * @param articleDoi the ID of the article
   * @param authId the authorization ID of the current user
   * @return articleInfo
   */
  @Transactional(readOnly = true)
  @Override
  @SuppressWarnings("unchecked")
  public ArticleInfo getArticleInfo(final String articleDoi, final String authId) throws NoSuchArticleIdException {
    final Article article;

    article = getArticle(articleDoi, authId);

    return createArticleInfo(article, authId);
  }

  /**
   * Get the articleInfo object for an article
   * @param articleDois the ID of the article
   * @param authId the authorization ID of the current user
   * @return articleInfo
   */
  @Transactional(readOnly = true)
  @Override
  @SuppressWarnings("unchecked")
  public List<ArticleInfo> getArticleInfos(final List<String> articleDois, final String authId) {

    final List<Article> articles = getArticles(articleDois, authId);
    List<ArticleInfo> articleInfos = new ArrayList<ArticleInfo>();

    for(Article article : articles) {
      articleInfos.add(createArticleInfo(article, authId));
    }

    return articleInfos;
  }

  private ArticleInfo createArticleInfo(Article article, final String authId) {
    final ArticleInfo articleInfo = new ArticleInfo();

    articleInfo.setId(article.getID());
    //Set properties from the dublin core
    articleInfo.setDoi(article.getDoi());
    articleInfo.setDate(article.getDate());
    articleInfo.setTitle(article.getTitle());
    articleInfo.setVolume(article.getVolume());
    articleInfo.setIssue(article.getIssue());
    articleInfo.setJournal(article.getJournal());
    articleInfo.setDescription(article.getDescription());
    articleInfo.setRights(article.getRights());
    articleInfo.setPublisher(article.getPublisherName());
    articleInfo.seteIssn(article.geteIssn());
    articleInfo.setTypes(article.getTypes());
    articleInfo.setPages(article.getPages());
    articleInfo.setIssue(article.getIssue());
    articleInfo.setVolume(article.getVolume());
    articleInfo.seteLocationId(article.geteLocationId());
    //Set the citation info
    CitationInfo citationInfo = new CitationInfo();
    citationInfo.setId(URI.create(article.getDoi()));
    citationInfo.setCollaborativeAuthors(article.getCollaborativeAuthors());

    //set article asset views
    List<AssetView> aViews = new ArrayList<AssetView>();
    for (ArticleAsset asset : article.getAssets()) {
      aViews.add(new AssetView(asset.getDoi(), asset.getSize(), asset.getExtension()));
    }
    articleInfo.setArticleAssets(aViews);

    Set<Category> categories = article.getCategories();
    Set<ArticleCategory> catViews = new HashSet<ArticleCategory>(categories.size());

    for(Category cat : categories) {
      catViews.add(new ArticleCategory(cat.getMainCategory(), cat.getSubCategory()));
    }
    articleInfo.setCategories(catViews);


    //authors (list of UserProfileInfo)
    //TODO: Refactor ArticleInfo and CitationInfo objects
    //there's no reason why authors need to be attached to the citation
    List<UserProfileInfo> authors = new ArrayList<UserProfileInfo>();
    for (ArticleAuthor ac : article.getAuthors()) {
      UserProfileInfo author = new UserProfileInfo();
      author.setRealName(ac.getFullName());
      authors.add(author);
    }
    citationInfo.setAuthors(authors);
    articleInfo.setCi(citationInfo);

    //set article type
    if (article.getTypes() != null) {
      articleInfo.setAt(article.getTypes());
    }

    Set<org.ambraproject.models.Journal> journals = article.getJournals();
    Set<JournalView> journalViews = new HashSet<JournalView>(journals.size());

    for(org.ambraproject.models.Journal journal : journals) {
      journalViews.add(new JournalView(journal));
    }

    articleInfo.setJournals(journalViews);

    //get related articles
    //this results in more queries than doing a join, but getArticle() already has security logic built in to it
    //and a very small percentage of articles even have related articles
    articleInfo.setRelatedArticles(new ArrayList<RelatedArticleInfo>(article.getRelatedArticles().size()));
    for (ArticleRelationship relationship : article.getRelatedArticles()) {
      if (relationship.getOtherArticleDoi() != null) {
        try {
          Article otherArticle = getArticle(relationship.getOtherArticleDoi(), authId);
          RelatedArticleInfo relatedArticleInfo = new RelatedArticleInfo();
          relatedArticleInfo.setUri(URI.create(otherArticle.getDoi()));
          relatedArticleInfo.setTitle(otherArticle.getTitle());
          if (!articleInfo.getRelatedArticles().contains(relatedArticleInfo)) {
            articleInfo.getRelatedArticles().add(relatedArticleInfo);
          }
        } catch (NoSuchArticleIdException e) {
          //exclude this article
        }
      }
    }

    log.debug("loaded ArticleInfo: id={}, articleTypes={}, " +
      "date={}, title={}, authors={}, related-articles={}",
      new Object[] {articleInfo.getDoi(), articleInfo.getArticleTypeForDisplay(), articleInfo.getDate(),
        articleInfo.getTitle(), Arrays.toString(articleInfo.getAuthors().toArray()),
        Arrays.toString(articleInfo.getRelatedArticles().toArray())});
    return articleInfo;
  }

  @Override
  public ArticleInfo getBasicArticleView(Long articleID) throws NoSuchArticleIdException {
    if (articleID == null) {
      throw new NoSuchArticleIdException("Null id");
    }
    log.debug("loading up title and doi for article: {}", articleID);
    Object[] results = new Object[0];
    try {
      results = (Object[]) hibernateTemplate.findByCriteria(DetachedCriteria.forClass(Article.class)
          .add(Restrictions.eq("ID", articleID))
          .setProjection(Projections.projectionList()
              .add(Projections.property("doi"))
              .add(Projections.property("title"))),0,1).get(0);
    } catch (IndexOutOfBoundsException e) {
      throw new NoSuchArticleIdException(articleID.toString());
    }
    ArticleInfo articleInfo = new ArticleInfo();
    articleInfo.setDoi((String) results[0]);
    articleInfo.setTitle((String) results[1]);
    articleInfo.setId(articleID);

    return articleInfo;
  }

  @Override
  public ArticleInfo getBasicArticleView(String articleDoi) throws NoSuchArticleIdException {
    if (articleDoi == null) {
      throw new NoSuchArticleIdException("Null doi");
    }
    log.debug("loading up title and doi for article: {}", articleDoi);
    Object[] results = new Object[0];
    try {
      results = (Object[]) hibernateTemplate.findByCriteria(DetachedCriteria.forClass(Article.class)
          .add(Restrictions.eq("doi", articleDoi))
          .setProjection(Projections.projectionList()
              .add(Projections.id())
              .add(Projections.property("title"))),0,1).get(0);
    } catch (IndexOutOfBoundsException e) {
      throw new NoSuchArticleIdException(articleDoi.toString());
    }
    ArticleInfo articleInfo = new ArticleInfo();
    articleInfo.setDoi(articleDoi);
    articleInfo.setId((Long) results[0]);
    articleInfo.setTitle((String) results[1]);

    return articleInfo;
  }


  /**
   * Help0er method for getArticleIds() since the restriction we want doesn't appear
   *
   * @param authorFilter
   * @param authors
   * @return
   */
  private boolean matchesAuthorFilter(String[] authorFilter, List<ArticleAuthor> authors) {
    if (authorFilter != null && authorFilter.length > 0) {
      List<String> authorNames = new ArrayList<String>(authors.size());
      for (ArticleAuthor author : authors) {
        authorNames.add(author.getFullName());
      }
      for (String author : authorFilter) {
        if (!authorNames.contains(author)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Helper method for getArticleIds(), since the type of restriction needed doesn't seem to be do-able in HQL or with
   * criteria - we want articles where each of the Strings in the category array is the main category of one of the
   * article's categories
   *
   * @param filterCategories - the array of main categories that was passed in to getArticleIds() to use to filter
   *                         results
   * @param categorySet      - the 'categories' property of an article
   * @return - true if the article passes the category filter, false otherwise
   */
  private boolean matchesCategoriesFilter(String[] filterCategories, Set<Category> categorySet) {
    if (filterCategories != null && filterCategories.length > 0) {

      if (filterCategories.length > categorySet.size()) {
        return false; //can't possibly contain all the categories if there's more of them than you have
      }

      //Just get the main category
      Set<String> mainCategories = new HashSet<String>(categorySet.size());
      for (Category category : categorySet) {
        mainCategories.add(category.getMainCategory());
      }
      //check that all the filter categories are in their
      for (String cat : filterCategories) {
        if (!mainCategories.contains(cat)) {
          return false;
        }
      }

    }
    return true;
  }

  /**
   * @param permissionsService the permissions service to use
   */
  @Required
  public void setPermissionsService(PermissionsService permissionsService) {
    this.permissionsService = permissionsService;
  }
}