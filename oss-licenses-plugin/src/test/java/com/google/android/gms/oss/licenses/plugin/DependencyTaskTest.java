/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.oss.licenses.plugin;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Tests for {@link DependencyTask} */
@RunWith(JUnit4.class)
public class DependencyTaskTest {
  private Project project;
  private DependencyTask dependencyTask;
  private ArtifactInfo artifact1 = new ArtifactInfo("groupA", "deps1", "1", "dependencies/groupA/deps1.pom", "dependencies/groupA/deps1.txt");
  private ArtifactInfo artifact2 = new ArtifactInfo("groupB", "deps2", "2", "dependencies/groupB/bcd/deps2.pom", "dependencies/groupB/abc/deps2.txt");
  private ArtifactInfo artifact3 = new ArtifactInfo("com.google.android.gms", "deps3", "3", null, "src/test/resources/dependencies/groupC/deps3.txt");
  private ArtifactInfo artifact4 = new ArtifactInfo("com.google.firebase", "deps4", "4", null, "src/test/resources/dependencies/groupD/deps4.txt");
  private ArtifactInfo artifact5 = new ArtifactInfo("groupE", "deps5", "5", "dependencies/groupE/deps5.pom", "dependencies/groupE/deps5.txt");
  private File dependencies = resource("testDependency.json");
  private DependencyHandler mockDependencyHandler = prepareDependencyHandler();

  @Before
  public void setUp() {
    project = ProjectBuilder.builder().build();
    dependencyTask = project.getTasks().create("getDependency", DependencyTask.class);
    dependencyTask.setConfigurations(project.getConfigurations());
    dependencyTask.setDependencyHandler(mockDependencyHandler);
  }

  private File resource(String relativePath) {
    return new File("src/test/resources", relativePath);
  }

  @Test
  public void testCheckArtifactSet_missingSet() {
    dependencyTask.artifactInfos = new HashSet<>(Arrays.asList(artifact1, artifact2));

    assertFalse(dependencyTask.checkArtifactInfoSet(dependencies));
  }

  @Test
  public void testCheckArtifactSet_correctSet() {
    dependencyTask.artifactInfos = new HashSet<>(Arrays.asList(artifact1, artifact2, artifact3, artifact4));
    assertTrue(dependencyTask.checkArtifactInfoSet(dependencies));
  }

  @Test
  public void testCheckArtifactSet_addMoreSet() {
    dependencyTask.artifactInfos = new HashSet<>(Arrays.asList(artifact1, artifact2, artifact3, artifact4, artifact5));
    assertFalse(dependencyTask.checkArtifactInfoSet(dependencies));
  }

  @Test
  public void testCheckArtifactSet_replaceSet() {
    dependencyTask.artifactInfos = new HashSet<>(Arrays.asList(artifact1, artifact2, artifact3, artifact5));
    assertFalse(dependencyTask.checkArtifactInfoSet(dependencies));
  }

