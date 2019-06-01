/*
 * Copyright (c) 2018, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package ro.fortsoft.pf4j.spring.boot;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ro.fortsoft.pf4j.PluginDescriptor;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginStateEvent;
import ro.fortsoft.pf4j.PluginStateListener;
import ro.fortsoft.pf4j.RuntimeMode;
import ro.fortsoft.pf4j.spring.SpringPlugin;
import ro.fortsoft.pf4j.spring.boot.ext.ExtendedExtensionsInjector;
import ro.fortsoft.pf4j.spring.boot.ext.ExtendedJarPluginManager;
import ro.fortsoft.pf4j.spring.boot.ext.ExtendedPluginManager;
import ro.fortsoft.pf4j.spring.boot.ext.registry.Pf4jDynamicControllerRegistry;
import ro.fortsoft.pf4j.spring.boot.ext.task.PluginUpdateTask;
import ro.fortsoft.pf4j.spring.boot.ext.utils.PluginUtils;
import ro.fortsoft.pf4j.update.DefaultUpdateRepository;
import ro.fortsoft.pf4j.update.UpdateManager;
import ro.fortsoft.pf4j.update.UpdateRepository;

/**
 * Pf4j 1.x Configuration
 * @author <a href="https://github.com/vindell">vindell</a>
 */
@Configuration
@ConditionalOnClass({ PluginManager.class, UpdateManager.class, SpringPlugin.class })
@ConditionalOnProperty(prefix = Pf4jProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties({Pf4jProperties.class, Pf4jUpdateMavenProperties.class})
public class Pf4jAutoConfiguration implements ApplicationContextAware {

	private ApplicationContext applicationContext;
	private Logger logger = LoggerFactory.getLogger(Pf4jAutoConfiguration.class);
	// 实例化Timer类
	private Timer timer = new Timer(true);
	
	@Bean
	@ConditionalOnMissingBean(Pf4jDynamicControllerRegistry.class)
	public Pf4jDynamicControllerRegistry pf4jDynamicControllerRegistry() {
		return new Pf4jDynamicControllerRegistry();
	}
	
	@Bean
	@ConditionalOnMissingBean(PluginStateListener.class)
	public PluginStateListener pluginStateListener() {

		return new PluginStateListener() {

			@Override
			public void pluginStateChanged(PluginStateEvent event) {

				PluginDescriptor descriptor = event.getPlugin().getDescriptor();

				if (logger.isDebugEnabled()) {
					logger.debug(String.format("Plugin [%s（%s）](%s) %s", descriptor.getPluginId(),
							descriptor.getVersion().toString(), descriptor.getPluginDescription(),
							event.getPluginState().toString()));
				}

			}

		};
	}
	
	@Bean
	@ConditionalOnBean(RequestMappingHandlerMapping.class)
	public PluginManager pluginManager(Pf4jProperties properties) {

		// 设置运行模式
		RuntimeMode mode = RuntimeMode.byName(properties.getMode());
		System.setProperty("pf4j.mode", mode.toString());

		// 设置插件目录
		String pluginsRoot = StringUtils.hasText(properties.getPluginsRoot()) ? properties.getPluginsRoot() : "plugins";
		System.setProperty("pf4j.pluginsDir", pluginsRoot);
		String apphome = System.getProperty("app.home");
		if (RuntimeMode.DEPLOYMENT.compareTo(RuntimeMode.byName(properties.getMode())) == 0
				&& StringUtils.hasText(apphome)) {
			System.setProperty("pf4j.pluginsDir", apphome + File.separator + pluginsRoot);
		}

		// final PluginManager pluginManager = new DefaultPluginManager();
		// final PluginManager pluginManager = new JarPluginManager();

		PluginManager pluginManager = null;
		if (properties.isJarPackages()) {
			pluginManager = new ExtendedJarPluginManager(properties.isAutowire(), properties.isSingleton());
		} else {
			pluginManager = new ExtendedPluginManager(pluginsRoot, properties.isAutowire(), properties.isSingleton());
		}

		/*
		 * pluginManager.enablePlugin(pluginId) pluginManager.disablePlugin(pluginId)
		 * pluginManager.deletePlugin(pluginId)
		 * pluginManager.loadPlugin(pluginPath) pluginManager.startPlugin(pluginId)
		 * pluginManager.stopPlugin(pluginId) pluginManager.unloadPlugin(pluginId)
		 */
		
		// 加载、启动插件目录中的插件
		pluginManager.loadPlugins();
		/*
		 * 调用Plugin实现类的start()方法:
		 */
		pluginManager.startPlugins();
		
		// 加载、启动绝对路径指定的插件
		PluginUtils.loadAndStartPlugins(pluginManager, properties.getPlugins());

		return pluginManager;
	}
	
	@Bean
	public UpdateManager updateManager(
			PluginManager pluginManager,
			@Autowired(required = false) ObjectProvider<UpdateRepository> repoProvider,
			Pf4jProperties properties) {
		
		UpdateManager updateManager = null;
		if (StringUtils.hasText(properties.getReposJsonPath())) {
			updateManager = new UpdateManager(pluginManager, Paths.get(properties.getReposJsonPath()));
		} else {
			updateManager = new UpdateManager(pluginManager);
		}
		
		if(!CollectionUtils.isEmpty(properties.getRepos())) {
			for (Pf4jUpdateProperties repo : properties.getRepos()) {
				updateManager.addRepository(new DefaultUpdateRepository(repo.getId(), repo.getUrl(), repo.getPluginsJsonFileName()));
			}
		}
		List<UpdateRepository> repos = repoProvider.orderedStream().collect(Collectors.toList());
		if(!CollectionUtils.isEmpty(repos)) {
			for (UpdateRepository newRepo : repos) {
				updateManager.addRepository(newRepo);
			}
		}
		// auto update
		if(properties.isAutoUpdate()) {
			timer.schedule(new PluginUpdateTask(pluginManager, updateManager), properties.getPeriod());
		}
		return updateManager;
	}
	
	@Bean
	@ConditionalOnProperty(prefix = Pf4jProperties.PREFIX, value = "injectable", havingValue = "true", matchIfMissing = true)
	public ExtendedExtensionsInjector extensionsInjector(PluginManager pluginManager, 
			Pf4jDynamicControllerRegistry dynamicControllerRegistry) {
		return new ExtendedExtensionsInjector(pluginManager, dynamicControllerRegistry);
	}

	@Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
	
}