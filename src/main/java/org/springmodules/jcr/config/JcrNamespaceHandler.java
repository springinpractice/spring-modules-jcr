/*
 * Copyright 2002-2006 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springmodules.jcr.config;

import java.util.Iterator;
import java.util.List;

import javax.jcr.observation.Event;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.core.Constants;
import org.springframework.util.xml.DomUtils;
import org.springmodules.jcr.EventListenerDefinition;
import org.springmodules.jcr.JcrSessionFactory;
import org.w3c.dom.Element;

/**
 * NamespaceHandler for elements in the jcr namespace.
 * 
 * @author Costin Leau
 * @author Willie Wheeler
 */
public class JcrNamespaceHandler extends NamespaceHandlerSupport {
	
	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.xml.NamespaceHandler#init()
	 */
	@Override
	public void init() {
		registerBeanDefinitionParser("eventListenerDefinition", new JcrEventListenerBeanDefinitionParser());
		registerBeanDefinitionParser("sessionFactory", new JcrSessionFactoryBeanDefinitionParser());
	}

	/**
	 * Parses the &lt;jcr:eventListenerDefinition&gt; configuration element.
	 */
	private class JcrEventListenerBeanDefinitionParser extends AbstractSimpleBeanDefinitionParser {
		public static final String EVENT_TYPE = "eventType";
		public static final String NODE_TYPE_NAME = "nodeTypeName";
		public static final String UUID = "uuid";
		
		/* (non-Javadoc)
		 * @see org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser#getBeanClass
		 * (org.w3c.dom.Element)
		 */
		@Override
		protected Class<?> getBeanClass(Element element) {
			return EventListenerDefinition.class;
		}

		/* (non-Javadoc)
		 * @see org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser#postProcess
		 * (org.springframework.beans.factory.support.BeanDefinitionBuilder, org.w3c.dom.Element)
		 */
		@Override
		protected void postProcess(BeanDefinitionBuilder definitionBuilder, Element element) {
			List<Element> eventTypes = DomUtils.getChildElementsByTagName(element, EVENT_TYPE);
			if (eventTypes != null && eventTypes.size() > 0) {
				// compute event type
				int eventType = 0;
				Constants types = new Constants(Event.class);
				for (Iterator<Element> iter = eventTypes.iterator(); iter.hasNext();) {
					Element evenTypeElement = iter.next();
					eventType |= types.asNumber(DomUtils.getTextValue(evenTypeElement)).intValue();
				}
				definitionBuilder.addPropertyValue(EVENT_TYPE, new Integer(eventType));
			}

			List<Element> nodeTypeNames = DomUtils.getChildElementsByTagName(element, NODE_TYPE_NAME);
			String[] nodeTypeValues = new String[nodeTypeNames.size()];

			for (int i = 0; i < nodeTypeValues.length; i++) {
				nodeTypeValues[i] = DomUtils.getTextValue(nodeTypeNames.get(i));
			}
			definitionBuilder.addPropertyValue(NODE_TYPE_NAME, nodeTypeValues);
			List<Element> uuids = DomUtils.getChildElementsByTagName(element, UUID);

			String[] uuidsValues = new String[uuids.size()];

			for (int i = 0; i < uuidsValues.length; i++) {
				uuidsValues[i] = DomUtils.getTextValue(uuids.get(i));
			}

			definitionBuilder.addPropertyValue(UUID, uuidsValues);
		}
	}

	/**
	 * Parses the &lt;jcr:sessionFactory&gt; configuration element.
	 */
	private class JcrSessionFactoryBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
		
		/* (non-Javadoc)
		 * @see org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser#getBeanClass
		 * (org.w3c.dom.Element)
		 */
		@Override
		protected Class<?> getBeanClass(Element element) {
			return JcrSessionFactory.class;
		}
		
		/* (non-Javadoc)
		 * @see org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser#doParse(org.w3c.dom.Element,
		 * org.springframework.beans.factory.support.BeanDefinitionBuilder)
		 */
		@Override
		protected void doParse(Element elem, BeanDefinitionBuilder builder) {
			String repository = elem.getAttribute("repository");
			if (!repository.isEmpty()) {
				builder.addPropertyReference("repository", repository);
			} else {
				throw new NullPointerException("<jcr:sessionFactory> must define repository");
			}
			
			String credentials = elem.getAttribute("credentials");
			if (!credentials.isEmpty()) {
				builder.addPropertyReference("credentials", credentials);
			}
			
			String keepNamespaces = elem.getAttribute("keepNamespaces");
			if (!keepNamespaces.isEmpty()) {
				builder.addPropertyValue("keepNamespaces", keepNamespaces);
			}
		}
	}
}
