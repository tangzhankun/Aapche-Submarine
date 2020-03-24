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

import org.apache.submarine.server.JobManager;
import org.apache.submarine.server.api.JobSubmitter;
import org.apache.submarine.server.response.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * The API for retrieving job logs
 */
@Path(RestConstants.V1 + "/" + RestConstants.LOGS)
@Produces({MediaType.TEXT_PLAIN + "; " + RestConstants.CHARSET_UTF8})
public class JobLogsRestApi {
  private final Logger LOG = LoggerFactory.getLogger(JobLogsRestApi.class);
  @GET
  public Response getLog(@QueryParam(RestConstants.JOB_ID) String jobId) {
    if (jobId == null) {
      return new JsonResponse.Builder<String>(Response.Status.BAD_REQUEST)
          .success(false).result("Invalid job id.").build();
    }
    JobSubmitter submitter = JobManager.getInstance().getJobSubmitter(jobId);
    if (submitter == null) {
      return new JsonResponse.Builder<String>(Response.Status.INTERNAL_SERVER_ERROR)
          .success(false).result("Unknown error that job id not exists.").build();
    }
    InputStream input = submitter.getLogStream(JobManager.getInstance().getHistoryJob(jobId));
    if (input == null) {
      return new JsonResponse.Builder<String>(Response.Status.INTERNAL_SERVER_ERROR)
          .success(false).result("Unknown error.").build();
    }
    BufferedReader in = new BufferedReader(new InputStreamReader(input));

    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(
          OutputStream os) throws IOException,
          WebApplicationException {
        String line = null;
        Writer writer = new BufferedWriter(new OutputStreamWriter(os));
        while ((line = in.readLine()) != null) {
          writer.write(line + "\n");
          writer.flush();
        }
        in.close();
      }
    };
    return Response.ok(stream).build();
  }
}
