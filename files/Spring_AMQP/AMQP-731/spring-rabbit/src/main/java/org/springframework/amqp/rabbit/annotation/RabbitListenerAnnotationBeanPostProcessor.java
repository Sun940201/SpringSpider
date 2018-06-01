/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.amqp.rabbit.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RabbitListenerConfigUtils;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.MultiMethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Bean post-processor that registers methods annotated with {@link RabbitListener}
 * to be invoked by a AMQP message listener container created under the cover
 * by a {@link org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory}
 * according to the parameters of the annotation.
 *
 * <p>Annotated methods can use flexible arguments as defined by {@link RabbitListener}.
 *
 * <p>This post-processor is automatically registered by Spring's
 * {@code <rabbit:annotation-driven>} XML element, and also by the {@link EnableRabbit}
 * annotation.
 *
 * <p>Auto-detect any {@link RabbitListenerConfigurer} instances in the container,
 * allowing for customization of the registry to be used, the default container
 * factory or for fine-grained control over endpoints registration. See
 * {@link EnableRabbit} Javadoc for complete usage details.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @author Gary Russell
 * @since 1.4
 * @see RabbitListener
 * @see EnableRabbit
 * @see RabbitListenerConfigurer
 * @see RabbitListenerEndpointRegistrar
 * @see RabbitListenerEndpointRegistry
 * @see org.springframework.amqp.rabbit.listener.RabbitListenerEndpoint
 * @see MethodRabbitListenerEndpoint
 */
