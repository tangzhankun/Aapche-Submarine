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

package org.apache.submarine.server.submitter.k8s;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1DeleteOptionsBuilder;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.submarine.commons.utils.SubmarineConfVars;
import org.apache.submarine.commons.utils.SubmarineConfiguration;
import org.apache.submarine.commons.utils.exception.SubmarineRuntimeException;
import org.apache.submarine.server.api.JobSubmitter;
import org.apache.submarine.server.api.exception.InvalidSpecException;
import org.apache.submarine.server.api.job.Job;
import org.apache.submarine.server.api.spec.JobLibrarySpec;
import org.apache.submarine.server.api.spec.JobSpec;
import org.apache.submarine.server.submitter.k8s.model.CustomResourceJob;
import org.apache.submarine.server.submitter.k8s.model.CustomResourceJobList;
import org.apache.submarine.server.submitter.k8s.model.MLJob;
import org.apache.submarine.server.submitter.k8s.parser.JobSpecParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * JobSubmitter for Kubernetes Cluster.
 */
public class K8sJobSubmitter implements JobSubmitter {
  private final Logger LOG = LoggerFactory.getLogger(K8sJobSubmitter.class);

  private static final String TF_JOB_SELECTOR_KEY = "tf-job-name=";
  private static final String PYTORCH_JOB_SELECTOR_KEY = "pytorch-job-name=";

  private String confPath;

  ApiClient client;

  // K8s API client for CRD
  private CustomObjectsApi api;

  public K8sJobSubmitter() {
  }

  public K8sJobSubmitter(String confPath) {
    this.confPath = confPath;
  }

  @Override
  public void initialize(SubmarineConfiguration conf) {
    if (confPath == null || confPath.trim().isEmpty()) {
      confPath = conf.getString(
          SubmarineConfVars.ConfVars.SUBMARINE_K8S_KUBE_CONFIG);
    }
    loadClientConfiguration(confPath);
    if (api == null) {
      api = new CustomObjectsApi();
    }
  }

  private void loadClientConfiguration(String path) {
    try {
      KubeConfig config = KubeConfig.loadKubeConfig(new FileReader(path));
      client = ClientBuilder.kubeconfig(config).build();
      Configuration.setDefaultApiClient(client);
    } catch (Exception e) {
      LOG.warn("Failed to load the configured K8s kubeconfig file: " +
          e.getMessage(), e);

      LOG.info("Assume running in the k8s cluster, " +
          "try to load in-cluster config");
      try {
        ApiClient client = ClientBuilder.cluster().build();
        Configuration.setDefaultApiClient(client);
      } catch (IOException e1) {
        throw new SubmarineRuntimeException("Failed to initialize k8s client");
      }
    }
  }

  @Override
  public String getSubmitterType() {
    return "k8s";
  }

  @Override
  public Job submitJob(JobSpec jobSpec)
      throws InvalidSpecException {
    Job job = null;

    boolean success = createJob(JobSpecParser.parseJob(jobSpec));
    if (success) {
      job = new Job();
      job.setName(jobSpec.getName());
    } else {
      LOG.error("Failed to create job." + jobSpec.toString());
    }
    return job;
  }

  @VisibleForTesting
  boolean createJob(MLJob job) {
    try {
      api.createNamespacedCustomObject(job.getGroup(), job.getVersion(),
          job.getMetadata().getNamespace(), job.getPlural(),
          job, "true");
    } catch (ApiException e) {
      LOG.error("Failed to create job. " + e.getMessage(), e);
      return false;
    }
    return true;
  }

  @VisibleForTesting
  CustomResourceJob createCustomJob(K8sJobRequest request) {
    try {
      K8sJobRequest.Path path = request.getPath();
      Object o = api.createNamespacedCustomObject(path.getGroup(),
          path.getApiVersion(), path.getNamespace(), path.getPlural(),
          request.getBody(), "true");
      Gson gson = new Gson();
      return gson.fromJson(gson.toJson(o), CustomResourceJob.class);
    } catch (ApiException ae) {
      LOG.error("Exceptions when creating CRD job: " + ae.getMessage(), ae);
    }
    return null;
  }

