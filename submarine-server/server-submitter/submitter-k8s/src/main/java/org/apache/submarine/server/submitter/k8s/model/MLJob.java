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

package org.apache.submarine.server.submitter.k8s.model;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1ObjectMeta;

/**
 * The abstract machine learning job for the CRD job
 */
public abstract class MLJob {
  @SerializedName("apiVersion")
  private String apiVersion;

  @SerializedName("kind")
  private String kind;

  @SerializedName("metadata")
  private V1ObjectMeta metadata;

  /**
   * Get the api with version
   * @return api with version
   */
  public String getApiVersion() {
    return apiVersion;
  }

  /**
   * Set the api with version
   * @param apiVersion api with version
   */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  /**
   * Get the kind
   * @return kind, Default is TFJob
   */
  public String getKind() {
    return kind;
  }

  /**
   * Set the CRD's name
   * @param kind the CRD's name
   */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /**
   * Get the metadata
   * @return meta
   */
  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  /**
   * Set metadata
   * @param metadata meta
   */
  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  /**
   * Get the resource's group name
   * @return group name
   */
  public String getGroup() {
    return apiVersion.split("/")[0];
  }

  /**
   * Get the resource's version
   * @return version
   */
  public String getVersion() {
    return apiVersion.split("/")[1];
  }
}
