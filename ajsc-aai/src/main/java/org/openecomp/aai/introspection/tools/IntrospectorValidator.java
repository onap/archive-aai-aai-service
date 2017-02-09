/*-
 * ============LICENSE_START=======================================================
 * org.openecomp.aai
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.openecomp.aai.introspection.tools;

import java.util.ArrayList;
import java.util.List;

import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorWalker;
import org.openecomp.aai.introspection.Wanderer;
import org.openecomp.aai.logging.LogLineBuilder;

public class IntrospectorValidator implements Wanderer {

	
	private List<Issue> issues = null;
	private List<IssueResolver> issueResolvers = null; 
	private boolean validateRequired = true;
	private final LogLineBuilder llBuilder;
	
	/**
	 * Instantiates a new introspector validator.
	 *
	 * @param builder the builder
	 */
	private IntrospectorValidator(IntrospectorValidator.Builder builder) {
		this.llBuilder = builder.getLogLineBuilder();
		this.validateRequired = builder.getValidateRequired();
		this.issueResolvers = builder.getResolvers();
		issues = new ArrayList<>();
	}
	
	/**
	 * Validate.
	 *
	 * @param obj the obj
	 * @return true, if successful
	 */
	public boolean validate(Introspector obj) {
		IntrospectorWalker walker = new IntrospectorWalker(this, llBuilder);
		walker.walk(obj);
		
		for (Issue m : issues) {
			if (!m.getSeverity().equals(Severity.WARNING)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Gets the issues.
	 *
	 * @return the issues
	 */
	public List<Issue> getIssues() {
		return this.issues;
	}
	
	/**
	 * Sets the issue resolvers.
	 *
	 * @param resolvers the new issue resolvers
	 */
	public void setIssueResolvers(List<IssueResolver> resolvers) {
		issueResolvers = new ArrayList<>();
		for (IssueResolver resolver : resolvers) {
			issueResolvers.add(resolver);
		}
	}
	
	/**
	 * Resolve issues.
	 *
	 * @return true, if successful
	 */
	public boolean resolveIssues() {
		boolean result = true;
		for (Issue issue : issues) {
			for (IssueResolver resolver : issueResolvers) {
				if (resolver.resolveIssue(issue)) {
					issue.setResolved(true);
				}
			}
			if (!issue.isResolved()) {
				result = false;
			}
		}
		
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processComplexObj(Introspector obj) {
		List<String> requiredProps = obj.getRequiredProperties();
		List<String> keys = obj.getKeys();
		
		requiredProps.removeAll(keys);
		if (validateRequired) {
			for (String prop : requiredProps) {
				Object value = obj.getValue(prop);
				if (value == null) {
					Issue message = 
							this.buildMessage(Severity.CRITICAL, Error.MISSING_REQUIRED_PROP, "Missing required property: " + prop);
					message.setIntrospector(obj);
					message.setPropName(prop);
					issues.add(message);
				}
			}
		}
		for (String prop : keys) {
			Object value = obj.getValue(prop);
			if (value == null) {
				Issue message = 
						this.buildMessage(Severity.CRITICAL, Error.MISSING_KEY_PROP, "Missing key property: " + prop);
				message.setIntrospector(obj);
				message.setPropName(prop);
				issues.add(message);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitive(String propName, Introspector obj) {
		//NO OP
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitiveList(String propName, Introspector obj) {
		//NO OP
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void modifyComplexList(List<Object> list, Introspector parent, Introspector child) {
		//NO OP
	}
	
	
	/**
	 * Builds the message.
	 *
	 * @param severity the severity
	 * @param error the error
	 * @param detail the detail
	 * @return the issue
	 */
	private Issue buildMessage(Severity severity, Error error, String detail) {
		Issue message = new Issue();
		message.setSeverity(severity);
		message.setError(error);
		message.setDetail(detail);
		
		return message;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean createComplexObjIfNull() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int createComplexListSize(Introspector parent, Introspector child) {
		return 0;
	}
	
	public static class Builder {
		
		private boolean validateRequired = true;
		private List<IssueResolver> issueResolvers = null;
		private final LogLineBuilder llBuilder;
		
		/**
		 * Instantiates a new builder.
		 *
		 * @param llBuilder the ll builder
		 */
		public Builder(LogLineBuilder llBuilder) {
			this.llBuilder = llBuilder;
			issueResolvers = new ArrayList<IssueResolver>();
		}
		
		/**
		 * Validate required.
		 *
		 * @param validateRequired the validate required
		 * @return the builder
		 */
		public Builder validateRequired(boolean validateRequired) {
			this.validateRequired = validateRequired;
			return this;
		}
		
		/**
		 * Adds the resolver.
		 *
		 * @param resolver the resolver
		 * @return the builder
		 */
		public Builder addResolver(IssueResolver resolver) {
			issueResolvers.add(resolver);
			return this;
		}
		
		/**
		 * Builds the.
		 *
		 * @return the introspector validator
		 */
		public IntrospectorValidator build() {
			return new IntrospectorValidator(this);
		}
		
		/**
		 * Gets the validate required.
		 *
		 * @return the validate required
		 */
		public boolean getValidateRequired() {
			return this.validateRequired;
		}
		
		/**
		 * Gets the resolvers.
		 *
		 * @return the resolvers
		 */
		public List<IssueResolver> getResolvers() {
			return this.issueResolvers;
		}
		
		/**
		 * Gets the log line builder.
		 *
		 * @return the log line builder
		 */
		public LogLineBuilder getLogLineBuilder() {
			return this.llBuilder;
		}
	}
	
}
