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

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.junit.Rule;
import org.junit.Test;
import org.openecomp.aai.util.AAIApiVersion;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.powermock.core.classloader.annotations.PrepareForTest;
import static org.mockito.Mockito.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;

@PrepareForTest({PhaseInterceptorChain.class, AAIConfig.class})

public class AAIApiVersionTest {
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	  
	  static { PowerMockAgent.initializeIfNeeded();}
	
	  /**
  	 * Test get version.
  	 *
  	 * @throws Exception the exception
  	 */
  	@Test
	  public void testGetVersion() throws Exception {
	    PowerMockito.mockStatic(PhaseInterceptorChain.class);
	    Message outMessage = new MessageImpl();
	    String msg = "/v2/";
	    outMessage.put(Message.REQUEST_URI, msg);
	    when(PhaseInterceptorChain.getCurrentMessage()).thenReturn(outMessage);
	    assertEquals("v2", AAIApiVersion.get()); 
	  }
	  
	  /**
  	 * Test get with null version.
  	 *
  	 * @throws Exception the exception
  	 */
  	@Test
	  public void testGetWithNullVersion() throws Exception {
	    PowerMockito.mockStatic(AAIConfig.class);
	    String defaultURI = "default-v2";
	    when(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP, AAIConstants.AAI_DEFAULT_API_VERSION)).thenReturn(defaultURI);
	    assertEquals(defaultURI, AAIApiVersion.get());
	  }
	  
	  /**
  	 * Test incorrect version pattern.
  	 *
  	 * @throws Exception the exception
  	 */
  	@Test
	  public void testIncorrectVersionPattern() throws Exception {
	    PowerMockito.mockStatic(PhaseInterceptorChain.class);
	    PowerMockito.mockStatic(AAIConfig.class);
	    Message outMessage = new MessageImpl();
	    String msg = "2.0.1";
	    String defaultURI = "default-v2";
	    outMessage.put(Message.REQUEST_URI, msg);
	    when(PhaseInterceptorChain.getCurrentMessage()).thenReturn(outMessage);
	    when(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP, AAIConstants.AAI_DEFAULT_API_VERSION)).thenReturn(defaultURI);
	    assertEquals(defaultURI, AAIApiVersion.get());
	  }

}
