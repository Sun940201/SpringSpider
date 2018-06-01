/*
 * Copyright 2012-2017 the original author or authors.
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
package org.springframework.data.repository.config;

import java.util.Optional;

import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.util.Streamable;

/**
 * Configuration information for a single repository instance.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public interface RepositoryConfiguration<T extends RepositoryConfigurationSource> {

 /**
  * Returns the base packages that the repository was scanned under.
  *
  * @return
  */
 Streamable<String> getBasePackages();

 /**
  * Returns the interface name of the repository.
  *
  * @return
  */
 String getRepositoryInterface();

 /**
  * Returns the key to resolve a {@link QueryLookupStrategy} from eventually.
  *
  * @see QueryLookupStrategy.Key
  * @return
  */
 Object getQueryLookupStrategyKey();

 /**
  * Returns the location of the file containing Spring Data named queries.
  *
  * @return
  */
 Optional<String> getNamedQueriesLocation();

 /**
  * Returns the class name of the custom implementation.
  *
  * @return
  * @deprecated since 2.0. Use repository compositions by creating mixins.
  */
 @Deprecated
 String getImplementationClassName();

 /**
  * Returns the bean name of the custom implementation.
  *
  * @return
  * @deprecated since 2.0. Use repository compositions by creating mixins.
  */
 @Deprecated
 String getImplementationBeanName();

 /**
  * Returns the name of the repository base class to be used or {@literal null} if the store specific defaults shall be
  * applied.
  *
  * @return
  * @since 1.11
  */
 Optional<String> getRepositoryBaseClassName();

 /**
  * Returns the name of the repository factory bean class to be used.
  *
  * @return
  */
 String getRepositoryFactoryBeanClassName();

 /**
  * Returns the source of the {@link RepositoryConfiguration}.
  *
  * @return
  */
 Object getSource();

 /**
  * Returns the {@link RepositoryConfigurationSource} that backs the {@link RepositoryConfiguration}.
  *
  * @return
  */
 T getConfigurationSource();

 /**
  * Returns whether to initialize the repository proxy lazily.
  *
  * @return
  */
 boolean isLazyInit();

 /**
  * Returns the {@link TypeFilter}s to be used to exclude packages from repository scanning.
  *
  * @return
  */
 Streamable<TypeFilter> getExcludeFilters();
}