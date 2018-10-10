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
package ro.fortsoft.pf4j.spring.boot.ext;

import ro.fortsoft.pf4j.DefaultExtensionFactory;
import ro.fortsoft.pf4j.ExtensionFactory;
import ro.fortsoft.pf4j.JarPluginManager;
import ro.fortsoft.pf4j.spring.SingletonSpringExtensionFactory;
import ro.fortsoft.pf4j.spring.SpringExtensionFactory;

public class ExtendedJarPluginManager extends JarPluginManager {

	/** Whether to register the object to the spring context */
	private boolean injectable = true;
	/** Whether always returns a singleton instance. */
	private boolean singleton = true;
	
	public ExtendedJarPluginManager(boolean injectable, boolean singleton ) {
		this.injectable = injectable;
		this.singleton = singleton;
	}
	
	@Override
	protected ExtensionFactory createExtensionFactory() {
		if (this.isInjectable()) {
			if (this.isSingleton()) {
				return new SingletonSpringExtensionFactory(this);
			}
			return new SpringExtensionFactory(this);
		}
		return new DefaultExtensionFactory();
	}

	public boolean isInjectable() {
		return injectable;
	}

	public boolean isSingleton() {
		return singleton;
	}
	
}
