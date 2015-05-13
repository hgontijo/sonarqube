/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform.components;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonar.core.platform.ComponentContainer;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class ComponentLevel {
  private final String name;
  @Nullable
  private final ComponentLevel parent;
  private final ComponentContainer container;

  public ComponentLevel(String name) {
    this.name = name;
    this.parent = null;
    this.container = createContainer(null);
  }

  public ComponentLevel(String name, @Nonnull ComponentLevel parent) {
    this.name = checkNotNull(name);
    this.parent = checkNotNull(parent);
    this.container = createContainer(parent.container);
  }

  public ComponentContainer getContainer() {
    return container;
  }

  public String getName() {
    return name;
  }

  /**
   * Intended to be override by subclasses if needed
   */
  protected ComponentContainer createContainer(@Nullable ComponentContainer parent) {
    if (parent == null) {
      return new ComponentContainer();
    }
    return parent.createChild();
  }

  public abstract ComponentLevel configure();

  /**
   * Intended to be override by subclasses if needed
   */
  public ComponentLevel start() {
    container.startComponents();

    return this;
  }

  /**
   * Intended to be override by subclasses if needed
   */
  public ComponentLevel stop() {
    container.stopComponents();

    return this;
  }

  /**
   * Intended to be override by subclasses if needed
   */
  public ComponentLevel destroy() {
    if (parent != null) {
      parent.container.removeChild();
    }
    return this;
  }

  protected void add(@Nullable Object object, boolean singleton) {
    if (object != null) {
      container.addComponent(object, singleton);
    }
  }

  protected <T> T getComponentByType(Class<T> tClass) {
    return container.getComponentByType(tClass);
  }

  protected void add(Object... objects) {
    for (Object object : objects) {
      if (object != null) {
        container.addComponent(object, true);
      }
    }
  }

  protected void addAll(Collection<?> objects) {
    add(objects.toArray(new Object[objects.size()]));
  }

}
