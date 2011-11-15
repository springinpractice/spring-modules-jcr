package org.springmodules.jcr;

import static org.springframework.util.Assert.notNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.springframework.core.CollectionFactory;
import org.springframework.dao.DataAccessException;
import org.xml.sax.ContentHandler;

/**
 * Helper class that simplifies JCR data access code.
 * 
 * Typically used to implement data access or business logic services that use
 * JCR within their implementation but are JCR-agnostic in their interface.
 * 
 * Requires a {@link JcrSessionFactory} to provide access to a JCR repository. A
 * workspace name is optional, as the repository will choose the default
 * workspace if a name is not provided.
 * 
 * @author Costin Leau
 */
public class JcrTemplate extends JcrAccessor implements JcrOperations {
	private boolean allowCreate = false;
	private boolean exposeNativeSession = false;

	/**
	 */
	public JcrTemplate() {
	}

	/**
	 */
	public JcrTemplate(SessionFactory sessionFactory) {
		setSessionFactory(sessionFactory);
		afterPropertiesSet();
	}

	/**
	 * @return Returns the allowCreate.
	 */
	public boolean isAllowCreate() { return allowCreate; }

	/**
	 * @param allowCreate The allowCreate to set.
	 */
	public void setAllowCreate(boolean allowCreate) { this.allowCreate = allowCreate; }

	/**
	 * @return Returns the exposeNativeSession.
	 */
	public boolean isExposeNativeSession() { return exposeNativeSession; }

