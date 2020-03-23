/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.submarine.server.rest;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.submarine.server.AbstractSubmarineServerTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class JobLogRestApiTest extends AbstractSubmarineServerTest {

  private static final Logger LOG = LoggerFactory.getLogger(JobLogRestApiTest.class);

  @BeforeClass
  public static void init() throws Exception {
    AbstractSubmarineServerTest.startUp(JobLogRestApiTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractSubmarineServerTest.shutDown();
  }

  @Test
  public void TestGetJobLogByJobId() throws IOException {
    String jobId = "job_1584888903731_0001";
    GetMethod response = httpGet("/api/" + RestConstants.V1 + "/"
        + RestConstants.LOGS + "?id=" + jobId);
    LOG.info(response.toString());

  }
}
