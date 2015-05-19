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

package org.sonar.server.computation.step;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.sonar.batch.protocol.Constants;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.batch.protocol.output.BatchReportReader;
import org.sonar.batch.protocol.output.BatchReportWriter;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.computation.ComputationContext;
import org.sonar.server.computation.component.ComponentsRefCache;
import org.sonar.server.db.DbClient;
import org.sonar.test.DbTests;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@Category(DbTests.class)
public class FeedComponentsCacheStepTest extends BaseStepTest {

  @ClassRule
  public static DbTester dbTester = new DbTester();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  File reportDir;

  DbSession session;

  DbClient dbClient;

  ComponentsRefCache componentsRefCache;

  FeedComponentsCacheStep sut;

  @Before
  public void setup() throws Exception {
    dbTester.truncateTables();
    session = dbTester.myBatis().openSession(false);
    dbClient = new DbClient(dbTester.database(), dbTester.myBatis(), new ComponentDao());

    reportDir = temp.newFolder();

    componentsRefCache = new ComponentsRefCache();
    sut = new FeedComponentsCacheStep(dbClient, componentsRefCache);
  }

  @Override
  protected ComputationStep step() {
    return sut;
  }


  @Test
  public void add_components() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    assertThat(componentsRefCache.getComponent(1).getKey()).isEqualTo("PROJECT_KEY");
    assertThat(componentsRefCache.getComponent(1).getUuid()).isNotNull();

    assertThat(componentsRefCache.getComponent(2).getKey()).isEqualTo("MODULE_KEY");
    assertThat(componentsRefCache.getComponent(2).getUuid()).isNotNull();

    assertThat(componentsRefCache.getComponent(3).getKey()).isEqualTo("MODULE_KEY:src/main/java/dir");
    assertThat(componentsRefCache.getComponent(3).getUuid()).isNotNull();

    assertThat(componentsRefCache.getComponent(4).getKey()).isEqualTo("MODULE_KEY:src/main/java/dir/Foo.java");
    assertThat(componentsRefCache.getComponent(4).getUuid()).isNotNull();
  }


  @Test
  public void use_latest_module_for_files_key() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.MODULE)
      .setKey("SUB_MODULE_KEY")
      .setName("Sub Module")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(5)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(5)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    assertThat(componentsRefCache.getComponent(4).getKey()).isEqualTo("SUB_MODULE_KEY:src/main/java/dir");
    assertThat(componentsRefCache.getComponent(5).getKey()).isEqualTo("SUB_MODULE_KEY:src/main/java/dir/Foo.java");
  }


  @Test
  public void use_branch_to_generate_keys() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .setBranch("origin/master")
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    assertThat(componentsRefCache.getComponent(1).getKey()).isEqualTo("PROJECT_KEY:origin/master");
    assertThat(componentsRefCache.getComponent(2).getKey()).isEqualTo("MODULE_KEY:origin/master");
    assertThat(componentsRefCache.getComponent(3).getKey()).isEqualTo("MODULE_KEY:origin/master:src/main/java/dir");
    assertThat(componentsRefCache.getComponent(4).getKey()).isEqualTo("MODULE_KEY:origin/master:src/main/java/dir/Foo.java");
  }


}
