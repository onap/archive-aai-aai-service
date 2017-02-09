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

package org.openecomp.aai.exceptions;

import java.util.ArrayList;
import java.util.List;

import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;

public class AAIException extends Exception {

	protected ErrorObject errorObject = ErrorObject.DefaultErrorObject;
	//protected Throwable cause; we don't need to have a cuase here - just set the parents class's cause
	private List<String> templateVars = new ArrayList<>();
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Instantiates a new AAI exception.
	 */
	public AAIException() {
	}

	/**
	 * Instantiates a new AAI exception.
	 *
	 * @param code the code
	 */
	public AAIException(String code) {
		super(code);
		errorObject = ErrorLogHelper.getErrorObject(code);
	}
	
	/**
	 * Instantiates a new AAI exception.
	 *
	 * @param code the code
	 * @param details the details
	 */
	public AAIException(String code, String details) {
		this(code);
		errorObject.setDetails(details);
	}

	/**
	 * Instantiates a new AAI exception.
	 *
	 * @param code the code
	 * @param cause the cause
	 */
	public AAIException(String code, Throwable cause) {
		this(code);
		if (cause != null) {
			if (cause.getMessage() == null)
				errorObject.setDetails(cause.toString());
			else
				errorObject.setDetails(cause.getMessage());
		}
		this.initCause(cause);
	}
	
	/**
	 * Instantiates a new AAI exception.
	 *
	 * @param code the code
	 * @param cause the cause
	 * @param details the details
	 */
	public AAIException(String code, Throwable cause, String details) {
		this(code);
		if (cause != null) {
			if (cause.getMessage() == null)
				errorObject.setDetails(cause.toString() + "-" + details);
			else
				errorObject.setDetails(cause.getMessage() + "-" + details);
		}
		this.initCause(cause);
	}
	
	/**
	 * Gets the error object.
	 *
	 * @return the error object
	 */
	public ErrorObject getErrorObject() {
		return this.errorObject;
	}
	
	/**
	 * Gets the stack top.
	 *
	 * @return the stack top
	 */
	public String getStackTop() {
		StringBuffer stackMessage = new StringBuffer();
		stackMessage.append("");
		if( this.getCause() != null) {
			StackTraceElement[] elements = this.getCause().getStackTrace();
			int i = 0;
			for(StackTraceElement element : elements){
				if(i < 3){
					stackMessage.append(" ClassName- ");
					stackMessage.append(element.getClassName());
					stackMessage.append(" :LineNumber- ");
					stackMessage.append(element.getLineNumber());
					stackMessage.append(" :MethodName- ");
					stackMessage.append(element.getMethodName());
				}
				i++;
			}
		}
		return stackMessage.toString();
	}

	/**
	 * Gets the template vars.
	 *
	 * @return the template vars
	 */
	public List<String> getTemplateVars() {
		return templateVars;
	}

	/**
	 * Sets the template vars.
	 *
	 * @param templateVars the new template vars
	 */
	public void setTemplateVars(List<String> templateVars) {
		this.templateVars = templateVars;
	}

	/*
	public String toString() {
		StringBuffer response = new StringBuffer(super.toString());
		if (errorObject != null) 
			response.append(" ").append(errorObject.toString());
		if (cause != null)
			response.append(" cause=").append(getStackTop());
		return response.toString();
	}
	*/
}
