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

import java.util.List;
import org.sonar.api.config.EmailSettings;
import org.sonar.api.issue.action.Actions;
import org.sonar.api.profiles.AnnotationProfileParser;
import org.sonar.api.profiles.XMLProfileParser;
import org.sonar.api.profiles.XMLProfileSerializer;
import org.sonar.api.resources.Languages;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.rules.AnnotationRuleParser;
import org.sonar.api.rules.XMLRuleParser;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonar.core.component.SnapshotPerspectives;
import org.sonar.core.computation.dbcleaner.DefaultPurgeTask;
import org.sonar.core.computation.dbcleaner.IndexPurgeListener;
import org.sonar.core.computation.dbcleaner.ProjectCleaner;
import org.sonar.core.computation.dbcleaner.period.DefaultPeriodCleaner;
import org.sonar.core.issue.IssueFilterSerializer;
import org.sonar.core.issue.IssueUpdater;
import org.sonar.core.issue.workflow.FunctionExecutor;
import org.sonar.core.issue.workflow.IssueWorkflow;
import org.sonar.core.metric.DefaultMetricFinder;
import org.sonar.core.notification.DefaultNotificationManager;
import org.sonar.core.permission.PermissionFacade;
import org.sonar.core.qualitygate.db.ProjectQgateAssociationDao;
import org.sonar.core.qualitygate.db.QualityGateConditionDao;
import org.sonar.core.qualitygate.db.QualityGateDao;
import org.sonar.core.resource.DefaultResourcePermissions;
import org.sonar.core.test.TestPlanPerspectiveLoader;
import org.sonar.core.test.TestablePerspectiveLoader;
import org.sonar.core.timemachine.Periods;
import org.sonar.core.user.DefaultUserFinder;
import org.sonar.core.user.HibernateUserFinder;
import org.sonar.jpa.dao.MeasuresDao;
import org.sonar.server.activity.ActivityService;
import org.sonar.server.activity.RubyQProfileActivityService;
import org.sonar.server.activity.index.ActivityIndex;
import org.sonar.server.activity.index.ActivityIndexDefinition;
import org.sonar.server.activity.index.ActivityIndexer;
import org.sonar.server.activity.ws.ActivitiesWebService;
import org.sonar.server.activity.ws.ActivityMapping;
import org.sonar.server.authentication.ws.AuthenticationWs;
import org.sonar.server.batch.BatchIndex;
import org.sonar.server.batch.BatchWs;
import org.sonar.server.batch.GlobalRepositoryAction;
import org.sonar.server.batch.IssuesAction;
import org.sonar.server.batch.ProjectRepositoryAction;
import org.sonar.server.batch.ProjectRepositoryLoader;
import org.sonar.server.batch.UsersAction;
import org.sonar.server.charts.ChartFactory;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.component.ComponentService;
import org.sonar.server.component.DefaultComponentFinder;
import org.sonar.server.component.DefaultRubyComponentService;
import org.sonar.server.component.ws.ComponentAppAction;
import org.sonar.server.component.ws.ComponentsWs;
import org.sonar.server.component.ws.EventsWs;
import org.sonar.server.component.ws.ProjectsWs;
import org.sonar.server.component.ws.ProvisionedProjectsAction;
import org.sonar.server.component.ws.ResourcesWs;
import org.sonar.server.computation.ComputationThreadLauncher;
import org.sonar.server.computation.ReportQueue;
import org.sonar.server.computation.ws.ComputationWebService;
import org.sonar.server.computation.ws.HistoryWsAction;
import org.sonar.server.computation.ws.IsQueueEmptyWebService;
import org.sonar.server.computation.ws.QueueWsAction;
import org.sonar.server.computation.ws.SubmitReportWsAction;
import org.sonar.server.config.ws.PropertiesWs;
import org.sonar.server.dashboard.ws.DashboardsShowAction;
import org.sonar.server.dashboard.ws.DashboardsWebService;
import org.sonar.server.debt.DebtCharacteristicsXMLImporter;
import org.sonar.server.debt.DebtModelBackup;
import org.sonar.server.debt.DebtModelLookup;
import org.sonar.server.debt.DebtModelOperations;
import org.sonar.server.debt.DebtModelPluginRepository;
import org.sonar.server.debt.DebtModelService;
import org.sonar.server.debt.DebtModelXMLExporter;
import org.sonar.server.debt.DebtRulesXMLImporter;
import org.sonar.server.design.FileDesignWidget;
import org.sonar.server.design.ws.DependenciesWs;
import org.sonar.server.duplication.ws.DuplicationsJsonWriter;
import org.sonar.server.duplication.ws.DuplicationsParser;
import org.sonar.server.duplication.ws.DuplicationsWs;
import org.sonar.server.es.IndexCreator;
import org.sonar.server.es.IndexDefinitions;
import org.sonar.server.issue.ActionService;
import org.sonar.server.issue.AddTagsAction;
import org.sonar.server.issue.AssignAction;
import org.sonar.server.issue.CommentAction;
import org.sonar.server.issue.InternalRubyIssueService;
import org.sonar.server.issue.IssueBulkChangeService;
import org.sonar.server.issue.IssueChangelogFormatter;
import org.sonar.server.issue.IssueChangelogService;
import org.sonar.server.issue.IssueCommentService;
import org.sonar.server.issue.IssueQueryService;
import org.sonar.server.issue.IssueService;
import org.sonar.server.issue.PlanAction;
import org.sonar.server.issue.RemoveTagsAction;
import org.sonar.server.issue.ServerIssueStorage;
import org.sonar.server.issue.SetSeverityAction;
import org.sonar.server.issue.TransitionAction;
import org.sonar.server.issue.actionplan.ActionPlanService;
import org.sonar.server.issue.actionplan.ActionPlanWs;
import org.sonar.server.issue.filter.IssueFilterService;
import org.sonar.server.issue.filter.IssueFilterWriter;
import org.sonar.server.issue.filter.IssueFilterWs;
import org.sonar.server.issue.index.IssueAuthorizationIndexer;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.notification.ChangesOnMyIssueNotificationDispatcher;
import org.sonar.server.issue.notification.DoNotFixNotificationDispatcher;
import org.sonar.server.issue.notification.IssueChangesEmailTemplate;
import org.sonar.server.issue.notification.MyNewIssuesEmailTemplate;
import org.sonar.server.issue.notification.MyNewIssuesNotificationDispatcher;
import org.sonar.server.issue.notification.NewIssuesEmailTemplate;
import org.sonar.server.issue.notification.NewIssuesNotificationDispatcher;
import org.sonar.server.issue.notification.NewIssuesNotificationFactory;
import org.sonar.server.issue.ws.ComponentTagsAction;
import org.sonar.server.issue.ws.IssueActionsWriter;
import org.sonar.server.issue.ws.IssueShowAction;
import org.sonar.server.issue.ws.IssuesWs;
import org.sonar.server.issue.ws.SetTagsAction;
import org.sonar.server.language.ws.LanguageWs;
import org.sonar.server.language.ws.ListAction;
import org.sonar.server.measure.MeasureFilterEngine;
import org.sonar.server.measure.MeasureFilterExecutor;
import org.sonar.server.measure.MeasureFilterFactory;
import org.sonar.server.measure.ws.ManualMeasuresWs;
import org.sonar.server.measure.ws.MetricsWs;
import org.sonar.server.measure.ws.TimeMachineWs;
import org.sonar.server.notifications.NotificationCenter;
import org.sonar.server.notifications.NotificationService;
import org.sonar.server.permission.InternalPermissionService;
import org.sonar.server.permission.InternalPermissionTemplateService;
import org.sonar.server.permission.PermissionFinder;
import org.sonar.server.permission.ws.PermissionsWs;
import org.sonar.server.platform.BackendCleanup;
import org.sonar.server.platform.ServerLifecycleNotifier;
import org.sonar.server.platform.SettingsChangeNotifier;
import org.sonar.server.platform.monitoring.DatabaseMonitor;
import org.sonar.server.platform.monitoring.EsMonitor;
import org.sonar.server.platform.monitoring.JvmPropertiesMonitor;
import org.sonar.server.platform.monitoring.PluginsMonitor;
import org.sonar.server.platform.monitoring.SonarQubeMonitor;
import org.sonar.server.platform.monitoring.SystemMonitor;
import org.sonar.server.platform.ws.L10nWs;
import org.sonar.server.platform.ws.MigrateDbSystemWsAction;
import org.sonar.server.platform.ws.ServerWs;
import org.sonar.server.platform.ws.SystemInfoWsAction;
import org.sonar.server.platform.ws.SystemRestartWsAction;
import org.sonar.server.platform.ws.SystemStatusWsAction;
import org.sonar.server.platform.ws.SystemWs;
import org.sonar.server.platform.ws.UpgradesSystemWsAction;
import org.sonar.server.plugins.PluginDownloader;
import org.sonar.server.plugins.ServerExtensionInstaller;
import org.sonar.server.plugins.UpdateCenterClient;
import org.sonar.server.plugins.UpdateCenterMatrixFactory;
import org.sonar.server.plugins.ws.AvailablePluginsWsAction;
import org.sonar.server.plugins.ws.CancelAllPluginsWsAction;
import org.sonar.server.plugins.ws.InstallPluginsWsAction;
import org.sonar.server.plugins.ws.InstalledPluginsWsAction;
import org.sonar.server.plugins.ws.PendingPluginsWsAction;
import org.sonar.server.plugins.ws.PluginUpdateAggregator;
import org.sonar.server.plugins.ws.PluginWSCommons;
import org.sonar.server.plugins.ws.PluginsWs;
import org.sonar.server.plugins.ws.UninstallPluginsWsAction;
import org.sonar.server.plugins.ws.UpdatePluginsWsAction;
import org.sonar.server.plugins.ws.UpdatesPluginsWsAction;
import org.sonar.server.properties.ProjectSettingsFactory;
import org.sonar.server.qualitygate.QgateProjectFinder;
import org.sonar.server.qualitygate.QualityGates;
import org.sonar.server.qualitygate.ws.QGatesAppAction;
import org.sonar.server.qualitygate.ws.QGatesCopyAction;
import org.sonar.server.qualitygate.ws.QGatesCreateAction;
import org.sonar.server.qualitygate.ws.QGatesCreateConditionAction;
import org.sonar.server.qualitygate.ws.QGatesDeleteConditionAction;
import org.sonar.server.qualitygate.ws.QGatesDeselectAction;
import org.sonar.server.qualitygate.ws.QGatesDestroyAction;
import org.sonar.server.qualitygate.ws.QGatesListAction;
import org.sonar.server.qualitygate.ws.QGatesRenameAction;
import org.sonar.server.qualitygate.ws.QGatesSearchAction;
import org.sonar.server.qualitygate.ws.QGatesSelectAction;
import org.sonar.server.qualitygate.ws.QGatesSetAsDefaultAction;
import org.sonar.server.qualitygate.ws.QGatesShowAction;
import org.sonar.server.qualitygate.ws.QGatesUnsetDefaultAction;
import org.sonar.server.qualitygate.ws.QGatesUpdateConditionAction;
import org.sonar.server.qualitygate.ws.QGatesWs;
import org.sonar.server.qualityprofile.BuiltInProfiles;
import org.sonar.server.qualityprofile.QProfileBackuper;
import org.sonar.server.qualityprofile.QProfileComparison;
import org.sonar.server.qualityprofile.QProfileCopier;
import org.sonar.server.qualityprofile.QProfileExporters;
import org.sonar.server.qualityprofile.QProfileFactory;
import org.sonar.server.qualityprofile.QProfileLoader;
import org.sonar.server.qualityprofile.QProfileLookup;
import org.sonar.server.qualityprofile.QProfileProjectLookup;
import org.sonar.server.qualityprofile.QProfileProjectOperations;
import org.sonar.server.qualityprofile.QProfileReset;
import org.sonar.server.qualityprofile.QProfileService;
import org.sonar.server.qualityprofile.QProfiles;
import org.sonar.server.qualityprofile.RuleActivator;
import org.sonar.server.qualityprofile.RuleActivatorContextFactory;
import org.sonar.server.qualityprofile.ws.BulkRuleActivationActions;
import org.sonar.server.qualityprofile.ws.ProfilesWs;
import org.sonar.server.qualityprofile.ws.ProjectAssociationActions;
import org.sonar.server.qualityprofile.ws.QProfileBackupAction;
import org.sonar.server.qualityprofile.ws.QProfileChangeParentAction;
import org.sonar.server.qualityprofile.ws.QProfileChangelogAction;
import org.sonar.server.qualityprofile.ws.QProfileCompareAction;
import org.sonar.server.qualityprofile.ws.QProfileCopyAction;
import org.sonar.server.qualityprofile.ws.QProfileCreateAction;
import org.sonar.server.qualityprofile.ws.QProfileDeleteAction;
import org.sonar.server.qualityprofile.ws.QProfileExportAction;
import org.sonar.server.qualityprofile.ws.QProfileExportersAction;
import org.sonar.server.qualityprofile.ws.QProfileImportersAction;
import org.sonar.server.qualityprofile.ws.QProfileInheritanceAction;
import org.sonar.server.qualityprofile.ws.QProfileProjectsAction;
import org.sonar.server.qualityprofile.ws.QProfileRenameAction;
import org.sonar.server.qualityprofile.ws.QProfileRestoreAction;
import org.sonar.server.qualityprofile.ws.QProfileRestoreBuiltInAction;
import org.sonar.server.qualityprofile.ws.QProfileSearchAction;
import org.sonar.server.qualityprofile.ws.QProfileSetDefaultAction;
import org.sonar.server.qualityprofile.ws.QProfilesWs;
import org.sonar.server.qualityprofile.ws.RuleActivationActions;
import org.sonar.server.rule.DefaultRuleFinder;
import org.sonar.server.rule.DeprecatedRulesDefinitionLoader;
import org.sonar.server.rule.RubyRuleService;
import org.sonar.server.rule.RuleCreator;
import org.sonar.server.rule.RuleDefinitionsLoader;
import org.sonar.server.rule.RuleDeleter;
import org.sonar.server.rule.RuleOperations;
import org.sonar.server.rule.RuleRepositories;
import org.sonar.server.rule.RuleService;
import org.sonar.server.rule.RuleUpdater;
import org.sonar.server.rule.ws.ActiveRuleCompleter;
import org.sonar.server.rule.ws.AppAction;
import org.sonar.server.rule.ws.DeleteAction;
import org.sonar.server.rule.ws.RepositoriesAction;
import org.sonar.server.rule.ws.RuleMapping;
import org.sonar.server.rule.ws.RulesWebService;
import org.sonar.server.rule.ws.SearchAction;
import org.sonar.server.rule.ws.TagsAction;
import org.sonar.server.rule.ws.UpdateAction;
import org.sonar.server.source.HtmlSourceDecorator;
import org.sonar.server.source.SourceService;
import org.sonar.server.source.index.SourceLineIndex;
import org.sonar.server.source.index.SourceLineIndexDefinition;
import org.sonar.server.source.index.SourceLineIndexer;
import org.sonar.server.source.ws.HashAction;
import org.sonar.server.source.ws.IndexAction;
import org.sonar.server.source.ws.LinesAction;
import org.sonar.server.source.ws.RawAction;
import org.sonar.server.source.ws.ScmAction;
import org.sonar.server.source.ws.ShowAction;
import org.sonar.server.source.ws.SourcesWs;
import org.sonar.server.test.CoverageService;
import org.sonar.server.test.index.TestIndex;
import org.sonar.server.test.index.TestIndexDefinition;
import org.sonar.server.test.index.TestIndexer;
import org.sonar.server.test.ws.TestsCoveredFilesAction;
import org.sonar.server.test.ws.TestsListAction;
import org.sonar.server.test.ws.TestsWs;
import org.sonar.server.text.MacroInterpreter;
import org.sonar.server.text.RubyTextService;
import org.sonar.server.ui.PageDecorations;
import org.sonar.server.ui.Views;
import org.sonar.server.ui.ws.ComponentNavigationAction;
import org.sonar.server.ui.ws.GlobalNavigationAction;
import org.sonar.server.ui.ws.NavigationWs;
import org.sonar.server.ui.ws.SettingsNavigationAction;
import org.sonar.server.updatecenter.ws.UpdateCenterWs;
import org.sonar.server.user.DefaultUserService;
import org.sonar.server.user.GroupMembershipFinder;
import org.sonar.server.user.GroupMembershipService;
import org.sonar.server.user.NewUserNotifier;
import org.sonar.server.user.SecurityRealmFactory;
import org.sonar.server.user.UserUpdater;
import org.sonar.server.user.index.UserIndex;
import org.sonar.server.user.index.UserIndexDefinition;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.user.ws.FavoritesWs;
import org.sonar.server.user.ws.UserPropertiesWs;
import org.sonar.server.user.ws.UsersWs;
import org.sonar.server.util.BooleanTypeValidation;
import org.sonar.server.util.FloatTypeValidation;
import org.sonar.server.util.IntegerTypeValidation;
import org.sonar.server.util.StringListTypeValidation;
import org.sonar.server.util.StringTypeValidation;
import org.sonar.server.util.TextTypeValidation;
import org.sonar.server.util.TypeValidations;
import org.sonar.server.view.index.ViewIndex;
import org.sonar.server.view.index.ViewIndexDefinition;
import org.sonar.server.view.index.ViewIndexer;
import org.sonar.server.ws.ListingWs;
import org.sonar.server.ws.WebServiceEngine;

