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

package org.openecomp.aai.dmaap;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;

public class AAIDmaapEventJMSProducer {
	@Autowired
	private JmsTemplate jmsTemplate;

	public void sendMessageToDefaultDestination(final String transId, final String fromAppId, final String message) {
		JSONObject jo = new JSONObject();
		try {
			jo.put("transId", transId);
			jo.put("fromAppId", fromAppId);
			jo.put("aaiEvent", message);

			jmsTemplate.convertAndSend(jo.toString());
		} catch (JSONException e) {
		}
	}
}
