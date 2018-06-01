/*
 * Copyright 2011-2014 the original author or authors.
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
package org.springframework.data.geo;

/**
 * Interface for {@link Metric}s that can be applied to a base scale.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @since 1.8
 */
public interface Metric {

 /**
  * Returns the multiplier to calculate metrics values from a base scale.
  * 
  * @return
  */
 double getMultiplier();
}