public class RabbitListenerAnnotationBeanPostProcessor
  implements BeanPostProcessor, Ordered, BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware,
  SmartInitializingSingleton {

 /**
  * The bean name of the default {@link org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory}.
  */
 public static final String DEFAULT_RABBIT_LISTENER_CONTAINER_FACTORY_BEAN_NAME = "rabbitListenerContainerFactory";

 public static final String RABBIT_EMPTY_STRING_ARGUMENTS_PROPERTY = "spring.rabbitmq.emptyStringArguments";

 private static final ConversionService CONVERSION_SERVICE = new DefaultConversionService();

 private final Log logger = LogFactory.getLog(this.getClass());

 private final Set<String> emptyStringArguments = new HashSet<>();

 private RabbitListenerEndpointRegistry endpointRegistry;

 private String containerFactoryBeanName = DEFAULT_RABBIT_LISTENER_CONTAINER_FACTORY_BEAN_NAME;

 private BeanFactory beanFactory;

 private ClassLoader beanClassLoader;

 private final RabbitHandlerMethodFactoryAdapter messageHandlerMethodFactory =
   new RabbitHandlerMethodFactoryAdapter();

 private final RabbitListenerEndpointRegistrar registrar = new RabbitListenerEndpointRegistrar();

 private final AtomicInteger counter = new AtomicInteger();

 private final MultiValueMap<Class<?>, Method> methodCache = new LinkedMultiValueMap<>();

 private final MultiValueMap<Method, RabbitListener> annotationCache = new LinkedMultiValueMap<>();

 private final MultiValueMap<Class<?>, RabbitListener> classAnnotationCache = new LinkedMultiValueMap<>();

 private final Map<Class<?>, List<Method>> multiMethodCache = new LinkedHashMap<>();

 private BeanExpressionResolver resolver = new StandardBeanExpressionResolver();

 private BeanExpressionContext expressionContext;

 private int increment;

 @Override
 public int getOrder() {
  return LOWEST_PRECEDENCE;
 }

 public RabbitListenerAnnotationBeanPostProcessor() {
  this.emptyStringArguments.add("x-dead-letter-exchange");
 }

 /**
  * Set the {@link RabbitListenerEndpointRegistry} that will hold the created
  * endpoint and manage the lifecycle of the related listener container.
  * @param endpointRegistry the {@link RabbitListenerEndpointRegistry} to set.
  */
 public void setEndpointRegistry(RabbitListenerEndpointRegistry endpointRegistry) {
  this.endpointRegistry = endpointRegistry;
 }

 /**
  * Set the name of the {@link RabbitListenerContainerFactory} to use by default.
  * <p>If none is specified, "rabbitListenerContainerFactory" is assumed to be defined.
  * @param containerFactoryBeanName the {@link RabbitListenerContainerFactory} bean name.
  */
 public void setContainerFactoryBeanName(String containerFactoryBeanName) {
  this.containerFactoryBeanName = containerFactoryBeanName;
 }

 /**
  * Set the {@link MessageHandlerMethodFactory} to use to configure the message
  * listener responsible to serve an endpoint detected by this processor.
  * <p>By default, {@link DefaultMessageHandlerMethodFactory} is used and it
  * can be configured further to support additional method arguments
  * or to customize conversion and validation support. See
  * {@link DefaultMessageHandlerMethodFactory} Javadoc for more details.
  * @param messageHandlerMethodFactory the {@link MessageHandlerMethodFactory} instance.
  */
 public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
  this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(messageHandlerMethodFactory);
 }

 /**
  * Making a {@link BeanFactory} available is optional; if not set,
  * {@link RabbitListenerConfigurer} beans won't get autodetected and an
  * {@link #setEndpointRegistry endpoint registry} has to be explicitly configured.
  * @param beanFactory the {@link BeanFactory} to be used.
  */
 @Override
 public void setBeanFactory(BeanFactory beanFactory) {
  this.beanFactory = beanFactory;
  if (beanFactory instanceof ConfigurableListableBeanFactory) {
   this.resolver = ((ConfigurableListableBeanFactory) beanFactory).getBeanExpressionResolver();
   this.expressionContext = new BeanExpressionContext((ConfigurableListableBeanFactory) beanFactory, null);
  }
 }

 @Override
 public void setBeanClassLoader(ClassLoader classLoader) {
  this.beanClassLoader = classLoader;
 }

 @Override
 public void setEnvironment(Environment environment) {
  String property = environment.getProperty(RABBIT_EMPTY_STRING_ARGUMENTS_PROPERTY, String.class);
  if (property != null) {
   this.emptyStringArguments.addAll(StringUtils.commaDelimitedListToSet(property));
  }
 }

 @Override
 public void afterSingletonsInstantiated() {
  this.registrar.setBeanFactory(this.beanFactory);

  if (this.beanFactory instanceof ListableBeanFactory) {
   Map<String, RabbitListenerConfigurer> instances =
     ((ListableBeanFactory) this.beanFactory).getBeansOfType(RabbitListenerConfigurer.class);
   for (RabbitListenerConfigurer configurer : instances.values()) {
    configurer.configureRabbitListeners(this.registrar);
   }
  }

  if (this.registrar.getEndpointRegistry() == null) {
   if (this.endpointRegistry == null) {
    Assert.state(this.beanFactory != null,
      "BeanFactory must be set to find endpoint registry by bean name");
    this.endpointRegistry = this.beanFactory.getBean(
      RabbitListenerConfigUtils.RABBIT_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME,
      RabbitListenerEndpointRegistry.class);
   }
   this.registrar.setEndpointRegistry(this.endpointRegistry);
  }

  if (this.containerFactoryBeanName != null) {
   this.registrar.setContainerFactoryBeanName(this.containerFactoryBeanName);
  }

  // Set the custom handler method factory once resolved by the configurer
  MessageHandlerMethodFactory handlerMethodFactory = this.registrar.getMessageHandlerMethodFactory();
  if (handlerMethodFactory != null) {
   this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(handlerMethodFactory);
  }

  // Actually register all listeners
  this.registrar.afterPropertiesSet();

  // clear the cache - prototype beans will be re-cached.
  this.classAnnotationCache.clear();
  this.annotationCache.clear();
  this.methodCache.clear();
  this.multiMethodCache.clear();
 }


 @Override
 public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
  return bean;
 }

 @Override
 public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
  Class<?> targetClass = AopUtils.getTargetClass(bean);
  if (this.methodCache.get(targetClass) == null) {
   findMethods(targetClass);
  }
  List<RabbitListener> classLevelListeners = this.classAnnotationCache.get(targetClass);
  List<Method> methods = this.methodCache.get(targetClass);
  List<Method> multiMethods = this.multiMethodCache.get(targetClass);
  for (Method method : methods) {
   List<RabbitListener> listenerAnnotations = this.annotationCache.get(method);
   if (listenerAnnotations != null) {
    for (RabbitListener rabbitListener : listenerAnnotations) {
     processAmqpListener(rabbitListener, method, bean, beanName);
    }
   }
  }
  if (multiMethods.size() > 0) {
   processMultiMethodListeners(classLevelListeners, multiMethods, bean, beanName);
  }
  return bean;
 }

 private void findMethods(Class<?> targetClass) {
  Collection<RabbitListener> classLevelListeners = findListenerAnnotations(targetClass);
  final boolean hasClassLevelListeners = classLevelListeners.size() > 0;
  final MultiValueMap<Method, RabbitListener> annotations = new LinkedMultiValueMap<>();
  final List<Method> methods = new ArrayList<>();
  final List<Method> multiMethods = new ArrayList<>();
  ReflectionUtils.doWithMethods(targetClass, method -> {
   Collection<RabbitListener> listenerAnnotations = findListenerAnnotations(method);
   if (listenerAnnotations.size() > 0) {
    methods.add(method);
    annotations.put(method, new ArrayList<>(listenerAnnotations));
   }
   if (hasClassLevelListeners) {
    RabbitHandler rabbitHandler = AnnotationUtils.findAnnotation(method, RabbitHandler.class);
    if (rabbitHandler != null) {
     multiMethods.add(method);
    }
   }
  }, ReflectionUtils.USER_DECLARED_METHODS);
  this.methodCache.put(targetClass, methods);
  for (Method method : methods) {
   this.annotationCache.put(method, annotations.get(method));
  }
  this.classAnnotationCache.put(targetClass, new ArrayList<>(classLevelListeners));
  this.multiMethodCache.put(targetClass, multiMethods);
 }

 /*
  * AnnotationUtils.getRepeatableAnnotations does not look at interfaces
  */
 private Collection<RabbitListener> findListenerAnnotations(Class<?> clazz) {
  Set<RabbitListener> listeners = new HashSet<>();
  RabbitListener ann = AnnotationUtils.findAnnotation(clazz, RabbitListener.class);
  if (ann != null) {
   listeners.add(ann);
  }
  RabbitListeners anns = AnnotationUtils.findAnnotation(clazz, RabbitListeners.class);
  if (anns != null) {
   listeners.addAll(Arrays.asList(anns.value()));
  }
  return listeners;
 }

 /*
  * AnnotationUtils.getRepeatableAnnotations does not look at interfaces
  */
 private Collection<RabbitListener> findListenerAnnotations(Method method) {
  Set<RabbitListener> listeners = new HashSet<RabbitListener>();
  RabbitListener ann = AnnotationUtils.findAnnotation(method, RabbitListener.class);
  if (ann != null) {
   listeners.add(ann);
  }
  RabbitListeners anns = AnnotationUtils.findAnnotation(method, RabbitListeners.class);
  if (anns != null) {
   listeners.addAll(Arrays.asList(anns.value()));
  }
  return listeners;
 }

 private void processMultiMethodListeners(Collection<RabbitListener> classLevelListeners, List<Method> multiMethods,
   Object bean, String beanName) {
  List<Method> checkedMethods = new ArrayList<Method>();
  for (Method method : multiMethods) {
   checkedMethods.add(checkProxy(method, bean));
  }
  for (RabbitListener classLevelListener : classLevelListeners) {
   MultiMethodRabbitListenerEndpoint endpoint = new MultiMethodRabbitListenerEndpoint(checkedMethods, bean);
   endpoint.setBeanFactory(this.beanFactory);
   processListener(endpoint, classLevelListener, bean, bean.getClass(), beanName);
  }
 }

 protected void processAmqpListener(RabbitListener rabbitListener, Method method, Object bean, String beanName) {
  Method methodToUse = checkProxy(method, bean);
  MethodRabbitListenerEndpoint endpoint = new MethodRabbitListenerEndpoint();
  endpoint.setMethod(methodToUse);
  endpoint.setBeanFactory(this.beanFactory);
  endpoint.setReturnExceptions(resolveExpressionAsBoolean(rabbitListener.returnExceptions()));
  String errorHandlerBeanName = resolveExpressionAsString(rabbitListener.errorHandler(), "errorHandler");
  if (StringUtils.hasText(errorHandlerBeanName)) {
   endpoint.setErrorHandler(this.beanFactory.getBean(errorHandlerBeanName, RabbitListenerErrorHandler.class));
  }
  processListener(endpoint, rabbitListener, bean, methodToUse, beanName);
 }

 private Method checkProxy(Method method, Object bean) {
  if (AopUtils.isJdkDynamicProxy(bean)) {
   try {
    // Found a @RabbitListener method on the target class for this JDK proxy ->
    // is it also present on the proxy itself?
    method = bean.getClass().getMethod(method.getName(), method.getParameterTypes());
    Class<?>[] proxiedInterfaces = ((Advised) bean).getProxiedInterfaces();
    for (Class<?> iface : proxiedInterfaces) {
     try {
      method = iface.getMethod(method.getName(), method.getParameterTypes());
      break;
     }
     catch (NoSuchMethodException noMethod) {
     }
    }
   }
   catch (SecurityException ex) {
    ReflectionUtils.handleReflectionException(ex);
   }
   catch (NoSuchMethodException ex) {
    throw new IllegalStateException(String.format(
      "@RabbitListener method '%s' found on bean target class '%s', " +
        "but not found in any interface(s) for bean JDK proxy. Either " +
        "pull the method up to an interface or switch to subclass (CGLIB) " +
        "proxies by setting proxy-target-class/proxyTargetClass " +
        "attribute to 'true'", method.getName(), method.getDeclaringClass().getSimpleName()));
   }
  }
  return method;
 }

 protected void processListener(MethodRabbitListenerEndpoint endpoint, RabbitListener rabbitListener, Object bean,
   Object adminTarget, String beanName) {
  endpoint.setBean(bean);
  endpoint.setMessageHandlerMethodFactory(this.messageHandlerMethodFactory);
  endpoint.setId(getEndpointId(rabbitListener));
  endpoint.setQueueNames(resolveQueues(rabbitListener));
  String group = rabbitListener.group();
  if (StringUtils.hasText(group)) {
   Object resolvedGroup = resolveExpression(group);
   if (resolvedGroup instanceof String) {
    endpoint.setGroup((String) resolvedGroup);
   }
  }

  endpoint.setExclusive(rabbitListener.exclusive());
  String priority = resolve(rabbitListener.priority());
  if (StringUtils.hasText(priority)) {
   try {
    endpoint.setPriority(Integer.valueOf(priority));
   }
   catch (NumberFormatException ex) {
    throw new BeanInitializationException("Invalid priority value for " +
      rabbitListener + " (must be an integer)", ex);
   }
  }

  String rabbitAdmin = resolve(rabbitListener.admin());
  if (StringUtils.hasText(rabbitAdmin)) {
   Assert.state(this.beanFactory != null, "BeanFactory must be set to resolve RabbitAdmin by bean name");
   try {
    endpoint.setAdmin(this.beanFactory.getBean(rabbitAdmin, RabbitAdmin.class));
   }
   catch (NoSuchBeanDefinitionException ex) {
    throw new BeanInitializationException("Could not register rabbit listener endpoint on [" +
      adminTarget + "], no " + RabbitAdmin.class.getSimpleName() + " with id '" +
      rabbitAdmin + "' was found in the application context", ex);
   }
  }


  RabbitListenerContainerFactory<?> factory = null;
  String containerFactoryBeanName = resolve(rabbitListener.containerFactory());
  if (StringUtils.hasText(containerFactoryBeanName)) {
   Assert.state(this.beanFactory != null, "BeanFactory must be set to obtain container factory by bean name");
   try {
    factory = this.beanFactory.getBean(containerFactoryBeanName, RabbitListenerContainerFactory.class);
   }
   catch (NoSuchBeanDefinitionException ex) {
    throw new BeanInitializationException("Could not register rabbit listener endpoint on [" +
      adminTarget + "] for bean " + beanName + ", no " + RabbitListenerContainerFactory.class.getSimpleName() + " with id '" +
      containerFactoryBeanName + "' was found in the application context", ex);
   }
  }

  this.registrar.registerEndpoint(endpoint, factory);
 }

 private String getEndpointId(RabbitListener rabbitListener) {
  if (StringUtils.hasText(rabbitListener.id())) {
   return resolve(rabbitListener.id());
  }
  else {
   return "org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#" + this.counter.getAndIncrement();
  }
 }

 private String[] resolveQueues(RabbitListener rabbitListener) {
  String[] queues = rabbitListener.queues();
  QueueBinding[] bindings = rabbitListener.bindings();
  org.springframework.amqp.rabbit.annotation.Queue[] queuesToDeclare = rabbitListener.queuesToDeclare();
  List<String> result = new ArrayList<String>();
  if (queues.length > 0) {
   for (int i = 0; i < queues.length; i++) {
    resolveAsString(resolveExpression(queues[i]), result);
   }
  }
  if (queuesToDeclare.length > 0) {
   if (queues.length > 0) {
    throw new BeanInitializationException(
      "@RabbitListener can have only one of 'queues', 'queuesToDeclare', or 'bindings'");
   }
   for (int i = 0; i < queuesToDeclare.length; i++) {
    result.add(declareQueue(queuesToDeclare[i]));
   }
  }
  if (bindings.length > 0) {
   if (queues.length > 0 || queuesToDeclare.length > 0) {
    throw new BeanInitializationException(
      "@RabbitListener can have only one of 'queues', 'queuesToDeclare', or 'bindings'");
   }
   return registerBeansForDeclaration(rabbitListener);
  }
  return result.toArray(new String[result.size()]);
 }

 @SuppressWarnings("unchecked")
 private void resolveAsString(Object resolvedValue, List<String> result) {
  Object resolvedValueToUse = resolvedValue;
  if (resolvedValue instanceof String[]) {
   resolvedValueToUse = Arrays.asList((String[]) resolvedValue);
  }
  if (resolvedValueToUse instanceof Queue) {
   result.add(((Queue) resolvedValueToUse).getName());
  }
  else if (resolvedValueToUse instanceof String) {
   result.add((String) resolvedValueToUse);
  }
  else if (resolvedValueToUse instanceof Iterable) {
   for (Object object : (Iterable<Object>) resolvedValueToUse) {
    resolveAsString(object, result);
   }
  }
  else {
   throw new IllegalArgumentException(String.format(
     "@RabbitListener can't resolve '%s' as either a String or a Queue",
     resolvedValue));
  }
 }

 private String[] registerBeansForDeclaration(RabbitListener rabbitListener) {
  List<String> queues = new ArrayList<String>();
  if (this.beanFactory instanceof ConfigurableBeanFactory) {
   for (QueueBinding binding : rabbitListener.bindings()) {
    String queueName = declareQueue(binding.value());
    queues.add(queueName);
    declareExchangeAndBinding(binding, queueName);
   }
  }
  return queues.toArray(new String[queues.size()]);
 }

 private String declareQueue(org.springframework.amqp.rabbit.annotation.Queue bindingQueue) {
  String queueName = (String) resolveExpression(bindingQueue.value());
  boolean exclusive = false;
  boolean autoDelete = false;
  if (!StringUtils.hasText(queueName)) {
   queueName = AnonymousQueue.Base64UrlNamingStrategy.DEFAULT.generateName();
   // default exclusive/autodelete to true when anonymous
   if (!StringUtils.hasText(bindingQueue.exclusive())
     || resolveExpressionAsBoolean(bindingQueue.exclusive())) {
    exclusive = true;
   }
   if (!StringUtils.hasText(bindingQueue.autoDelete())
     || resolveExpressionAsBoolean(bindingQueue.autoDelete())) {
    autoDelete = true;
   }
  }
  else {
   exclusive = resolveExpressionAsBoolean(bindingQueue.exclusive());
   autoDelete = resolveExpressionAsBoolean(bindingQueue.autoDelete());
  }
  Queue queue = new Queue(queueName,
    resolveExpressionAsBoolean(bindingQueue.durable()),
    exclusive,
    autoDelete,
    resolveArguments(bindingQueue.arguments()));
  queue.setIgnoreDeclarationExceptions(resolveExpressionAsBoolean(bindingQueue.ignoreDeclarationExceptions()));
  ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(queueName + ++this.increment, queue);
  return queueName;
 }

 private void declareExchangeAndBinding(QueueBinding binding, String queueName) {
  org.springframework.amqp.rabbit.annotation.Exchange bindingExchange = binding.exchange();
  String exchangeName = resolveExpressionAsString(bindingExchange.value(), "@Exchange.exchange");
  Assert.isTrue(StringUtils.hasText(exchangeName), () -> "Exchange name required; binding queue " + queueName);
  String exchangeType = resolveExpressionAsString(bindingExchange.type(), "@Exchange.type");
  Exchange exchange;
  if (exchangeType.equals(ExchangeTypes.DIRECT)) {
   exchange = directExchange(bindingExchange, exchangeName);
  }
  else if (exchangeType.equals(ExchangeTypes.FANOUT)) {
   exchange = fanoutExchange(bindingExchange, exchangeName);
  }
  else if (exchangeType.equals(ExchangeTypes.TOPIC)) {
   exchange = topicExchange(bindingExchange, exchangeName);
  }
  else if (exchangeType.equals(ExchangeTypes.HEADERS)) {
   exchange = headersExchange(bindingExchange, exchangeName);
  }
  else {
   throw new BeanInitializationException("Unexpected exchange type: " + exchangeType);
  }
  AbstractExchange abstractExchange = (AbstractExchange) exchange;
  abstractExchange.setInternal(resolveExpressionAsBoolean(bindingExchange.internal()));
  abstractExchange.setDelayed(resolveExpressionAsBoolean(bindingExchange.delayed()));
  abstractExchange.setIgnoreDeclarationExceptions(resolveExpressionAsBoolean(bindingExchange.ignoreDeclarationExceptions()));
  ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(exchangeName + ++this.increment,
    exchange);
  final String[] routingKeys;
  if (exchangeType.equals(ExchangeTypes.FANOUT) || binding.key().length == 0) {
   routingKeys = new String[] { "" };
  }
  else {
   final int length = binding.key().length;
   routingKeys = new String[length];
   for (int i = 0; i < length; ++i) {
    routingKeys[i] = resolveExpressionAsString(binding.key()[i], "@QueueBinding.key");
   }
  }
  final Map<String, Object> bindingArguments = resolveArguments(binding.arguments());
  final boolean bindingIgnoreExceptions = resolveExpressionAsBoolean(binding.ignoreDeclarationExceptions());
  for (String routingKey : routingKeys) {
   final Binding actualBinding = new Binding(queueName, DestinationType.QUEUE, exchangeName, routingKey,
     bindingArguments);
   actualBinding.setIgnoreDeclarationExceptions(bindingIgnoreExceptions);
   ((ConfigurableBeanFactory) this.beanFactory)
     .registerSingleton(exchangeName + "." + queueName + ++this.increment, actualBinding);
  }
 }

 private Exchange directExchange(org.springframework.amqp.rabbit.annotation.Exchange bindingExchange,
   String exchangeName) {
  return new DirectExchange(exchangeName,
    resolveExpressionAsBoolean(bindingExchange.durable()),
    resolveExpressionAsBoolean(bindingExchange.autoDelete()),
    resolveArguments(bindingExchange.arguments()));
 }

 private Exchange fanoutExchange(org.springframework.amqp.rabbit.annotation.Exchange bindingExchange,
   String exchangeName) {
  return new FanoutExchange(exchangeName,
    resolveExpressionAsBoolean(bindingExchange.durable()),
    resolveExpressionAsBoolean(bindingExchange.autoDelete()),
    resolveArguments(bindingExchange.arguments()));
 }

 private Exchange topicExchange(org.springframework.amqp.rabbit.annotation.Exchange bindingExchange,
   String exchangeName) {
  return new TopicExchange(exchangeName,
    resolveExpressionAsBoolean(bindingExchange.durable()),
    resolveExpressionAsBoolean(bindingExchange.autoDelete()),
    resolveArguments(bindingExchange.arguments()));
 }

 private Exchange headersExchange(org.springframework.amqp.rabbit.annotation.Exchange bindingExchange,
   String exchangeName) {
  return new HeadersExchange(exchangeName,
    resolveExpressionAsBoolean(bindingExchange.durable()),
    resolveExpressionAsBoolean(bindingExchange.autoDelete()),
    resolveArguments(bindingExchange.arguments()));
 }

 private Map<String, Object> resolveArguments(Argument[] arguments) {
  Map<String, Object> map = new HashMap<String, Object>();
  for (Argument arg : arguments) {
   String key = resolveExpressionAsString(arg.name(), "@Argument.name");
   if (StringUtils.hasText(key)) {
    Object value = resolveExpression(arg.value());
    Object type = resolveExpression(arg.type());
    Class<?> typeClass;
    String typeName;
    if (type instanceof Class) {
     typeClass = (Class<?>) type;
     typeName = typeClass.getName();
    }
    else {
     Assert.isTrue(type instanceof String, () -> "Type must resolve to a Class or String, but resolved to ["
       + type.getClass().getName() + "]");
     typeName = (String) type;
     try {
      typeClass = ClassUtils.forName(typeName, this.beanClassLoader);
     }
     catch (Exception e) {
      throw new IllegalStateException("Could not load class", e);
     }
    }
    addToMap(map, key, value, typeClass, typeName);
   }
   else {
    if (this.logger.isDebugEnabled()) {
     this.logger.debug("@Argument ignored because the name resolved to an empty String");
    }
   }
  }
  return map.size() < 1 ? null : map;
 }

 private void addToMap(Map<String, Object> map, String key, Object value, Class<?> typeClass, String typeName) {
  if (value.getClass().getName().equals(typeName)) {
   if (typeClass.equals(String.class) && !StringUtils.hasText((String) value)) {
    putEmpty(map, key);
   }
   else {
    map.put(key, value);
   }
  }
  else {
   if (value instanceof String && !StringUtils.hasText((String) value)) {
    putEmpty(map, key);
   }
   else {
    if (CONVERSION_SERVICE.canConvert(value.getClass(), typeClass)) {
     map.put(key, CONVERSION_SERVICE.convert(value, typeClass));
    }
    else {
     throw new IllegalStateException("Cannot convert from " + value.getClass().getName()
       + " to " + typeName);
    }
   }
  }
 }

 private void putEmpty(Map<String, Object> map, String key) {
  if (this.emptyStringArguments.contains(key)) {
   map.put(key, "");
  }
  else {
   map.put(key, null);
  }
 }

 private boolean resolveExpressionAsBoolean(String value) {
  Object resolved = resolveExpression(value);
  if (resolved instanceof Boolean) {
   return (Boolean) resolved;
  }
  else if (resolved instanceof String) {
   return Boolean.valueOf((String) resolved);
  }
  else {
   return false;
  }
 }

 private String resolveExpressionAsString(String value, String attribute) {
  Object resolved = resolveExpression(value);
  if (resolved instanceof String) {
   return (String) resolved;
  }
  else {
   throw new IllegalStateException("The [" + attribute + "] must resolve to a String. "
     + "Resolved to [" + resolved.getClass() + "] for [" + value + "]");
  }
 }

 private Object resolveExpression(String value) {
  String resolvedValue = resolve(value);

  if (!(resolvedValue.startsWith("#{") && value.endsWith("}"))) {
   return resolvedValue;
  }

  return this.resolver.evaluate(resolvedValue, this.expressionContext);
 }

 /**
  * Resolve the specified value if possible.
  * @param value the value to resolve.
  * @return the resolved value.
  * @see ConfigurableBeanFactory#resolveEmbeddedValue
  */
 private String resolve(String value) {
  if (this.beanFactory != null && this.beanFactory instanceof ConfigurableBeanFactory) {
   return ((ConfigurableBeanFactory) this.beanFactory).resolveEmbeddedValue(value);
  }
  return value;
 }

 /**
  * An {@link MessageHandlerMethodFactory} adapter that offers a configurable underlying
  * instance to use. Useful if the factory to use is determined once the endpoints
  * have been registered but not created yet.
  * @see RabbitListenerEndpointRegistrar#setMessageHandlerMethodFactory
  */
 private class RabbitHandlerMethodFactoryAdapter implements MessageHandlerMethodFactory {

  private MessageHandlerMethodFactory messageHandlerMethodFactory;

  RabbitHandlerMethodFactoryAdapter() {
   super();
  }

  public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory rabbitHandlerMethodFactory1) {
   this.messageHandlerMethodFactory = rabbitHandlerMethodFactory1;
  }

  @Override
  public InvocableHandlerMethod createInvocableHandlerMethod(Object bean, Method method) {
   return getMessageHandlerMethodFactory().createInvocableHandlerMethod(bean, method);
  }

  private MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
   if (this.messageHandlerMethodFactory == null) {
    this.messageHandlerMethodFactory = createDefaultMessageHandlerMethodFactory();
   }
   return this.messageHandlerMethodFactory;
  }

  private MessageHandlerMethodFactory createDefaultMessageHandlerMethodFactory() {
   DefaultMessageHandlerMethodFactory defaultFactory = new DefaultMessageHandlerMethodFactory();
   defaultFactory.setBeanFactory(RabbitListenerAnnotationBeanPostProcessor.this.beanFactory);
   defaultFactory.afterPropertiesSet();
   return defaultFactory;
  }

 }

}