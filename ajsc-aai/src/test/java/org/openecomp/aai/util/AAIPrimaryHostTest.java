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
 
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
 
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIPrimaryHost;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
 
@PrepareForTest({InetAddress.class, AAIPrimaryHost.class})
public class AAIPrimaryHostTest {
	
      
  @Rule
  public PowerMockRule rule = new PowerMockRule();
      
  static {
    PowerMockAgent.initializeIfNeeded();
  }
      
      
  AAIPrimaryHost obj = null;
  private static final String transId = UUID.randomUUID().toString();
  private static final String fromAppId = "AAIPrimaryHostTest";
      
  /**
   * Initialize.
   */
  @Before
  public void initialize(){
    partialSetupForAAIConfig();
    obj = new AAIPrimaryHost(transId, fromAppId);
      
  }

  /**
   * Test do commandwith failure.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDoCommandwithFailure() throws Exception {
    List<String> myCommands = new ArrayList<String>();
    String command = "some-command";
    myCommands.add(command);
    try {
      obj.doCommand(myCommands);
    }
    catch (Exception e){
      assertTrue(e.getMessage().contains("Cannot run program \"some-command\""));
    }
  }
      
  /**
   * Test am I primary with random key name.
   */
  @Test
  public void testAmIPrimary_withRandomKeyName(){
    assertTrue("If key isn't found in the config file, log exception and return true.", obj.amIPrimary("randomName"));
  }
      
  /**
   * Test am I primary with DEFAUL T CHEC K for localhost.
   */
  @Test
  public void testAmIPrimary_with_DEFAULT_CHECK_for_localhost(){
    assertTrue("localhost name should not be in the server list", obj.amIPrimary(new String("aai.primary.filetransfer.")));
  }
      
      
  /**
   * Test am I primary with DEFAUL T CHEC K for valid host echo success.
   */
  @Test
  public void testAmIPrimary_with_DEFAULT_CHECK_for_valid_host_echo_success(){
    mockIP();
    obj = getTestObject(true, "");
    assertTrue("host name should exist in the server list in echo success", obj.amIPrimary(new String("aai.primary.filetransfer.")));
  }
  
  /**
   * Test which is primary when missingconfig properties.
   *
   * @throws Exception the exception
   */
  @Test
  public void testWhichIsPrimaryWhenMissingconfigProperties() throws Exception {
    assertEquals(null, obj.whichIsPrimary("checkName"));
  }
  
  /**
   * Test which is primary.
   *
   * @throws Exception the exception
   */
  @Test
  public void testWhichIsPrimary() throws Exception {
    AAIPrimaryHost primaryHost = getTestObject(true, "primaryHost");
    assertEquals("primaryHost", primaryHost.whichIsPrimary("aai.primary.filetransfer."));
  }

  
      
  /**
   * Mock IP.
   */
  public void mockIP(){
             
    PowerMockito.mockStatic(InetAddress.class);
    InetAddress dummyInetAddress = null;
    try {
      Mockito.when(InetAddress.getLocalHost()).thenReturn(dummyInetAddress);
      Mockito.when(dummyInetAddress.getHostAddress()).thenReturn("localhost");
    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
             
  }
      
  /**
   * Gets the test object.
   *
   * @param echoStatus the echo status
   * @param host the host
   * @return the test object
   */
  public AAIPrimaryHost getTestObject(final boolean echoStatus, final String host){
    return new AAIPrimaryHost(transId, fromAppId){
      @Override
      public boolean amIPrimaryUsingEcho( String hostname, String aaiPrimaryCheck, String aaiServerList) {
        return echoStatus;
      }
      
      @Override
      public String whichIsPrimaryUsingEcho( String aaiPrimaryCheck, String aaiServerList) {
        return "primaryHost";
      }
  
    };
  }
 
 
  static void setFinalStatic(Field field, Object newValue) throws Exception {
    field.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    field.set(null, newValue);
  }
      
  /**
   * Partial setup for AAI config.
   */
  public void partialSetupForAAIConfig(){
    try {
      setFinalStatic(AAIConfig.class.getDeclaredField("GlobalPropFileName"),  "src/test/resources/test_aaiconfig.properties");
    }
    catch (Exception e) {fail();}
    
    AAIConfig.reloadConfig();
  }
      
      
}
