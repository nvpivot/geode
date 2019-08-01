/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.management.internal.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.apache.geode.cache.configuration.RegionConfig;
import org.apache.geode.cache.configuration.RegionType;
import org.apache.geode.management.api.ClusterManagementListResult;
import org.apache.geode.management.api.ClusterManagementService;
import org.apache.geode.management.client.ClusterManagementServiceBuilder;
import org.apache.geode.management.runtime.RuntimeInfo;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.rules.GfshCommandRule;

public class ListIndexManagementDUnitTest {
  private static MemberVM locator, server;

  private RegionConfig regionConfig;

  @ClassRule
  public static ClusterStartupRule lsRule = new ClusterStartupRule();

  @ClassRule
  public static GfshCommandRule gfsh = new GfshCommandRule();

  private static ClusterManagementService cms;

  @BeforeClass
  public static void beforeclass() throws Exception {
    locator = lsRule.startLocatorVM(0, l -> l.withHttpService());
    server = lsRule.startServerVM(1, locator.getPort());

    cms = ClusterManagementServiceBuilder.buildWithHostAddress()
        .setHostAddress("localhost", locator.getHttpPort())
        .build();

    RegionConfig config = new RegionConfig();
    config.setName("region1");
    config.setType(RegionType.REPLICATE);
    cms.create(config);
    locator.waitUntilRegionIsReadyOnExactlyThisManyServers("/region1", 1);

    gfsh.connectAndVerify(locator);
    gfsh.executeAndAssertThat(
        "create index --name=index1 --type=key --expression=id --region=/region1")
        .statusIsSuccess();
    gfsh.executeAndAssertThat(
        "create index --name=index2 --type=key --expression=key --region=/region1")
        .statusIsSuccess();
  }

  @Before
  public void before() {
    regionConfig = new RegionConfig();
  }

  @Test
  public void listRegion() {
    List<RegionConfig> result =
        cms.list(new RegionConfig()).getConfigResult();
    assertThat(result).hasSize(1);
  }

  @Test
  public void getRegion() {
    regionConfig.setName("region1");
    List<RegionConfig> regions = cms.get(regionConfig).getConfigResult();
    assertThat(regions).hasSize(1);
    RegionConfig region = regions.get(0);
    List<RegionConfig.Index> indexes = region.getIndexes();
    assertThat(indexes).hasSize(2);
  }

  @Test
  public void getNonExistRegion() {
    regionConfig.setName("notExist");
    assertThatThrownBy(() -> cms.get(regionConfig)).hasMessageContaining("ENTITY_NOT_FOUND");
  }

  @Test
  public void listIndexForOneRegion() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    ClusterManagementListResult<RegionConfig.Index, RuntimeInfo> list = cms.list(index);
    List<RegionConfig.Index> result = list.getConfigResult();
    assertThat(result).hasSize(2);
  }

  @Test
  public void getIndex() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    index.setName("index1");
    ClusterManagementListResult<RegionConfig.Index, RuntimeInfo> list = cms.get(index);
    List<RegionConfig.Index> result = list.getConfigResult();
    assertThat(result).hasSize(1);
    RegionConfig.Index runtimeIndex = result.get(0);
    assertThat(runtimeIndex.getRegionName()).isEqualTo("region1");
    assertThat(runtimeIndex.getName()).isEqualTo("index1");
    assertThat(runtimeIndex.getFromClause()).isEqualTo("/region1");
    assertThat(runtimeIndex.getExpression()).isEqualTo("id");
  }

  @Test
  public void getIndexWithoutIndexId() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    assertThatThrownBy(() -> cms.get(index)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unable to construct the uri ");
  }

  @Test
  public void getIndexWithoutRegionName() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setName("index1");
    assertThatThrownBy(() -> cms.get(index)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unable to construct the uri ");
  }

  @Test
  public void listIndexesWithIdFilter() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    index.setName("index1");
    ClusterManagementListResult<RegionConfig.Index, RuntimeInfo> list = cms.list(index);
    List<RegionConfig.Index> result = list.getConfigResult();
    assertThat(result).hasSize(1);
    RegionConfig.Index runtimeIndex = result.get(0);
    assertThat(runtimeIndex.getRegionName()).isEqualTo("region1");
    assertThat(runtimeIndex.getName()).isEqualTo("index1");
    assertThat(runtimeIndex.getFromClause()).isEqualTo("/region1");
    assertThat(runtimeIndex.getExpression()).isEqualTo("id");
  }

  @Test
  public void getNonExistingIndex() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    index.setName("index333");
    assertThatThrownBy(() -> cms.get(index)).hasMessageContaining("ENTITY_NOT_FOUND");
  }

  @Test
  public void listNonExistingIndexesWithIdFilter() {
    RegionConfig.Index index = new RegionConfig.Index();
    index.setRegionName("region1");
    index.setName("index333");
    ClusterManagementListResult<RegionConfig.Index, RuntimeInfo> list = cms.list(index);
    List<RegionConfig.Index> result = list.getConfigResult();
    assertThat(result).hasSize(0);
    assertThat(list.isSuccessful()).isTrue();
  }
}
