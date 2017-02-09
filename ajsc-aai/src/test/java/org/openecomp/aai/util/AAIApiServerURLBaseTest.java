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

package org.openecomp.aai.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openecomp.aai.util.AAIApiServerURLBase;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.powermock.core.classloader.annotations.PrepareForTest;
import static org.mockito.Mockito.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;

@PrepareForTest({PhaseInterceptorChain.class, AAIConfig.class})

public class AAIApiServerURLBaseTest {
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	
	static {
	     PowerMockAgent.initializeIfNeeded();
	 }
	
	 /**
 	 * Test get hostname.
 	 *
 	 * @throws Exception the exception
 	 */
 	@Test
	  public void testGetHostname() throws Exception {
	    PowerMockito.mockStatic(PhaseInterceptorChain.class);	    
	    Map <String, List<String>> hm = new HashMap<String, List<String>>();
	    List<String> host = new ArrayList<String>();
	    host.add("my-localhost");
	    hm.put("host", host);
	    
	    Message outMessage = new MessageImpl();
	    outMessage.put(Message.PROTOCOL_HEADERS, hm);
	    
	    when(PhaseInterceptorChain.getCurrentMessage()).thenReturn(outMessage);
	    assertEquals("https://my-localhost/aai/", AAIApiServerURLBase.get());
	  }
	 
	 /**
 	 * Test get with null hostname.
 	 *
 	 * @throws Exception the exception
 	 */
 	@Test
	  public void testGetWithNullHostname() throws Exception {
		 PowerMockito.mockStatic(AAIConfig.class);
	    String defaultHostname = "default-name";
	    when(AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE)).thenReturn(defaultHostname);
	    assertEquals(defaultHostname, AAIApiServerURLBase.get());
	  }	 
}