  @VisibleForTesting
  CustomResourceJob getCustomResourceJob(K8sJobRequest request) {
    try {
      K8sJobRequest.Path path = request.getPath();
      Object o = api.getNamespacedCustomObject(path.getGroup(),
          path.getApiVersion(),
          path.getNamespace(), path.getPlural(), request.getJobName());
      Gson gson = new Gson();
      return gson.fromJson(gson.toJson(o), CustomResourceJob.class);
    } catch (ApiException ae) {
      // The API getNamespacedCustomObject throws exception when cannot found resource
      // So the ApiException  seems not a big issue
      LOG.warn("Exceptions when getting CRD job: " + ae.getMessage());
    }
    return null;
  }

  @VisibleForTesting
  CustomResourceJob deleteCustomResourceJob(K8sJobRequest request) {
    try {
      K8sJobRequest.Path path = request.getPath();
      V1DeleteOptions body =
          new V1DeleteOptionsBuilder().withApiVersion(
              path.getApiVersion()).build();
      Object o = api.deleteNamespacedCustomObject(path.getGroup(),
          path.getApiVersion(), path.getNamespace(), path.getPlural(),
          request.getJobName(), body, null,
          null, null);
      Gson gson = new Gson();
      return gson.fromJson(gson.toJson(o), CustomResourceJob.class);
    } catch (ApiException ae) {
      LOG.error("Exceptions when deleting CRD job: " + ae.getMessage(), ae);
    }
    return null;
  }

  @VisibleForTesting
  CustomResourceJobList listCustomResourceJobs(K8sJobRequest request) {
    try {
      K8sJobRequest.Path path = request.getPath();
      Object o = api.listNamespacedCustomObject(path.getGroup(),
          path.getApiVersion(),
          path.getNamespace(), path.getPlural(), "true",
          null, null, null,
          null, null);
      Gson gson = new Gson();
      return gson.fromJson(gson.toJson(o), CustomResourceJobList.class);
    } catch (ApiException ae) {
      LOG.error("Exceptions when listing CRD jobs: " + ae.getMessage(), ae);
    }
    return null;
  }

  @Override
  public InputStream getLogStream(Job job) {
    if (job == null) {
      return null;
    }
    CoreV1Api coreApi = new CoreV1Api(client);
    PodLogs logs = new PodLogs();
    try {
      V1PodList podList = coreApi.listNamespacedPod(
          job.getSpec().getSubmitterSpec().getNamespace(),
          "false", null, null,
          getJobLabelSelector(job), null, null,
          null, null);
      V1Pod pod = podList.getItems().get(0);
      return logs.streamNamespacedPodLog(pod);
    } catch (ApiException e) {
      LOG.warn("Error when listing pod for job:" + job.toString(), e.getMessage());
    } catch (IOException e) {
      LOG.warn("Error when get pod log stream", e.getMessage());
    }
    return null;
  }

  public InputStream getLogStream() {
    CoreV1Api coreApi = new CoreV1Api(client);
    PodLogs logs = new PodLogs();
    try {
      V1PodList podList = coreApi.listNamespacedPod(
          "submarine",
          "false", null, null,
          "pytorch-job-name=mnist", null, null,
          null, null);
      V1Pod pod = podList.getItems().get(0);
      return logs.streamNamespacedPodLog(pod);
    } catch (ApiException e) {
      LOG.warn("Error when listing pod for job:", e.getMessage());
    } catch (IOException e) {
      LOG.warn("Error when get pod log stream", e.getMessage());
    }
    return null;
  }

  public String getJobLabelSelector(Job job) {
    if (job.getSpec().getLibrarySpec()
        .getName().equalsIgnoreCase(JobLibrarySpec.SupportedMLFramework.TENSORFLOW.getName())) {
      return TF_JOB_SELECTOR_KEY + job.getSpec().getName();
    } else {
      return PYTORCH_JOB_SELECTOR_KEY + job.getSpec().getName();
    }
  }
}
