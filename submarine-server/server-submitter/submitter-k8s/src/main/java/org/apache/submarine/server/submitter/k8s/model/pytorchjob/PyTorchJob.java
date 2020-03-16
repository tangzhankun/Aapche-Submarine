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

package org.apache.submarine.server.submitter.k8s.model.pytorchjob;

import com.google.gson.annotations.SerializedName;
import org.apache.submarine.server.submitter.k8s.model.MLJob;

public class PyTorchJob extends MLJob {

  public static final  String CRD_PYTORCH_KIND = "PyTorchJob";
  public static final  String CRD_PYTORCH_PLURAL = "pytorchjobs";
  public static final  String CRD_PYTORCH_GROUP = "kubeflow.org";
  public static final  String CRD_PYTORCH_VERSION = "v1";


  @SerializedName("spec")
  private PyTorchJobSpec spec;

  public PyTorchJob() {
    setApiVersion(CRD_PYTORCH_GROUP + "/" + CRD_PYTORCH_VERSION);
    setKind(CRD_PYTORCH_KIND);
  }

  /**
   * Get the job spec which contains PyTorchJob JSON CRD.
   *
   * @return job spec
   */
  public PyTorchJobSpec getSpec() {
    return spec;
  }

  /**
   * Set the spec
   *
   * @param spec job spec
   */
  public void setSpec(PyTorchJobSpec spec) {
    this.spec = spec;
  }
}
