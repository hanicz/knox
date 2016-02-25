/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.config;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public interface GatewayConfig {

  // Used as the basis for any home directory that is not specified.
  static final String GATEWAY_HOME_VAR = "GATEWAY_HOME";

  // Variable name for the location of configuration files edited by users
  static final String GATEWAY_CONF_HOME_VAR = "GATEWAY_CONF_HOME";

  // Variable name for the location of data files generated by the gateway at runtime.
  static final String GATEWAY_DATA_HOME_VAR = "GATEWAY_DATA_HOME";

  public static final String GATEWAY_CONFIG_ATTRIBUTE = "org.apache.hadoop.gateway.config";
  public static final String HADOOP_KERBEROS_SECURED = "gateway.hadoop.kerberos.secured";
  public static final String KRB5_CONFIG = "java.security.krb5.conf";
  public static final String KRB5_DEBUG = "sun.security.krb5.debug";
  public static final String KRB5_LOGIN_CONFIG = "java.security.auth.login.config";
  public static final String KRB5_USE_SUBJECT_CREDS_ONLY = "javax.security.auth.useSubjectCredsOnly";

  /**
   * The location of the gateway configuration.
   * Subdirectories will be: topologies
   * @return The location of the gateway configuration.
   */
  String getGatewayConfDir();

  /**
   * The location of the gateway runtime generated data.
   * Subdirectories will be security, deployments
   * @return The location of the gateway runtime generated data.
   */
  String getGatewayDataDir();

  /**
   * The location of the gateway services definition's root directory
   * @return The location of the gateway services top level directory.
   */
  String getGatewayServicesDir();

  /**
   * The location of the gateway applications's root directory
   * @return The location of the gateway applications top level directory.
   */
  String getGatewayApplicationsDir();

  String getHadoopConfDir();

  String getGatewayHost();

  int getGatewayPort();

  String getGatewayPath();

  String getGatewayTopologyDir();

  String getGatewaySecurityDir();

  String getGatewayDeploymentDir();

  InetSocketAddress getGatewayAddress() throws UnknownHostException;

  boolean isSSLEnabled();
  
  List<String> getExcludedSSLProtocols();

  boolean isHadoopKerberosSecured();

  String getKerberosConfig();

  boolean isKerberosDebugEnabled();

  String getKerberosLoginConfig();

  String getDefaultTopologyName();

  String getDefaultAppRedirectPath();

  String getFrontendUrl();

  boolean isClientAuthNeeded();

  String getTruststorePath();

  boolean getTrustAllCerts();

  String getKeystoreType();

  String getTruststoreType();

  boolean isXForwardedEnabled();

  String getEphemeralDHKeySize();

  int getHttpClientMaxConnections();

  int getThreadPoolMax();

  int getHttpServerRequestBuffer();

  int getHttpServerRequestHeaderBuffer();

  int getHttpServerResponseBuffer();

  int getHttpServerResponseHeaderBuffer();

  int getGatewayDeploymentsBackupVersionLimit();

  long getGatewayDeploymentsBackupAgeLimit();

}
