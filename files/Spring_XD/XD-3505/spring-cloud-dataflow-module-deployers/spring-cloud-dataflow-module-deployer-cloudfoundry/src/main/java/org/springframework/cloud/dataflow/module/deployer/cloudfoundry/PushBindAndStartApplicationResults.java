/*
 * Copyright 2015 the original author or authors.
 *
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
 */

package org.springframework.cloud.dataflow.module.deployer.cloudfoundry;

/**
 * Results from {@link CloudFoundryApplicationOperations#pushBindAndStartApplication(PushBindAndStartApplicationParameters) pushBindAndStartApplication()} operation.
 *
 * @author Steve Powell
 */
class PushBindAndStartApplicationResults {

 private boolean createSucceeded;

 public boolean isCreateSucceeded() {
  return createSucceeded;
 }

 public PushBindAndStartApplicationResults withCreateSucceeded(boolean createSucceeded) {
  this.createSucceeded = createSucceeded;
  return this;
 }

}