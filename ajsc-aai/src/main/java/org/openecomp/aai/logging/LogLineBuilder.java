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

package org.openecomp.aai.logging;

import java.util.UUID;

public class LogLineBuilder {

	private String component;
	private final String transId;
	private final String fromAppId;
	private String operation;
	
	/**
	 * Instantiates a new log line builder.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 */
	public LogLineBuilder(String transId, String fromAppId) {
		this.transId = transId;
		this.fromAppId = fromAppId;
	}
	
	/**
	 * Instantiates a new log line builder.
	 */
	public LogLineBuilder() {
		this.transId = UUID.randomUUID().toString();
		this.fromAppId = "AAI";
	}
	
	/**
	 * Builds the.
	 *
	 * @param component the component
	 * @param operation the operation
	 * @return the log line
	 */
	public LogLine build(String component, String operation) {
		LogLine line = new LogLine();
		line.init(component, transId, fromAppId, operation);
		return line;
	}
	
}
