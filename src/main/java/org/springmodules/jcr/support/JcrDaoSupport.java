package org.springmodules.jcr.support;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.support.DaoSupport;
import org.springframework.util.Assert;
import org.springmodules.jcr.JcrTemplate;
import org.springmodules.jcr.SessionFactory;
import org.springmodules.jcr.SessionFactoryUtils;

/**
 * Convenient class for accessing JCR objects.
 * 
 * @author Costin Leau
 * @author Guillaume Bort <guillaume.bort@zenexity.fr>
 */
public abstract class JcrDaoSupport extends DaoSupport {
	private static Logger log = LoggerFactory.getLogger(JcrDaoSupport.class);
	
	private JcrTemplate template;

	/**
	 * Return the JCR SessionFactory used by this DAO.
	 */
	public SessionFactory getSessionFactory() {
		return (template != null ? template.getSessionFactory() : null);
	}

	/**
	 * Set the JCR SessionFactory to be used by this DAO. Will automatically create a JcrTemplate for the given
	 * SessionFactory.
	 * 
	 * @see #createJcrTemplate
	 * @see #setJcrTemplate
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.template = new JcrTemplate(sessionFactory);
	}

	/**
	 * Return the JcrTemplate for this DAO, pre-initialized with the SessionFactory or set explicitly.
	 */
	public JcrTemplate getTemplate() {
		return template;
	}

	/**
	 * Set the JcrTemplate for this DAO explicitly, as an alternative to specifying a SessionFactory.
	 * 
	 * @see #setSessionFactory
	 */
	public void setTemplate(JcrTemplate jcrTemplate) {
		this.template = jcrTemplate;
	}

	protected final void checkDaoConfig() {
		Assert.notNull(template, "sessionFactory or jcrTemplate is required");
	}

	/**
	 * Get a JCR Session, either from the current transaction or a new one. The latter is only allowed if the
	 * "allowCreate" setting of this bean's JcrTemplate is true.
	 * 
	 * @return the JCR Session
	 * @throws DataAccessResourceFailureException if the Session couldn't be created
	 * @throws IllegalStateException if no thread-bound Session found and allowCreate false
	 * @see org.springmodules.jcr.SessionFactoryUtils#getSession
	 */
	protected final Session getSession() {
		return getSession(this.template.isAllowCreate());
	}

	/**
	 * Get a JCR Session, either from the current transaction or a new one. The latter is only allowed if "allowCreate"
	 * is true.
	 * 
	 * @param allowCreate
	 *            if a non-transactional Session should be created when no transactional Session can be found for the
	 *            current thread
	 * @return the JCR Session
	 * @throws DataAccessResourceFailureException
	 *             if the Session couldn't be created
	 * @throws IllegalStateException
	 *             if no thread-bound Session found and allowCreate false
	 * @see org.springmodules.jcr.SessionFactoryUtils#getSession
	 */
	protected final Session getSession(boolean allowCreate)
			throws DataAccessResourceFailureException, IllegalStateException {
		
		log.debug("Getting session");
		return SessionFactoryUtils.getSession(getSessionFactory(), allowCreate);
	}

	/**
	 * Convert the given JCRException to an appropriate exception from the org.springframework.dao hierarchy.
	 * 
	 * <p>Delegates to the convertJCRAccessException method of this DAO's JCRTemplate.
	 * 
	 * @param ex JCRException that occured
	 * @return the corresponding DataAccessException instance
	 * @see #setJCRTemplate
	 * @see org.springmodules.jcr.JcrTemplate#convertJCRAccessException
	 */
	protected final DataAccessException convertJcrAccessException(RepositoryException ex) {
		return this.template.convertJcrAccessException(ex);
	}
	
	/**
	 * Indicates whether the given JCR Session is thread-bound; i.e., bound to
	 * the current thread by Spring's transaction facilities (which is used as a
	 * thread-binding utility class).
	 * 
	 * @param session
	 *            session
	 * @return boolean indicating whether the session is bound to the thread
	 *         (and hence part of an existing transaction)
	 */
	protected final boolean isSessionThreadBound(Session session) {
		return SessionFactoryUtils.isSessionThreadBound(session, getSessionFactory());
	}
	
	/**
	 * Close the given JCR Session, created via this DAO's
	 * SessionFactory, if it isn't bound to the thread.
	 * @param pm Session to close
	 * @see org.springframework.orm.JCR.SessionFactoryUtils#releaseSession
	 */
	protected final void releaseSession(Session session) {
		log.debug("Releasing session");
		SessionFactoryUtils.releaseSession(session, getSessionFactory());
	}

}