  @Test
  public void testGetResolvedArtifacts_cannotResolve() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(false);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(emptySet()));
  }

  @Test
  public void testGetResolvedArtifacts_isTest() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("testCompile");
    when(configuration.isCanBeResolved()).thenReturn(true);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(emptySet()));
  }

  @Test
  public void testGetResolvedArtifacts_isNotPackaged() {
    Set<ResolvedArtifact> artifactSet = (Set<ResolvedArtifact>) mock(Set.class);
    ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
    when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("annotationProcessor");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(emptySet()));
  }

  public void canGetResolvedArtifactsForConfiguration(String configurationName) {
    ResolvedConfiguration resolvedConfiguration = spy(ResolvedConfiguration.class);
    Set<ResolvedArtifact> artifacts = prepareArtifactSet(1);
    artifacts.addAll(prepareArtifactSet(1));
    when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifacts);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getName()).thenReturn(configurationName);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    dependencyTask.getConfigurations().add(configuration);
    dependencyTask.updateDependencyArtifacts();

    assertThat(dependencyTask.artifactInfos.size(), is(1));
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedApi() {
    canGetResolvedArtifactsForConfiguration("api");
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedImplementation() {
    canGetResolvedArtifactsForConfiguration("implementation");
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedCompile() {
    canGetResolvedArtifactsForConfiguration("compile");
  }

  @Test
  public void testGetResolvedArtifacts_ResolveException() {
    ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
    when(resolvedConfiguration.getResolvedArtifacts()).thenThrow(ResolveException.class);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(emptySet()));
  }

  @Test
  public void testAddArtifacts() {
    ResolvedConfiguration resolvedConfiguration = spy(ResolvedConfiguration.class);
    Set<ResolvedArtifact> artifacts = prepareArtifactSet(3);
    when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifacts);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    dependencyTask.getConfigurations().add(configuration);
    dependencyTask.updateDependencyArtifacts();
    assertThat(dependencyTask.artifactInfos.size(), is(3));
  }

  @Test
  public void testAddArtifacts_willNotAddDuplicate() {
    ResolvedConfiguration resolvedConfiguration = spy(ResolvedConfiguration.class);
    Set<ResolvedArtifact> artifacts = prepareArtifactSet(1);
    artifacts.addAll(prepareArtifactSet(1));
    when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifacts);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    dependencyTask.getConfigurations().add(configuration);
    dependencyTask.updateDependencyArtifacts();

    assertThat(dependencyTask.artifactInfos.size(), is(1));
  }

  @Test
  public void testCanBeResolved_isTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);

    assertTrue(dependencyTask.canBeResolved(configuration));
  }

  @Test
  public void testCanBeResolved_isFalse() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(false);

    assertFalse(dependencyTask.canBeResolved(configuration));
  }

  @Test
  public void testIsTest_isNotTest() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("random");

    assertFalse(dependencyTask.isTest(configuration));
  }

  @Test
  public void testIsTest_isTestCompile() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("testCompile");

    assertTrue(dependencyTask.isTest(configuration));
  }

  @Test
  public void testIsTest_isAndroidTestCompile() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("androidTestCompile");

    assertTrue(dependencyTask.isTest(configuration));
  }

  @Test
  public void testIsTest_fromHierarchy() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("random");

    Configuration parent = mock(Configuration.class);
    when(parent.getName()).thenReturn("testCompile");

    Set<Configuration> hierarchy = new HashSet<>();
    hierarchy.add(parent);

    when(configuration.getHierarchy()).thenReturn(hierarchy);
    assertTrue(dependencyTask.isTest(configuration));
  }

  private DependencyHandler prepareDependencyHandler() {
    DependencyHandler mockHandler = mock(DependencyHandler.class);
    ArtifactResolutionQuery mockQuery = mock(ArtifactResolutionQuery.class);
    when(mockHandler.createArtifactResolutionQuery()).thenReturn(mockQuery);

    when(mockQuery.forModule(anyString(), anyString(), anyString())).thenAnswer(this::mockQueryResult);
    return mockHandler;
  }

  private ArtifactResolutionQuery mockQueryResult(InvocationOnMock invocation) {
    String name = invocation.getArgument(1);
    String version = invocation.getArgument(2);
    ArtifactResolutionQuery mockQuery = mock(ArtifactResolutionQuery.class);
    ArtifactResolutionResult mockQueryResult = mock(ArtifactResolutionResult.class);
    ComponentArtifactsResult mockArtifactResult = mock(ComponentArtifactsResult.class);
    ResolvedArtifactResult artifactResult = mock(ResolvedArtifactResult.class);

    when(mockQuery.withArtifacts(MavenModule.class, MavenPomArtifact.class)).thenReturn(mockQuery);
    when(mockQuery.execute()).thenReturn(mockQueryResult);
    when(mockQueryResult.getResolvedComponents()).thenReturn(ImmutableSet.of(mockArtifactResult));
    when(mockArtifactResult.getArtifacts(MavenPomArtifact.class)).thenReturn(ImmutableSet.of(artifactResult));
    when(artifactResult.getFile()).thenReturn(new File(name + "-" + version + ".pom"));
    return mockQuery;
  }

  private Set<ResolvedArtifact> prepareArtifactSet(int count) {
    Set<ResolvedArtifact> artifacts = new HashSet<>();
    String namePrefix = "artifact";
    String groupPrefix = "group";
    String locationPrefix = "location";
    String versionPostfix = ".0";
    for (int i = 0; i < count; i++) {
      String index = String.valueOf(i);
      artifacts.add(
          prepareArtifact(
              namePrefix + index,
              groupPrefix + index,
              locationPrefix + index,
              index + versionPostfix));
    }
    return artifacts;
  }

  private ResolvedArtifact prepareArtifact(
      String name, String group, String filePath, String version) {
    ModuleVersionIdentifier moduleId = mock(ModuleVersionIdentifier.class);
    when(moduleId.getGroup()).thenReturn(group);
    when(moduleId.getVersion()).thenReturn(version);

    ResolvedModuleVersion moduleVersion = mock(ResolvedModuleVersion.class);
    when(moduleVersion.getId()).thenReturn(moduleId);

    File artifactFile = mock(File.class);
    when(artifactFile.getAbsolutePath()).thenReturn(filePath);

    ResolvedArtifact artifact = mock(ResolvedArtifact.class);
    when(artifact.getName()).thenReturn(name);
    when(artifact.getFile()).thenReturn(artifactFile);
    when(artifact.getModuleVersion()).thenReturn(moduleVersion);

    return artifact;
  }
}
