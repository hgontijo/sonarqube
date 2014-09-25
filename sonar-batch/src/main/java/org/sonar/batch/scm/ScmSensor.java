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
package org.sonar.batch.scm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Status;
import org.sonar.api.batch.scm.BlameCommand.BlameResult;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.measure.internal.DefaultMeasure;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.PropertiesBuilder;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.batch.protocol.input.FileData;
import org.sonar.batch.protocol.input.ProjectReferentials;

import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public final class ScmSensor implements Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(ScmSensor.class);

  private static final Pattern NON_ASCII_CHARS = Pattern.compile("[^\\x00-\\x7F]");
  private static final Pattern ACCENT_CODES = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private final ProjectDefinition projectDefinition;
  private final ScmConfiguration configuration;
  private final FileSystem fs;
  private final ProjectReferentials projectReferentials;

  public ScmSensor(ProjectDefinition projectDefinition, ScmConfiguration configuration,
    ProjectReferentials projectReferentials, FileSystem fs) {
    this.projectDefinition = projectDefinition;
    this.configuration = configuration;
    this.projectReferentials = projectReferentials;
    this.fs = fs;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("SCM Sensor")
      .provides(CoreMetrics.SCM_AUTHORS_BY_LINE,
        CoreMetrics.SCM_LAST_COMMIT_DATETIMES_BY_LINE,
        CoreMetrics.SCM_REVISIONS_BY_LINE);
  }

  @Override
  public void execute(final SensorContext context) {
    if (configuration.provider() == null) {
      LOG.info("No SCM provider");
      return;
    }

    TimeProfiler profiler = new TimeProfiler().start("Retrieve SCM blame information with encoding " + Charset.defaultCharset());

    List<InputFile> filesToBlame = new LinkedList<InputFile>();
    for (InputFile f : fs.inputFiles(fs.predicates().all())) {
      FileData fileData = projectReferentials.fileData(projectDefinition.getKeyWithBranch(), f.relativePath());
      if (f.status() == Status.SAME
        && fileData != null
        && fileData.scmAuthorsByLine() != null
        && fileData.scmLastCommitDatetimesByLine() != null
        && fileData.scmRevisionsByLine() != null) {
        saveMeasures(context, f, fileData.scmAuthorsByLine(), fileData.scmLastCommitDatetimesByLine(), fileData.scmRevisionsByLine());
      } else {
        filesToBlame.add(f);
      }
    }
    configuration.provider().blameCommand().blame(fs, filesToBlame, new BlameResult() {

      @Override
      public void add(InputFile file, List<BlameLine> lines) {

        PropertiesBuilder<Integer, String> authors = propertiesBuilder(CoreMetrics.SCM_AUTHORS_BY_LINE);
        PropertiesBuilder<Integer, String> dates = propertiesBuilder(CoreMetrics.SCM_LAST_COMMIT_DATETIMES_BY_LINE);
        PropertiesBuilder<Integer, String> revisions = propertiesBuilder(CoreMetrics.SCM_REVISIONS_BY_LINE);

        int lineNumber = 1;
        for (BlameLine line : lines) {
          authors.add(lineNumber, normalizeString(line.getAuthor()));
          dates.add(lineNumber, DateUtils.formatDateTime(line.getDate()));
          revisions.add(lineNumber, line.getRevision());

          lineNumber++;
          // SONARPLUGINS-3097 For some SCM blame is missing on last empty line
          if (lineNumber > lines.size() && lineNumber == file.lines()) {
            authors.add(lineNumber, normalizeString(line.getAuthor()));
            dates.add(lineNumber, DateUtils.formatDateTime(line.getDate()));
            revisions.add(lineNumber, line.getRevision());
          }
        }

        saveMeasures(context, file, authors.buildData(), dates.buildData(), revisions.buildData());

      }
    });
    profiler.stop();
  }

  private String normalizeString(String inputString) {
    String lowerCasedString = inputString.toLowerCase();
    String stringWithoutAccents = removeAccents(lowerCasedString);
    return removeNonAsciiCharacters(stringWithoutAccents);
  }

  private String removeAccents(String inputString) {
    String unicodeDecomposedString = Normalizer.normalize(inputString, Normalizer.Form.NFD);
    return ACCENT_CODES.matcher(unicodeDecomposedString).replaceAll("");
  }

  private String removeNonAsciiCharacters(String inputString) {
    return NON_ASCII_CHARS.matcher(inputString).replaceAll("_");
  }

  private static PropertiesBuilder<Integer, String> propertiesBuilder(Metric metric) {
    return new PropertiesBuilder<Integer, String>(metric);
  }

  /**
   * This method is synchronized since it is allowed for plugins to compute blame in parallel.
   */
  private synchronized void saveMeasures(SensorContext context, InputFile f, String scmAuthorsByLine, String scmLastCommitDatetimesByLine, String scmRevisionsByLine) {
    ((DefaultMeasure<String>) context.<String>newMeasure()
      .onFile(f)
      .forMetric(CoreMetrics.SCM_AUTHORS_BY_LINE)
      .withValue(scmAuthorsByLine))
      .setFromCore()
      .save();
    ((DefaultMeasure<String>) context.<String>newMeasure()
      .onFile(f)
      .forMetric(CoreMetrics.SCM_LAST_COMMIT_DATETIMES_BY_LINE)
      .withValue(scmLastCommitDatetimesByLine))
      .setFromCore()
      .save();
    ((DefaultMeasure<String>) context.<String>newMeasure()
      .onFile(f)
      .forMetric(CoreMetrics.SCM_REVISIONS_BY_LINE)
      .withValue(scmRevisionsByLine))
      .setFromCore()
      .save();
  }
}