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

import org.sonar.jpa.session.DefaultDatabaseConnector;
import org.sonar.jpa.session.ThreadLocalDatabaseSessionFactory;
import org.sonar.server.platform.ws.MigrateDbSystemWsAction;
import org.sonar.server.platform.ws.SystemStatusWsAction;
import org.sonar.server.platform.ws.SystemWs;
import org.sonar.server.ws.ListingWs;
import org.sonar.server.ws.WebServiceEngine;

public class ComponentLevelSafeMode extends ComponentLevel {
  public ComponentLevelSafeMode(ComponentLevel parent) {
    super("Safemode", parent);
  }

  @Override
  public ComponentLevel configure() {
    add(
      // DB access required by DatabaseSessionFilter wired into ROR
      DefaultDatabaseConnector.class,
      ThreadLocalDatabaseSessionFactory.class,

      // Server WS
      SystemStatusWsAction.class,
      MigrateDbSystemWsAction.class,
      SystemWs.class,

      // Listing WS
      ListingWs.class,

      // WS engine
      WebServiceEngine.class);

    return this;
  }
}
