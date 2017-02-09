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

package org.openecomp.aai.auth;

import javax.ws.rs.core.HttpHeaders;

import org.openecomp.aai.exceptions.AAIException;

/**
 * The Class AAIAuth.
 */
/*
 * this appears to be simply a wrapper class for AAIAuthCore
 * it just handled cookie stuff
 * and can return an error code
 * and forwards everything to AAIAuthCore to do the real work
 * since cookies appear not to be used, not sure if this class
 * is still needed, depending on other architectural needs
 */
public class AAIAuth  {
	
	/**
	 * Instantiates a new AAI auth.
	 */
	public AAIAuth() {}
	
	/**
	 * Auth user.
	 *
	 * @param headers the headers
	 * @param authUser the auth user
	 * @param auth_function the auth function
	 * @return the string
	 * @throws AAIException the AAI exception
	 */
	/*public String auth_user(HttpHeaders headers, String authUser, String auth_function) throws AAIException {
		if (AAIAuthCore.getInstance().authorize(authUser, auth_function)) {
			return "OK"; // this seems kinda brittle, since one could easily forget the caps & need to look it up in the caller
		} else {
			return "AAI_9101";
		}
	}*/
}
