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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIRSyncUtility;


public class AAIRSyncUtilityTest {

  AAIRSyncUtility syncUtil; 
  AAIRSyncUtility syncUtilOmitDoCommand;
  AAIConfig aaiConfig;
  String hostName;
  String transId = UUID.randomUUID().toString();
        
  /**
   * Initialize.
   */
  @Before
  public void initialize(){
    syncUtil = new AAIRSyncUtility();
                
    syncUtilOmitDoCommand = new AAIRSyncUtility(){
        /**
         * {@inheritDoc}
         */
        @Override
        public int doCommand(List<String> command) throws Exception 
        { 
          return 1;
        }
      };
                
    partialSetupForAAIConfig();
                
    InetAddress ip = null;
    try {
      ip = InetAddress.getLocalHost();
    } catch (UnknownHostException e2) {
      e2.printStackTrace();
    }
    hostName = ip.getHostName();
  }

        
  /**
   * Test sendRsync.
   */
  @Ignore
  @Test
  public void testSendRsyncCommand(){
    syncUtilOmitDoCommand.sendRsyncCommand(transId, "RandomFileName");
    //TODO write codes to check what is being logged 
  }
        
  /**
   * Test getHost.
   */
  @Test
  public void testGetHost(){
                
    String returnedHost = null;
    Method getHostMethod = makePublic("getHost");
    try {
      returnedHost = (String)getHostMethod.invoke(syncUtil, null);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
    }
                
    assertEquals("Host name didn't match", returnedHost, hostName);
  }
        
  /**
   * Test getRemoteHostList.
   */
  @Test
  public void testGetRemoteHostList(){
    String localHost = "host_local";
    String remoteHost1 = "hostR1";
    String remoteHost2 = "hostR2";
    ArrayList<String> remotes = new ArrayList<String>();
    remotes.add(remoteHost1);
    remotes.add(remoteHost2);
                
    StringTokenizer stTokenizer = new StringTokenizer(remoteHost1+"\r"+remoteHost2+"\r"+localHost);
                
    Method m = makePublic("getRemoteHostList");
    try {
      assertEquals("Remote host missing", remotes, m.invoke(syncUtil, stTokenizer, localHost));
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  /**
   * Test doCommand.
   */
  @Test
  public void testDoCommand(){
                
    assertTrue("Don't have execute permissions", Files.isExecutable(new File(".").toPath()));
                
    List<String> commands = new ArrayList<String>();
    commands.add("ping");
    commands.add("google.com");
    try {
      assertEquals("Failed to execute commands", 1, syncUtilOmitDoCommand.doCommand(commands));
    } catch (Exception e) {
      fail("Failed to execute a command");
      e.printStackTrace();
    }
                
  }
        
  /**
   * Test doCommand with null.
   */
  @Test
  public void testDoCommand_withNull(){
    assertTrue("Don't have execute permissions", Files.isExecutable(new File(".").toPath()));
    try {
      assertEquals("This should be unreachable", 1, syncUtil.doCommand(null));
    } catch (Exception e) {
      assertTrue("Expecting an NPE from ProcessBuilder", e instanceof NullPointerException);
    }
                
  }
        

  /**
   * Helper method to covert access type of a method from private to public .
   *
   * @param privateMethodName Method which is private originally
   * @return method object with 'access type = 'public'
   */
  public Method makePublic(String privateMethodName){
    Method targetMethod = null;
    try {
      if (privateMethodName.equals("getHost"))
        targetMethod = AAIRSyncUtility.class.getDeclaredMethod(privateMethodName, null);
      else if (privateMethodName.equals("getRemoteHostList"))
        targetMethod = AAIRSyncUtility.class.getDeclaredMethod(privateMethodName, StringTokenizer.class, String.class);
    } catch (NoSuchMethodException | SecurityException e) {
      e.printStackTrace();
    }
    targetMethod.setAccessible(true);
    return targetMethod;
  }
        
  /**
   * Helper method to load aai config from test configuration file
   * This requires that the 'test_aaiconfig.properties' file is available 
   */
  static void setFinalStatic(Field field, Object newValue) throws Exception {
    field.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    field.set(null, newValue);
  }
        
  /**
   * Helper method to setup AAIConfig for test.
   */
  public void partialSetupForAAIConfig(){
    try {
      setFinalStatic(AAIConfig.class.getDeclaredField("GlobalPropFileName"),  "src/test/resources/test_aaiconfig.properties");
    } 
    catch (SecurityException e) {fail();}
    catch (NoSuchFieldException e) {fail();}
    catch (Exception e) {fail();}
  
    AAIConfig.reloadConfig();
  }
	
}
