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

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1NamespaceList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import org.apache.submarine.server.submitter.k8s.model.CustomResourceJob;
import org.apache.submarine.server.submitter.k8s.model.CustomResourceJobList;
import org.apache.submarine.server.submitter.k8s.model.pytorchjob.PyTorchJob;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * We have two ways to test submitter for K8s cluster, local and travis CI.
 *
 * For running the tests locally, ensure that:
 * 1. There's a k8s cluster running somewhere
 * 2. There's a "config" file as ~/.kube/config targeting the cluster
 * 3. A namespace submarine was created
 * 3. The CRDs was created beforehand. The operator doesn't needs to be running.
 *
 * Use "kubectl -n submarine get tfjob" or "kubectl -n submarine get pytorchjob"
 * to check the status if you comment the deletion job code in method "after()"
 *
 *
 * For the travis CI, we use the kind to setup K8s, more info see '.travis.yml' file.
 *  Local: docker run -it --privileged -p 8443:8443 -p 10080:10080 bsycorp/kind:latest-1.15
 *  Travis: See '.travis.yml'
 *
 */
public class K8SJobSubmitterTest {
  private final String tfJobName = "mnist";
  private final String pytorchJobName = "pytorch-dist-mnist-gloo";

  // The spec files in test/resources
  private final String tfJobSpecfile = "/tf_job_mnist.json";
  private final String pytorchJobSpecfile = "/pytorch_job_mnist_gloo.json";

  private K8sJobSubmitter submitter;

  private K8sJobRequest.Path tfPath;
  private K8sJobRequest.Path pyTorchPath;

  @Before
  public void before() throws IOException, ApiException {
    String confPath = System.getProperty("user.home") + "/.kube/config";
    if (!new File(confPath).exists()) {
      throw new IOException("Get kube config file failed.");
    }
    submitter = new K8sJobSubmitter(confPath);
    submitter.initialize(null);
    String ns = "submarine";
    if (!isEnvReady()){
      throw new ApiException(" Please create a namespace 'submarine'.");
    }
    tfPath = new K8sJobRequest.Path("kubeflow.org", "v1", ns, "tfjobs");
    pyTorchPath = new K8sJobRequest.Path(PyTorchJob.CRD_PYTORCH_GROUP,
      PyTorchJob.CRD_PYTORCH_VERSION, ns, PyTorchJob.CRD_PYTORCH_PLURAL);
  }

  private boolean isEnvReady() throws ApiException {
    CoreV1Api api = new CoreV1Api();
    V1NamespaceList list = api.listNamespace(null, null, null, null, null, null, null, null);
    for (V1Namespace item : list.getItems()) {
      if(item.getMetadata().getName().equals("submarine")) {
        return true;
      }
    }
    return false;
  }

  // Delete the job might take time
  @After
  public void after() {
    try {
      tryDeleteCustomJob(tfPath, tfJobName);
      tryDeleteCustomJob(pyTorchPath, pytorchJobName);
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testTFJob() throws URISyntaxException {
    tryCreateCustomJob(tfPath, tfJobName, tfJobSpecfile);
    CustomResourceJob job = getCustomJob(tfPath, tfJobName);
    Assert.assertNotNull(job);
    Assert.assertEquals(tfJobName, job.getMetadata().getName());
    CustomResourceJobList jobList = listCustomJobs(tfPath, tfJobSpecfile);
    Assert.assertEquals(1, jobList.getItems().size());
  }

  @Test
  public void testPyTorchJob() throws URISyntaxException {
    tryCreateCustomJob(pyTorchPath, pytorchJobName, pytorchJobSpecfile);
    CustomResourceJob job = getCustomJob(pyTorchPath, pytorchJobName);
    Assert.assertNotNull(job);
    Assert.assertEquals(pytorchJobName, job.getMetadata().getName());
  }

  public CustomResourceJob tryCreateCustomJob(K8sJobRequest.Path requestPath,
    String jobName, String jobSpecFile) throws URISyntaxException {
    CustomResourceJob job = getCustomJob(requestPath, jobName);
    if (job != null) {
      return job;
    }
    return submitter.createCustomJob(
      new K8sJobRequest(requestPath, getCustomJobSpecFile(jobSpecFile)));
  }

  public CustomResourceJobList listCustomJobs(K8sJobRequest.Path requestPath,
    String jobSpecFile) throws URISyntaxException {
    return submitter.listCustomResourceJobs(
          new K8sJobRequest(tfPath, getCustomJobSpecFile(jobSpecFile)));
  }

  public CustomResourceJob tryDeleteCustomJob(K8sJobRequest.Path requestPath,
    String jobName) {
    if (getCustomJob(requestPath, jobName) != null) {
      K8sJobRequest request = new K8sJobRequest(tfPath, null, tfJobName);
      return submitter.deleteCustomResourceJob(request);
    }
    return null;
  }

  private CustomResourceJob getCustomJob(K8sJobRequest.Path requestPath, String jobName) {
    K8sJobRequest request = new K8sJobRequest(requestPath, null, jobName);
    return submitter.getCustomResourceJob(request);
  }

  private File getCustomJobSpecFile(String path) throws URISyntaxException {
    URL fileUrl = this.getClass().getResource(path);
    return new File(fileUrl.toURI());
  }
}
