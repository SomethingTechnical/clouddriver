/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kato.services

import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.model.aws.SubnetAnalyzer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class RegionScopedProviderFactory {

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  ThrottleService throttleService

  @Value('${discovery.host.format:#{null}}')
  String discoveryHostFormat

  RegionScopedProvider forRegion(AmazonCredentials amazonCredentials, String region) {
    new RegionScopedProvider(amazonCredentials, region)
  }

  class RegionScopedProvider {

    final AmazonCredentials amazonCredentials
    final String region

    RegionScopedProvider(AmazonCredentials amazonCredentials, String region) {
      this.amazonCredentials = amazonCredentials
      this.region = region
    }

    private AmazonEC2 getAmazonEC2() {
      amazonClientProvider.getAmazonEC2(amazonCredentials, region)
    }

    SubnetAnalyzer getSubnetAnalyzer() {
      SubnetAnalyzer.from(amazonEC2.describeSubnets().subnets)
    }

    SecurityGroupService getSecurityGroupService() {
      new SecurityGroupService(amazonEC2, subnetAnalyzer)
    }

    NetworkInterfaceService getNetworkInterfaceService() {
      new NetworkInterfaceService(securityGroupService, subnetAnalyzer, amazonEC2)
    }

    AsgService getAsgService() {
      new AsgService(throttleService, amazonClientProvider.getAutoScaling(amazonCredentials, region))
    }

    ElbService getElbService() {
      new ElbService(throttleService, amazonClientProvider.getAmazonElasticLoadBalancing(amazonCredentials, region))
    }

    EurekaService getEurekaService(Task task, String phase) {
      new EurekaService(throttleService, discoveryHostFormat, new RestTemplate(), task, phase, amazonCredentials.environment, region)
    }
  }

}