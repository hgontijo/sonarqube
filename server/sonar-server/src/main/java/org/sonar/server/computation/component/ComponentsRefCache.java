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

package org.sonar.server.computation.component;

import javax.annotation.CheckForNull;

import java.util.HashMap;
import java.util.Map;

public class ComponentsRefCache {

  private final Map<Integer, Component> componentsByRef;
  private final Map<String, Component> componentsByKey;

  public ComponentsRefCache() {
    componentsByRef = new HashMap<>();
    componentsByKey = new HashMap<>();
  }

  public void addComponent(Integer ref, Component component){
    componentsByRef.put(ref, component);
    componentsByKey.put(component.key, component);
  }

  @CheckForNull
  public Component getComponent(Integer ref){
    return componentsByRef.get(ref);
  }

  public static class Component {

    private String uuid;
    private String key;

    public Component(String key, String uuid) {
      this.key = key;
      this.uuid = uuid;
    }

    public String getKey() {
      return key;
    }

    public String getUuid() {
      return uuid;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Component component = (Component) o;

      if (!uuid.equals(component.uuid)) {
        return false;
      }
      return key.equals(component.key);

    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + key.hashCode();
      return result;
    }
  }
}
