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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.att.nsa.mr.client.MRBatchingPublisher;
import com.att.nsa.mr.client.MRClientFactory;
import com.att.nsa.mr.client.MRPublisher.message;

public class AAIDmaapPublisher {
	private String publisherPropertyFile;

	private MRBatchingPublisher pub = null;

	public AAIDmaapPublisher(String publisherPropertyFile) throws FileNotFoundException, IOException {
		this.publisherPropertyFile = publisherPropertyFile;
		this.pub = MRClientFactory.createBatchingPublisher(this.publisherPropertyFile);

	}

	public List<message> publishAndClose(JSONObject payload) throws IOException, InterruptedException {
		this.pub.send(payload.toString());
		return pub.close(60, TimeUnit.SECONDS);
	}
	
	public MRBatchingPublisher getMRBatchingPublisher() {
		return this.pub;
	}

}