	/**
	 * @param exposeNativeSession The exposeNativeSession to set.
	 */
	public void setExposeNativeSession(boolean exposeNativeSession) { this.exposeNativeSession = exposeNativeSession; }

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOperations#execute(org.springmodules.jcr.JcrCallback, boolean)
	 */
	@Override
	public Object execute(JcrCallback action, boolean exposeNativeSession) throws DataAccessException {
		Session session = getSession();
		
		boolean existingTransaction = SessionFactoryUtils.isSessionThreadBound(session, getSessionFactory());
		if (existingTransaction) {
			logger.debug("Found thread-bound Session for JcrTemplate");
		}

		try {
			Session sessionToExpose = (exposeNativeSession ? session : createSessionProxy(session));
			Object result = action.doInJcr(sessionToExpose);
			// TODO: does flushing (session.refresh) should work here?
			// flushIfNecessary(session, existingTransaction);
			return result;
		} catch (RepositoryException ex) {
			throw convertJcrAccessException(ex);
			// IOException are not converted here
		} catch (IOException ex) {
			// use method to decouple the static call
			throw convertJcrAccessException(ex);
		} catch (RuntimeException ex) {
			// Callback code threw application exception...
			throw convertJcrAccessException(ex);
		} finally {
			if (existingTransaction) {
				logger.debug("Not closing pre-bound Jcr Session after JcrTemplate");
			} else {
				SessionFactoryUtils.releaseSession(session, getSessionFactory());
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOperations#execute(org.springmodules.jcr.JcrCallback)
	 */
	@Override
	public Object execute(JcrCallback callback) throws DataAccessException {
		return execute(callback, isExposeNativeSession());
	}

	/**
	 * Return a Session for use by this template. A pre-bound Session in case of
	 * "allowCreate" turned off and a pre-bound or new Session else (new only if
	 * not transactional or otherwise pre-bound Session exists).
	 * 
	 * @see SessionFactoryUtils#getSession
	 * @see SessionFactoryUtils#getNewSession
	 * @see #setAllowCreate
	 */
	protected Session getSession() {
		return SessionFactoryUtils.getSession(getSessionFactory(), allowCreate);
	}
	
	
	// =================================================================================================================
	// Convenience methods for loading individual objects
	// =================================================================================================================

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOptionalOperations#addLockToken(java.lang.String)
	 */
	@Override
	public void addLockToken(final String lock) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.addLockToken(lock);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(final String name) {
		return execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getAttribute(name);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getAttributeNames()
	 */
	@Override
	public String[] getAttributeNames() {
		return (String[]) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getAttributeNames();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getImportContentHandler(java.lang.String, int)
	 */
	@Override
	public ContentHandler getImportContentHandler(final String parentAbsPath, final int uuidBehavior) {
		return (ContentHandler) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getImportContentHandler(parentAbsPath, uuidBehavior);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getItem(java.lang.String)
	 */
	@Override
	public Item getItem(final String absPath) {
		return (Item) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getItem(absPath);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOptionalOperations#getLockTokens()
	 */
	@Override
	public String[] getLockTokens() {
		return (String[]) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getLockTokens();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getNamespacePrefix(java.lang.String)
	 */
	@Override
	public String getNamespacePrefix(final String uri) {
		return (String) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getNamespacePrefix(uri);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getNamespacePrefixes()
	 */
	@Override
	public String[] getNamespacePrefixes() {
		return (String[]) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getNamespacePrefixes();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getNamespaceURI(java.lang.String)
	 */
	@Override
	public String getNamespaceURI(final String prefix) {
		return (String) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getNamespaceURI(prefix);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getNodeByUUID(java.lang.String)
	 */
	@Override
	public Node getNodeByUUID(final String uuid) {
		return (Node) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getNodeByUUID(uuid);
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getRootNode()
	 */
	@Override
	public Node getRootNode() {
		return (Node) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getRootNode();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getUserID()
	 */
	@Override
	public String getUserID() {
		return (String) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getUserID();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#getValueFactory()
	 */
	@Override
	public ValueFactory getValueFactory() {
		return (ValueFactory) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return session.getValueFactory();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#hasPendingChanges()
	 */
	@Override
	public boolean hasPendingChanges() {
		return ((Boolean) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return new Boolean(session.hasPendingChanges());
			}
		}, true)).booleanValue();
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#importXML(java.lang.String, java.io.InputStream, int)
	 */
	@Override
	public void importXML(final String parentAbsPath, final InputStream in, final int uuidBehavior) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				try {
					session.importXML(parentAbsPath, in, uuidBehavior);
				} catch (IOException e) {
					throw new JcrSystemException(e);
				}
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#refresh(boolean)
	 */
	@Override
	public void refresh(final boolean keepChanges) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.refresh(keepChanges);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOptionalOperations#removeLockToken(java.lang.String)
	 */
	@Override
	public void removeLockToken(final String lt) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.removeLockToken(lt);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOperations#rename(javax.jcr.Node, java.lang.String)
	 */
	@Override
	public void rename(final Node node, final String newName) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.move(node.getPath(), node.getParent().getPath() + "/" + newName);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#setNamespacePrefix(java.lang.String, java.lang.String)
	 */
	public void setNamespacePrefix(final String prefix, final String uri) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.setNamespacePrefix(prefix, uri);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#isLive()
	 */
	@Override
	public boolean isLive() {
		return ((Boolean) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return new Boolean(session.isLive());
			}
		}, true)).booleanValue();
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#itemExists(java.lang.String)
	 */
	@Override
	public boolean itemExists(final String absPath) {
		return ((Boolean) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				return new Boolean(session.itemExists(absPath));
			}
		}, true)).booleanValue();
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#move(java.lang.String, java.lang.String)
	 */
	@Override
	public void move(final String srcAbsPath, final String destAbsPath) {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.move(srcAbsPath, destAbsPath);
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel2Operations#save()
	 */
	@Override
	public void save() {
		execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				session.save();
				return null;
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrOperations#dump(javax.jcr.Node)
	 */
	@Override
	public String dump(final Node node) {
		return (String) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				Node nd = node;
				if (nd == null) { nd = session.getRootNode(); }
				return dumpNode(nd);
			}
		}, true);
	}

	/**
	 * Recursive method for dumping a node. This method is separate to avoid the
	 * overhead of searching and opening/closing JCR sessions.
	 * 
	 * @param node
	 * @return
	 * @throws RepositoryException
	 */
	protected String dumpNode(Node node) throws RepositoryException {
		StringBuilder builder = new StringBuilder();
		builder.append(node.getPath());

		PropertyIterator properties = node.getProperties();
		while (properties.hasNext()) {
			Property property = properties.nextProperty();
			builder.append(property.getPath() + "=");
			if (property.getDefinition().isMultiple()) {
				Value[] values = property.getValues();
				for (int i = 0; i < values.length; i++) {
					if (i > 0) { builder.append(","); }
					builder.append(values[i].getString());
				}
			} else {
				builder.append(property.getString());
			}
			builder.append("\n");
		}

		NodeIterator nodes = node.getNodes();
		while (nodes.hasNext()) {
			Node child = nodes.nextNode();
			builder.append(dumpNode(child));
		}
		return builder.toString();

	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#query(javax.jcr.Node)
	 */
	@Override
	public QueryResult query(final Node node) {
		if (node == null){
			throw new IllegalArgumentException("node can't be null");
		}
		
		return (QueryResult) execute(new JcrCallback() {
			/**
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			public Object doInJcr(Session session) throws RepositoryException {
				boolean debug = logger.isDebugEnabled();

				// get query manager
				QueryManager manager = session.getWorkspace().getQueryManager();
				if (debug) { logger.debug("retrieved manager " + manager); }
				Query query = manager.getQuery(node);
				if (debug) { logger.debug("created query " + query); }
				return query.execute();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#query(java.lang.String)
	 */
	@Override
	public QueryResult query(final String statement) {
		return query(statement, null);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#query(java.lang.String, java.lang.String)
	 */
	@Override
	public QueryResult query(final String statement, final String language) {
		notNull(statement, "statement can't be null");
		return (QueryResult) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				
				// check language
				String lang = language;
				if (lang == null) { lang = Query.XPATH; }
				boolean debug = logger.isDebugEnabled();

				// get query manager
				QueryManager manager = session.getWorkspace().getQueryManager();
				if (debug) { logger.debug("retrieved manager " + manager); }
				Query query = manager.createQuery(statement, lang);
				if (debug) { logger.debug("created query " + query); }
				return query.execute();
			}
		}, true);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#query(java.util.List)
	 */
	@Override
	public Map query(final List list) {
		return query(list, null, false);
	}

	/* (non-Javadoc)
	 * @see org.springmodules.jcr.JcrModel1Operations#query(java.util.List, java.lang.String, boolean)
	 */
	@Override
	public Map query(final List list, final String language, final boolean ignoreErrors) {
		if (list == null)
			throw new IllegalArgumentException("list can't be null");

		return (Map) execute(new JcrCallback() {
			
			/* (non-Javadoc)
			 * @see org.springmodules.jcr.JcrCallback#doInJcr(javax.jcr.Session)
			 */
			@Override
			public Object doInJcr(Session session) throws RepositoryException {
				
				// check language
				String lang = language;
				if (lang == null) { lang = Query.XPATH; }
				boolean debug = logger.isDebugEnabled();

				Map map = CollectionFactory.createLinkedMapIfPossible(list.size());

				// get query manager
				QueryManager manager = session.getWorkspace().getQueryManager();
				if (debug) { logger.debug("retrieved manager " + manager); }
				for (Iterator iter = list.iterator(); iter.hasNext();) {
					String statement = (String) iter.next();

					Query query = manager.createQuery(statement, lang);
					if (debug) { logger.debug("created query " + query); }

					QueryResult result;
					try {
						result = query.execute();
						map.put(statement, result);
					} catch (RepositoryException e) {
						if (ignoreErrors) {
							map.put(statement, null);
						} else {
							throw convertJcrAccessException(e);
						}
					}
				}
				return map;
			}
		}, true);
	}

	/**
	 * Create a logout-suppressing proxy for the given JCR Session. 
	 * 
	 * @param session the Jcr Session to create a proxy for
	 * @return the Session proxy
	 * @see javax.jcr.Session#logout()
	 */
	protected Session createSessionProxy(Session session) {
		return (Session) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class[] { Session.class },
				new LogoutSuppressingInvocationHandler(session));
	}

	/**
	 * Invocation handler that suppresses logout calls on JCR Session.
	 * 
	 * @see javax.jcr.Sesion#logout
	 */
	private class LogoutSuppressingInvocationHandler implements InvocationHandler {
		private final Session target;

		public LogoutSuppressingInvocationHandler(Session target) {
			this.target = target;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			
			// Invocation on Session interface (or vendor-specific extension) coming in...
			String methodName = method.getName();
			if ("equals".equals(methodName)) {
				// Only consider equal when proxies are identical.
				return (proxy == args[0] ? Boolean.TRUE : Boolean.FALSE);
			} else if ("hashCode".equals(methodName)) {
				// Use hashCode of session proxy.
				return new Integer(hashCode());
			} else if ("logout".equals(methodName)) {
				// Handle close method: suppress, not valid.
				return null;
			}

			// Invoke method on target Session.
			try {
				Object retVal = method.invoke(this.target, args);

				// TODO: watch out for Query returned
				/*
				 * if (retVal instanceof Query) { prepareQuery(((Query)
				 * retVal)); }
				 */
				return retVal;
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}

	protected boolean isVersionable(Node node) throws RepositoryException {
		return node.isNodeType("mix:versionable");
	}

}
