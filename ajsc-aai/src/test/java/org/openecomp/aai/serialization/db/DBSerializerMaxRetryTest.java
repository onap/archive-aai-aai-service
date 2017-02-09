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

package org.openecomp.aai.serialization.db;


import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.serialization.db.DBSerializer;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.thinkaurelius.titan.core.TitanException;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanTransaction;

@RunWith(PowerMockRunner.class)
@PrepareForTest({QueryParser.class, TransactionalGraphEngine.class, TitanGraph.class, TitanTransaction.class, LoaderFactory.class, AAILogger.class, DBSerializer.class})
public class DBSerializerMaxRetryTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	/**
	 * Test.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test
	public void test() throws Exception {
		
		QueryParser mockQuery = PowerMockito.mock(QueryParser.class);
		PowerMockito.when(mockQuery.isDependent()).thenReturn(true);
		
		TransactionalGraphEngine mockEngine = PowerMockito.mock(TransactionalGraphEngine.class);
		PowerMockito.when(mockEngine.getQueryEngine()).thenThrow(new TitanException("mock error"));
		
		TitanGraph mockGraph = PowerMockito.mock(TitanGraph.class);
		PowerMockito.when(mockEngine.getGraph()).thenReturn(mockGraph);
		TitanTransaction mockGraphTrans = PowerMockito.mock(TitanTransaction.class);
		PowerMockito.when(mockGraph.newTransaction()).thenReturn(mockGraphTrans);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		PowerMockito.mockStatic(LoaderFactory.class);
		
		DBSerializer dbs = new DBSerializer(mockEngine, mockGraph, null, null, llb);
		
		thrown.expect(AAIException.class);
		thrown.expectMessage("AAI_6134");
		dbs.serializeToDb(null, null, mockQuery, null);
	}

}
