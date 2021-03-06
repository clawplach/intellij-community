/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.util.lang.UrlClassLoader;

import javax.swing.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class Bootstrap {
  private static final String PLUGIN_MANAGER = "com.intellij.ide.plugins.PluginManager";

  private Bootstrap() {}

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList<URL>());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, final List<URL> classpathElements) {
    final UrlClassLoader newClassLoader = ClassloaderUtil.initClassloader(classpathElements);
    try {
      WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, newClassLoader);

      final Class<?> klass = Class.forName(PLUGIN_MANAGER, true, newClassLoader);

      final Method startMethod = klass.getDeclaredMethod("start", String.class, String.class, String[].class);
      startMethod.setAccessible(true);
      startMethod.invoke(null, mainClass, methodName, args);
    }
    catch (Exception e) {
      if ("true".equals(System.getProperty("java.awt.headless"))) {
        //noinspection UseOfSystemOutOrSystemErr
        e.printStackTrace(System.err);
      }
      else {
        JOptionPane.showMessageDialog(null, e.getClass().getName() + ": " + e.getMessage(), "Error starting IntelliJ Platform", JOptionPane.ERROR_MESSAGE);
      }
    }
  }
}