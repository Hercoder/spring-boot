/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	/**
	 * The class name of application context that will be used by default for non-web
	 * environments.
	 */
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
	 * The class name of application context that will be used by default for web
	 * environments.
	 */
	public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot."
			+ "web.servlet.context.AnnotationConfigServletWebServerApplicationContext";

	/**
	 * The class name of application context that will be used by default for reactive web
	 * environments.
	 */
	public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext";

	/**
	 * Default banner location.
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
	 * Banner location property key.
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

	// 主要bean来源集合
	private Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();

	// 主应用程序类
	private Class<?> mainApplicationClass;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	private boolean addConversionService = true;

	private Banner banner;

	private ResourceLoader resourceLoader;

	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private Class<? extends ConfigurableApplicationContext> applicationContextClass;

	// 应用程序类型
	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;

	// 应用程序初始化器集合
	private List<ApplicationContextInitializer<?>> initializers;

	// 应用程序监听器集合
	private List<ApplicationListener<?>> listeners;

	private Map<String, Object> defaultProperties;

	private Set<String> additionalProfiles = new HashSet<>();

	private boolean allowBeanDefinitionOverriding;

	private boolean isCustomEnvironment = false;

	private boolean lazyInitialization = false;

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		//空的资源加载器
		this(null, primarySources);
	}

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 *
	 * 创建一个新的SpringApplication实例，并从指定的来源加载bean的信息
	 *
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		//将主要来源设置到一个集合里面，默认来源就只有一个启动类的class
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		//设置Web应用程序类型，可能是SERVLET程序，也可能是REACTIVE响应式程序，还有可能是NONE，即非web应用程序
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		//设置ApplicationContextInitializer，应用程序初始化器。从META-INF/spring.factories文件中获取
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		//设置ApplicationListener，应用程序监听器。从META-INF/spring.factories文件中获取
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		//设置主应用程序类，一般就是Spring boot项目的启动类
		this.mainApplicationClass = deduceMainApplicationClass();
	}

	/**
	 * 推断应用程序主类
	 * 该方法推断应用程序主类，一般就是Spring boot项目的启动类，也就是调用main方法的类。
	 * @return
	 */
	private Class<?> deduceMainApplicationClass() {
		try {
			//获取堆栈跟踪记录
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
			for (StackTraceElement stackTraceElement : stackTrace) {
				if ("main".equals(stackTraceElement.getMethodName())) {
					//找到调用main方法的类，返回该类的class
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue
		}
		return null;
	}

	/**
	 * Run the Spring application, creating and refreshing a new
	 *
	 * 该方法将会运行SpringApplication实例，并且创建并刷新一个新的ApplicationContext。该方法就是Spring boot项目启动的关键方法。
	 *
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		//创建并启动一个计时器，用于统计run方法执行时常，即应用启动时常
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		// spring的应用程序上下文，代表着spring容器
		ConfigurableApplicationContext context = null;
		// 配置headless系统属性，Headless模式是系统的一种配置模式。在该模式下，系统缺少了显示设备、键盘或鼠标。
		// 这里就是 System.getProperty 属性设置
		configureHeadlessProperty();
		/**
		 * 1、获取全部SpringApplicationRunListener运行监听器并封装到SpringApplicationRunListeners中
		 * SpringApplicationRunListener的实现也是从spring.factories文件中加载的，其中一个实现就是EventPublishingRunListener
		 */
		SpringApplicationRunListeners listeners = getRunListeners(args);
		/**
		 * 2、执行所有SpringApplicationRunListener监听器的starting的方法，可以用于非常早期的初始化操作
		 * EventPublishingRunListener的starting方法会向之前初始化的所有ApplicationListener发送一个ApplicationStartingEvent事件
		 * ApplicationStartingEvent事件标志着SpringApplication的启动，并且此时ApplicationContext还没有初始化，这是一个早期事件
		 */
		listeners.starting();
		try {
			// 参数对象，封装了传递进来的启动参数
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			/**
			 * 3、根据SpringApplicationRunListeners和启动参数准备环境
			 */
			ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
			/**
			 * 配置spring.beaninfo.ignore属性
			 * 这个配置用来忽略所有自定义的BeanInfo类的搜索，优化启动速度
			 */
			configureIgnoreBeanInfo(environment);
			/**
			 * 4、打印启动banner图
			 */
			Banner printedBanner = printBanner(environment);
			/**
			 * 创建spring上下文容器实例，核心方法
			 * org.springframework.context.annotation.AnnotationConfigApplicationContext
			 */
			context = createApplicationContext();
			/**
			 * 6、准备上下文容器，核心方法
			 * 该方法会将我们的启动类注入到容器中，后续通过该类启动自动配置
			 */
			prepareContext(context, environment, listeners, applicationArguments, printedBanner);
			/**
			 * 7、刷新上下文容器，核心方法
			 * 该方法就是spring容器的启动方法，将会加载bean以及各种配置
			 * 这个方法的源码非常多，我们在之前的spring启动源码部分花了大量时间已经讲过了，在此不再赘述
			 */
			refreshContext(context);
			/**
			 * 8、刷新后处理
			 * 该方法是一个扩展方法，没有提供任何默认的实现，我们自定义的子类可以进行扩展。
			 */
			afterRefresh(context, applicationArguments);
			// springboot项目启动完毕，停止stopWatch计时
			stopWatch.stop();
			/*
			 * 打印容器启动耗时日志
			 * 例如：Started SpringBootLearnApplication in 13.611 seconds (JVM running for 20.626)
			 */
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
			}
			/**
			 * 9、应用SpringApplicationRunListener监听器的started方法
			 * EventPublishingRunListener将会发出ApplicationStartedEvent事件，表明容器已启动
			 */
			listeners.started(context);
			/**
			 * 10、执行容器中的ApplicationRunner和CommandLineRunner类型的bean的run方法
			 * 这也是一个扩展点，用于实现spring容器启动后需要做的事
			 *
			 * runner是一类特殊的bean，如果该bean属于ApplicationRunner或者CommandLineRunner类型，那么该bean就被称为runner，
			 * 那么在springboot启动之后会自动调用该bean的run方法，这里就是调用的源码！
			 */
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			// 如果启动过程中出现了异常，那么会将异常信息加入到exceptionReporters中并抛出IllegalStateException
			handleRunFailure(context, ex, listeners);
			throw new IllegalStateException(ex);
		}

		try {
			/**
			 * 11、应用SpringApplicationRunListener监听器的running方法
			 * EventPublishingRunListener将会发出ApplicationReadyEvent事件，表明容器已就绪，可以被使用了
			 */
			listeners.running(context);
		}
		catch (Throwable ex) {
			// 如果发布事件的过程中出现了异常，那么会将异常信息加入到exceptionReporters中并抛出IllegalStateException
			handleRunFailure(context, ex, null);
			throw new IllegalStateException(ex);
		}
		//返回容器
		return context;
	}

	/**
	 * 启动监听器之后，首先需要准备容器环境，根据SpringApplicationRunListeners和启动参数准备环境。这里会对外部配置的参数进行设置，
	 * 比如端口号之类的。
	 * @param listeners
	 * @param applicationArguments
	 * @return
	 */
	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		// Create and configure the environment
		// 获取或者创建环境对象，一般都是StandardServletEnvironment类型
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		// 配置环境
		// 主要配置属性源和激活的环境，将来自外部的配置源放在属性源集合的头部
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		//继续封装属性源
		ConfigurationPropertySources.attach(environment);
		// 环境准备完毕之后，向所有的ApplicationListener发出ApplicationEnvironmentPreparedEvent事件
		listeners.environmentPrepared(environment);
		// 将环境对象绑定到SpringApplication
		bindToSpringApplication(environment);
		// 如果不是自定义的环境变量，若有必要则进行转换，一般都需要转换
		if (!this.isCustomEnvironment) {
			environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
					deduceEnvironmentClass());
		}
		//继续封装属性源
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
		switch (this.webApplicationType) {
		case SERVLET:
			return StandardServletEnvironment.class;
		case REACTIVE:
			return StandardReactiveWebEnvironment.class;
		default:
			return StandardEnvironment.class;
		}
	}

	/**
	 * 该方法用于准备容器上下文，主要是进行ApplicationContextInitializer扩展点的应用，以及打印启动日志，比如激活的profiles图，
	 * 设置是否允许同名bean覆盖（默认不允许），是否懒加载（默认不允许）等操作。
	 *
	 * 最重要的是该方法会将我们的启动类注入到springboot的容器上下文内部的beanfactory中，有了这一步，后面就可以解析启动类的注解和各种配置，
	 * 进而执行springboot的自动配置（启动类上有@SpringBootApplication注解，还有其他各种注解）。
	 *
	 * @param context
	 * @param environment
	 * @param listeners
	 * @param applicationArguments
	 * @param printedBanner
	 */
	private void prepareContext(ConfigurableApplicationContext context, ConfigurableEnvironment environment,
			SpringApplicationRunListeners listeners, ApplicationArguments applicationArguments, Banner printedBanner) {
		//设置环境变量
		context.setEnvironment(environment);
		/*
		 * 对于容器的后处理，会尝试注入BeanNameGenerator，设置resourceLoader，设置ConversionService等操作
		 * 子类可以根据需要应用额外的处理。
		 */
		postProcessApplicationContext(context);
		/*
		 * 应用ApplicationContextInitializer扩展点的initialize方法
		 * 从而实现自定义容器的逻辑，这是一个扩展点
		 */
		applyInitializers(context);
		/*
		 * 应用SpringApplicationRunListener监听器的contextPrepared方法
		 * EventPublishingRunListener将会发出ApplicationContextInitializedEvent事件
		 */
		listeners.contextPrepared(context);
		//是否打印启动相关日志，默认true
		if (this.logStartupInfo) {
			//记录启动日志信息，即banner图日志下面的第一行日志信息
			logStartupInfo(context.getParent() == null);
			//记录激活的配置环境profiles日志信息
			//即The following profiles are active: dev，或者No active profile set, falling back to default profiles:
			logStartupProfileInfo(context);
		}
		// Add boot specific singleton beans
		// 获取此上下文内部的beanFactory，即bean工厂
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		//将applicationArguments作为一个单例对象注册到bean工厂中
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			//将banner作为一个单例对象注册到bean工厂中
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		//如果是该类型，servlet项目默认就是该类型
		if (beanFactory instanceof DefaultListableBeanFactory) {
			//设置是否允许同名bean的覆盖，这里默认false，如果有同名bean就会抛出异常
			((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		//设置是否允许懒加载，即延迟初始化bean，即仅在需要bean时才创建该bean，并注入其依赖项。
		//默认false，即所有定义的bean及其依赖项都是在创建应用程序上下文时创建的。
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// Load the sources
		// 加载启动源，默认就是我们的启动类
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		/*
		 * 加载启动类，并将启动类注入到容器，关键方法
		 * 有了这一步，后面就可以解析启动类上的注解和各种配置，进行执行springboot的自动配置
		 */
		load(context, sources.toArray(new Object[0]));
		/*
		 * 应用SpringApplicationRunListener监听器的contextLoaded方法
		 * EventPublishingRunListener将会发出ApplicationPreparedEvent事件
		 */
		listeners.contextLoaded(context);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		//判断是否注册销毁容器的钩子方法，默认true
		if (this.registerShutdownHook) {
			try {
				//注册销毁容器时的钩子
				//该方法向 JVM 运行时环境注册一个关闭钩子函数，在 JVM 关闭时会先关闭此上下文，即执行此上线文的close方法
				context.registerShutdownHook();
			}
			catch (AccessControlException ex) {
				// Not allowed in some environments.
			}
		}
		/*
		 * 刷新容器
		 */
		refresh((ApplicationContext) context);
	}

	/**
	 * 配置headless系统属性，Headless模式是系统的一种配置模式。在该模式下，系统缺少了显示设备、键盘或鼠标。默认为true。
	 */
	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

	/**
	 * 获取运行监听器SpringApplicationRunListener并封装到SpringApplicationRunListeners中
	 * @param args
	 * @return
	 */
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		/**
		 * 借助SpringFactoriesLoader获取所有引入的jar包中和当前类路径下的META-INF/spring.factories文件中指定类型的实例
		 * 这里指定类型为SpringApplicationRunListener，默认情况下spring.factories文件中有一个实现，即EventPublishingRunListener
		 * 最终所有的SpringApplicationRunListener集合会被封装到一个SpringApplicationRunListeners对象中。
		 */
		return new SpringApplicationRunListeners(logger,
				getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args));
	}

	/**
	 * 借助SpringFactoriesLoader获取所有引入的jar包中和当前类路径下的META-INF/spring.factories文件中指定类型的实例
	 * @param type
	 * @param <T>
	 * @return
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	/**
	 * 借助SpringFactoriesLoader获取所有引入的jar包中和当前类路径下的META-INF/spring.factories文件中指定类型的实例
	 * spring.factories 文件必须是 Properties 格式，其中 key 是接口或抽象类的完全限定名称，value 是逗号分隔的实现类名称列表。
	 *
	 * @param type
	 * @param parameterTypes
	 * @param args
	 * @param <T>
	 * @return
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		ClassLoader classLoader = getClassLoader();

		//借助SpringFactoriesLoader获取所有引入的jar包中和当前类路径下的META-INF/spring.factories文件中指定类型的实例名称
		//spring.factories 文件必须是 Properties 格式，其中 key 是接口或抽象类的完全限定名称，value 是逗号分隔的实现类名称列表。
		//使用名称Set集合保存数据，确保唯一以防止重复
		// Use names and ensure unique to protect against duplicates
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		//根据获取的类型全路径名反射创建实例
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
		//对实例进行排序
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}

	/**
	 * 该方法将会对上面方法加载的全部ApplicationContextInitializer的实现进行实例化，实际上就是一系列反射创建对象的过程。
	 * @param type
	 * @param parameterTypes
	 * @param classLoader
	 * @param args
	 * @param names
	 * @param <T>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
			ClassLoader classLoader, Object[] args, Set<String> names) {
		//最终返回的对象集合
		List<T> instances = new ArrayList<>(names.size());
		for (String name : names) {
			try {
				//根据全路径名获取对应的class对象
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				//根据参数获取指定的构造器
				Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
				//通过构造器反射创建对象
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}

	/**
	 * SpringApplication的方法
	 * 创建和配置环境
	 * @return
	 */
	private ConfigurableEnvironment getOrCreateEnvironment() {
		//如果已存在就直接返回
		if (this.environment != null) {
			return this.environment;
		}
		//根据应用程序类型创建不同的环境对象
		switch (this.webApplicationType) {
			//一般都是SERVLET项目，因此一般都是StandardServletEnvironment
			case SERVLET:
			return new StandardServletEnvironment();
		case REACTIVE:
			return new StandardReactiveWebEnvironment();
		default:
			return new StandardEnvironment();
		}
	}

	/**
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * 配置环境
	 *
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		//是否需要添加转换服务，默认需要
		if (this.addConversionService) {
			ConversionService conversionService = ApplicationConversionService.getSharedInstance();
			environment.setConversionService((ConfigurableConversionService) conversionService);
		}
		//配置属性源，所谓属性源实际上可以看做是多个配置集的集合，来自外部的配置集将会被放在配置集合的首位
		configurePropertySources(environment, args);
		//配置激活的环境
		configureProfiles(environment, args);
	}

	/**
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));
		}
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(
						new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(StringUtils.toStringArray(profiles));
	}

	private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
		if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
			Boolean ignore = environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
			System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
		}
	}

	/**
	 * Bind the environment to the {@link SpringApplication}.
	 * @param environment the environment to bind
	 */
	protected void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	/**
	 * 这个方法用于打印banner图，首先会查找自定义的banner资源，banner可以是图片或者txt文本格式，默认自定义资源是txt格式，名字是banner.txt，位于resources目录下。
	 * @param environment
	 * @return
	 */
	private Banner printBanner(ConfigurableEnvironment environment) {
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(null);
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		//banner输出到日志文件中还是控制台，默认是CONSOLE，即控制台
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	/**
	 * Strategy method used to create the {@link ApplicationContext}. By default this
	 * method will respect any explicitly set application context or application context
	 * class before falling back to a suitable default.
	 *
	 * 在准备好环境并且加载了配置文件之后，通过createApplicationContext方法创建spring容器对象，
	 * 基于servlet的web项目容器是AnnotationConfigServletWebServerApplicationContext类型。
	 *
	 *
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		Class<?> contextClass = this.applicationContextClass;
		//如果没有设置容器类型
		if (contextClass == null) {
			try {
				//根据web应用程序类型选择spring容器的类型
				switch (this.webApplicationType) {
					//一般都是servlet应用，因此spring容器就是AnnotationConfigServletWebServerApplicationContext类型
					case SERVLET:
					contextClass = Class.forName(DEFAULT_SERVLET_WEB_CONTEXT_CLASS);
					break;
				case REACTIVE:
					contextClass = Class.forName(DEFAULT_REACTIVE_WEB_CONTEXT_CLASS);
					break;
				default:
					contextClass = Class.forName(DEFAULT_CONTEXT_CLASS);
				}
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Unable create a default ApplicationContext, please specify an ApplicationContextClass", ex);
			}
		}
		//调用无参构造器反射创建实例
		return (ConfigurableApplicationContext) BeanUtils.instantiateClass(contextClass);
	}

	/**
	 * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
	 * apply additional processing as required.
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		if (this.addConversionService) {
			context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
		}
	}

	/**
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass).logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information.
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: "
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: "
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * Load beans into the application context.
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of
	 * {@link #refresh(ConfigurableApplicationContext)}
	 */
	@Deprecated
	protected void refresh(ApplicationContext applicationContext) {
		Assert.isInstanceOf(ConfigurableApplicationContext.class, applicationContext);
		refresh((ConfigurableApplicationContext) applicationContext);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ConfigurableApplicationContext applicationContext) {
		applicationContext.refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
	}

	/**
	 *
	 * @param context
	 * @param args
	 */
	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		//从容器中查找全部ApplicationRunner和CommandLineRunner类型的bean实例，并且加入到集合中
		List<Object> runners = new ArrayList<>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		//排序
		AnnotationAwareOrderComparator.sort(runners);
		//依次运行这些runner
		for (Object runner : new LinkedHashSet<>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			//运行ApplicationRunner，参数就是当前的配置参数
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			//运行CommandLineRunner，参数就是当前返回传递给应用程序的原始未处理参数
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

	private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
			SpringApplicationRunListeners listeners) {
		try {
			try {
				handleExitCode(context, exception);
				if (listeners != null) {
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(context, exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private void reportFailure(ApplicationContext context, Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : getExceptionReporters(context)) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application run failed", failure);
			registerLoggedException(failure);
		}
	}

	private Collection<SpringBootExceptionReporter> getExceptionReporters(ApplicationContext context) {
		try {
			if (context != null) {
				return getSpringFactoriesInstances(SpringBootExceptionReporter.class,
						new Class[] { ConfigurableApplicationContext.class }, context);
			}
		}
		catch (Throwable ex) {
			// Continue
		}
		return Collections.emptyList();
	}

	/**
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

	private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of web
	 * application will be deduced based on the classpath.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1.0
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Sets if beans should be initialized lazily. Defaults to {@code false}.
	 * @param lazyInitialization if initialization should be lazy
	 * @since 2.2
	 * @see BeanDefinition#setLazyInit(boolean)
	 */
	public void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the application
	 * context's {@link Environment}.
	 * @param addConversionService if the application conversion service should be added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = new LinkedHashSet<>(Arrays.asList(profiles));
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
	 * calling this method.
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A source
	 * can be: a class name, package name, or an XML resource location.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method combines any
	 * primary sources specified in the constructor with any additional ones that have
	 * been {@link #setSources(Set) explicitly set}.
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}
		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_SERVLET_WEB_CONTEXT_CLASS} for web based
	 * applications or {@link AnnotationConfigApplicationContext} for non web based
	 * applications.
	 * @param applicationContextClass the context class to set
	 */
	public void setApplicationContextClass(Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.applicationContextClass = applicationContextClass;
		this.webApplicationType = WebApplicationType.deduceFromApplicationContext(applicationContextClass);
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 *
	 * 设置ApplicationContextInitializer初始化器，后面初始化的时候会使用到，在Spring上下文被刷新之前进行初始化的操作，
	 * 例如注入Property Sources属性源或者是激活Profiles环境。
	 *
	 * 这里会借助SpringFactoriesLoader工具类获取所有引入的jar包中和当前类路径下的META-INF/spring.factories文件中指定类型的实例，
	 * 也就是ApplicationContextInitializer类型的实例。
	 *
	 * spring.factories 是Spirng boot提供的一种扩展机制，实际上spring.factories就是仿照Java中的SPI扩展机制来实现的Spring Boot自己的SPI机制，
	 * 它是实现 Spring Boot的自动配置的基础。
	 *
	 * spring.factories该文件必须是 Properties 格式，其中 key 是接口或抽象类的完全限定名称，value 是逗号分隔的实现类名称列表。
	 *
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
	 * applied to the SpringApplication and registered with the {@link ApplicationContext}
	 * .
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings.
	 * @param primarySource the primary source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 *
	 * 创建 ApplicationContextInitializer 实例
	 * 创建 ApplicationListener 实例
	 * 确定主启动类
	 *
	 * @param primarySources the primary sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful when
	 * application sources are defined via a {@literal --spring.main.sources} command line
	 * argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

}
