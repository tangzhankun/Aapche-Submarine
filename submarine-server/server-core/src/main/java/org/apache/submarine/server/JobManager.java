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

package org.apache.submarine.server;

import org.apache.submarine.commons.utils.SubmarineConfiguration;
import org.apache.submarine.server.api.JobHandler;
import org.apache.submarine.server.api.JobSubmitter;
import org.apache.submarine.server.api.exception.DuplicatedJobSubmittionException;
import org.apache.submarine.server.api.exception.InvalidSpecException;
import org.apache.submarine.server.api.exception.UnsupportedJobTypeException;
import org.apache.submarine.server.api.job.Job;
import org.apache.submarine.server.api.job.JobId;
import org.apache.submarine.server.api.spec.JobSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * It responsible for manage the job (CRUD) and cached the job.
 */
public class JobManager implements JobHandler {
  private static final Logger LOG = LoggerFactory.getLogger(JobManager.class);
  private static volatile JobManager manager;

  private final AtomicInteger jobCounter = new AtomicInteger(0);

  private final ConcurrentMap<JobId, Job> jobs = new ConcurrentHashMap<>();

  // Key is job identifier. Can be used to loop if the job can be submitted.
  private final ConcurrentMap<String, Job> identifierToJobs = new ConcurrentHashMap<>();

  // Key is job id string.
  private final ConcurrentMap<String, Job> idStringToJobs = new ConcurrentHashMap<>();

  private SubmitterManager submitterManager;

  /**
   * Get the singleton instance
   * @return object
   */
  public static JobManager getInstance() {
    if (manager == null) {
      synchronized (JobManager.class) {
        if (manager == null) {
          SubmarineConfiguration conf = SubmarineConfiguration.getInstance();
          SubmitterManager submitterManager = new SubmitterManager(conf);
          manager = new JobManager(submitterManager);
        }
      }
    }
    return manager;
  }

  private JobManager(SubmitterManager submitterManager) {
    this.submitterManager = submitterManager;
  }

  @Override
  public Job submitJob(JobSpec spec) throws UnsupportedJobTypeException,
      DuplicatedJobSubmittionException {
    if (!spec.validate()) {
      return null;
    }
    JobSubmitter submitter = submitterManager.getSubmitterByType(
        spec.getSubmitterSpec().getType());
    if (submitter == null) {
      throw new UnsupportedJobTypeException();
    }
    String identifier = generateIdentifier(spec);
    if (identifierToJobs.get(identifier) != null) {
      throw new DuplicatedJobSubmittionException(
          String.format("Job[namespace: %s, name: %s] name already exists." +
              " Please change a name or namespace.",
              spec.getSubmitterSpec().getNamespace(),
              spec.getName()));
    }
    Job job = new Job();
    job.setSpec(spec);
    job.setJobId(generateJobId());
    LOG.info("New job accepted: " + job.toString());
    try {
      Job temp = submitter.submitJob(spec);
      LOG.info("Try submit job: " + job.toString());
      job.setName(temp.getName());
      job.setIdentifier(
          spec.getSubmitterSpec().getNamespace() + "-" + temp.getName());
      jobs.putIfAbsent(job.getJobId(), job);
      identifierToJobs.putIfAbsent(job.getIdentifier(), job);
      idStringToJobs.put(job.getJobId().toString(), job);
      LOG.info("New job submitted successfully: " + job.toString());
    } catch (InvalidSpecException e) {
      LOG.error("Invalid job spec: " + spec + ", " + e.getMessage());
      return null;
    } catch (Exception e) {
      LOG.error("Unknown exception", e.getMessage(), e.getStackTrace());
      return null;
    }
    return job;
  }

  private JobId generateJobId() {
    return JobId.newInstance(SubmarineServer.getServerTimeStamp(), jobCounter.incrementAndGet());
  }

  public String generateIdentifier(JobSpec spec) {
    return spec.getSubmitterSpec().getNamespace() + "-" +
        spec.getLibrarySpec().getName() + "-" +
        spec.getName();
  }

  public Job getHistoryJob(String jobId) {
    return idStringToJobs.get(jobId);
  }

  public JobSubmitter getJobSubmitter(String jobId) {
    if (this.idStringToJobs.get(jobId) == null) {
      LOG.error("Unknown job id.");
      return null;
    }
    return submitterManager.getSubmitterByType(this.idStringToJobs.get(
        jobId).getSpec().getSubmitterSpec().getType());
  }

  public List<JobId> getJobs() {
    return new ArrayList<JobId>(jobs.keySet());
  }

}