public class ComponentLevel4 extends ComponentLevel {

  private final List<Object> level4AddedComponents;

  public ComponentLevel4(ComponentLevel parent, List<Object> level4AddedComponents) {
    super("level4", parent);
    this.level4AddedComponents = level4AddedComponents;
  }

  @Override
  protected void configureLevel() {
    add(
      PluginDownloader.class,
      ChartFactory.class,
      Views.class,
      ResourceTypes.class,
      SettingsChangeNotifier.class,
      PageDecorations.class,
      DefaultResourcePermissions.class,
      Periods.class,
      ServerWs.class,
      BackendCleanup.class,
      IndexDefinitions.class,
      IndexCreator.class,

      // Activity
      ActivityService.class,
      ActivityIndexDefinition.class,
      ActivityIndexer.class,
      ActivityIndex.class,

      // batch
      BatchIndex.class,
      GlobalRepositoryAction.class,
      ProjectRepositoryAction.class,
      ProjectRepositoryLoader.class,
      SubmitReportWsAction.class,
      IssuesAction.class,
      UsersAction.class,
      BatchWs.class,

      // Dashboard
      DashboardsWebService.class,
      DashboardsShowAction.class,

      // update center
      UpdateCenterClient.class,
      UpdateCenterMatrixFactory.class,
      UpdateCenterWs.class,

      // quality profile
      XMLProfileParser.class,
      XMLProfileSerializer.class,
      AnnotationProfileParser.class,
      QProfiles.class,
      QProfileLookup.class,
      QProfileProjectOperations.class,
      QProfileProjectLookup.class,
      QProfileComparison.class,
      BuiltInProfiles.class,
      QProfileRestoreBuiltInAction.class,
      QProfileSearchAction.class,
      QProfileSetDefaultAction.class,
      QProfileProjectsAction.class,
      QProfileDeleteAction.class,
      QProfileRenameAction.class,
      QProfileCopyAction.class,
      QProfileBackupAction.class,
      QProfileRestoreAction.class,
      QProfileCreateAction.class,
      QProfileImportersAction.class,
      QProfileInheritanceAction.class,
      QProfileChangeParentAction.class,
      QProfileChangelogAction.class,
      QProfileCompareAction.class,
      QProfileExportAction.class,
      QProfileExportersAction.class,
      QProfilesWs.class,
      ProfilesWs.class,
      RuleActivationActions.class,
      BulkRuleActivationActions.class,
      ProjectAssociationActions.class,
      RuleActivator.class,
      QProfileLoader.class,
      QProfileExporters.class,
      QProfileService.class,
      RuleActivatorContextFactory.class,
      QProfileFactory.class,
      QProfileCopier.class,
      QProfileBackuper.class,
      QProfileReset.class,
      RubyQProfileActivityService.class,

      // rule
      AnnotationRuleParser.class,
      XMLRuleParser.class,
      DefaultRuleFinder.class,
      RuleOperations.class,
      RubyRuleService.class,
      RuleRepositories.class,
      DeprecatedRulesDefinitionLoader.class,
      RuleDefinitionsLoader.class,
      RulesDefinitionXmlLoader.class,
      RuleService.class,
      RuleUpdater.class,
      RuleCreator.class,
      RuleDeleter.class,
      UpdateAction.class,
      RulesWebService.class,
      SearchAction.class,
      org.sonar.server.rule.ws.ShowAction.class,
      org.sonar.server.rule.ws.CreateAction.class,
      DeleteAction.class,
      TagsAction.class,
      RuleMapping.class,
      ActiveRuleCompleter.class,
      RepositoriesAction.class,
      AppAction.class,

      // languages
      Languages.class,
      LanguageWs.class,
      ListAction.class,

      // activity
      ActivitiesWebService.class,
      org.sonar.server.activity.ws.SearchAction.class,
      ActivityMapping.class);

    // measure
    add(MeasuresDao.class, false);

    add(
      MeasureFilterFactory.class,
      MeasureFilterExecutor.class,
      MeasureFilterEngine.class,
      DefaultMetricFinder.class,
      ServerLifecycleNotifier.class,
      TimeMachineWs.class,
      ManualMeasuresWs.class,
      MetricsWs.class,

      // quality gates
      QualityGateDao.class,
      QualityGateConditionDao.class,
      QualityGates.class,
      ProjectQgateAssociationDao.class,
      QgateProjectFinder.class,

      QGatesListAction.class,
      QGatesSearchAction.class,
      QGatesShowAction.class,
      QGatesCreateAction.class,
      QGatesRenameAction.class,
      QGatesCopyAction.class,
      QGatesDestroyAction.class,
      QGatesSetAsDefaultAction.class,
      QGatesUnsetDefaultAction.class,
      QGatesSelectAction.class,
      QGatesDeselectAction.class,
      QGatesCreateConditionAction.class,
      QGatesDeleteConditionAction.class,
      QGatesUpdateConditionAction.class,
      QGatesAppAction.class,
      QGatesWs.class,

      // web services
      WebServiceEngine.class,
      ListingWs.class,

      // localization
      L10nWs.class,

      // authentication
      AuthenticationWs.class,

      // users
      SecurityRealmFactory.class,
      HibernateUserFinder.class,
      NewUserNotifier.class,
      DefaultUserFinder.class,
      DefaultUserService.class,
      UsersWs.class,
      org.sonar.server.user.ws.CreateAction.class,
      org.sonar.server.user.ws.UpdateAction.class,
      org.sonar.server.user.ws.DeactivateAction.class,
      org.sonar.server.user.ws.ChangePasswordAction.class,
      org.sonar.server.user.ws.CurrentUserAction.class,
      org.sonar.server.user.ws.SearchAction.class,
      org.sonar.server.user.ws.GroupsAction.class,
      org.sonar.server.issue.ws.AuthorsAction.class,
      FavoritesWs.class,
      UserPropertiesWs.class,
      UserIndexDefinition.class,
      UserIndexer.class,
      UserIndex.class,
      UserUpdater.class,

      // groups
      GroupMembershipService.class,
      GroupMembershipFinder.class,

      // permissions
      PermissionFacade.class,
      InternalPermissionService.class,
      InternalPermissionTemplateService.class,
      PermissionFinder.class,
      PermissionsWs.class,

      // components
      DefaultComponentFinder.class,
      DefaultRubyComponentService.class,
      ComponentService.class,
      ResourcesWs.class,
      ComponentsWs.class,
      ProjectsWs.class,
      ComponentAppAction.class,
      org.sonar.server.component.ws.SearchAction.class,
      EventsWs.class,
      ComponentCleanerService.class,
      ProvisionedProjectsAction.class,

      // views
      ViewIndexDefinition.class,
      ViewIndexer.class,
      ViewIndex.class,

      // issues
      IssueIndexDefinition.class,
      IssueIndexer.class,
      IssueAuthorizationIndexer.class,
      ServerIssueStorage.class,
      IssueUpdater.class,
      FunctionExecutor.class,
      IssueWorkflow.class,
      IssueCommentService.class,
      InternalRubyIssueService.class,
      IssueChangelogService.class,
      ActionService.class,
      Actions.class,
      IssueBulkChangeService.class,
      IssueChangelogFormatter.class,
      IssuesWs.class,
      IssueShowAction.class,
      org.sonar.server.issue.ws.SearchAction.class,
      org.sonar.server.issue.ws.TagsAction.class,
      SetTagsAction.class,
      ComponentTagsAction.class,
      IssueService.class,
      IssueActionsWriter.class,
      IssueQueryService.class,
      NewIssuesEmailTemplate.class,
      MyNewIssuesEmailTemplate.class,
      IssueChangesEmailTemplate.class,
      ChangesOnMyIssueNotificationDispatcher.class,
      ChangesOnMyIssueNotificationDispatcher.newMetadata(),
      NewIssuesNotificationDispatcher.class,
      NewIssuesNotificationDispatcher.newMetadata(),
      MyNewIssuesNotificationDispatcher.class,
      MyNewIssuesNotificationDispatcher.newMetadata(),
      DoNotFixNotificationDispatcher.class,
      DoNotFixNotificationDispatcher.newMetadata(),
      NewIssuesNotificationFactory.class,

      // issue filters
      IssueFilterService.class,
      IssueFilterSerializer.class,
      IssueFilterWs.class,
      IssueFilterWriter.class,
      org.sonar.server.issue.filter.AppAction.class,
      org.sonar.server.issue.filter.ShowAction.class,
      org.sonar.server.issue.filter.FavoritesAction.class,

      // action plan
      ActionPlanWs.class,
      ActionPlanService.class,

      // issues actions
      AssignAction.class,
      PlanAction.class,
      SetSeverityAction.class,
      CommentAction.class,
      TransitionAction.class,
      AddTagsAction.class,
      RemoveTagsAction.class,

      // technical debt
      DebtModelService.class,
      DebtModelOperations.class,
      DebtModelLookup.class,
      DebtModelBackup.class,
      DebtModelPluginRepository.class,
      DebtModelXMLExporter.class,
      DebtRulesXMLImporter.class,
      DebtCharacteristicsXMLImporter.class,

      // source
      HtmlSourceDecorator.class,
      SourceService.class,
      SourcesWs.class,
      ShowAction.class,
      LinesAction.class,
      HashAction.class,
      RawAction.class,
      IndexAction.class,
      ScmAction.class,
      SourceLineIndexDefinition.class,
      SourceLineIndex.class,
      SourceLineIndexer.class,

      // Duplications
      DuplicationsParser.class,
      DuplicationsWs.class,
      DuplicationsJsonWriter.class,
      org.sonar.server.duplication.ws.ShowAction.class,

      // text
      MacroInterpreter.class,
      RubyTextService.class,

      // Notifications
      EmailSettings.class,
      NotificationService.class,
      NotificationCenter.class,
      DefaultNotificationManager.class,

      // Tests
      CoverageService.class,
      TestsWs.class,
      TestsCoveredFilesAction.class,
      TestsListAction.class,
      TestIndexDefinition.class,
      TestIndex.class,
      TestIndexer.class,

      // Properties
      PropertiesWs.class,

      // graphs and perspective related classes
      TestablePerspectiveLoader.class,
      TestPlanPerspectiveLoader.class,
      SnapshotPerspectives.class,

      // Type validation
      TypeValidations.class,
      IntegerTypeValidation.class,
      FloatTypeValidation.class,
      BooleanTypeValidation.class,
      TextTypeValidation.class,
      StringTypeValidation.class,
      StringListTypeValidation.class,

      // Design
      FileDesignWidget.class,
      DependenciesWs.class,
      org.sonar.server.design.ws.ShowAction.class,

      // System

      SystemRestartWsAction.class,
      SystemInfoWsAction.class,
      UpgradesSystemWsAction.class,
      MigrateDbSystemWsAction.class,
      SystemStatusWsAction.class,
      SystemWs.class,
      SystemMonitor.class,
      SonarQubeMonitor.class,
      EsMonitor.class,
      PluginsMonitor.class,
      JvmPropertiesMonitor.class,
      DatabaseMonitor.class,

      // Plugins WS
      PluginWSCommons.class,
      PluginUpdateAggregator.class,
      InstalledPluginsWsAction.class,
      AvailablePluginsWsAction.class,
      UpdatesPluginsWsAction.class,
      PendingPluginsWsAction.class,
      InstallPluginsWsAction.class,
      UpdatePluginsWsAction.class,
      UninstallPluginsWsAction.class,
      CancelAllPluginsWsAction.class,
      PluginsWs.class,

      // Compute engine
      ReportQueue.class,
      ComputationThreadLauncher.class,
      ComputationWebService.class,
      IsQueueEmptyWebService.class,
      QueueWsAction.class,
      HistoryWsAction.class,
      DefaultPeriodCleaner.class,
      DefaultPurgeTask.class,
      ProjectCleaner.class,
      ProjectSettingsFactory.class,
      IndexPurgeListener.class,

      // UI
      GlobalNavigationAction.class,
      SettingsNavigationAction.class,
      ComponentNavigationAction.class,
      NavigationWs.class);

    addAll(level4AddedComponents);
  }

  @Override
  public ComponentLevel start() {
    ServerExtensionInstaller extensionInstaller = getComponentByType(ServerExtensionInstaller.class);
    extensionInstaller.installExtensions(getContainer());

    super.start();

    return this;
  }
}
