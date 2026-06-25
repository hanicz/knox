/*
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
package org.apache.knox.gateway.pac4j.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.fips.FipsUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.pac4j.Pac4jMessages;
import org.pac4j.config.client.PropertiesConstants;
import org.pac4j.core.client.Client;
import org.pac4j.saml.client.SAML2Client;

public class SAML2ClientConfigurationDecorator implements ClientConfigurationDecorator {

  private static final String SAML2_CLIENT_CLASS_NAME = SAML2Client.class.getSimpleName();
  private static final String CONFIG_NAME_USE_NAME_QUALIFIER = "useNameQualifier";
  private static final String CONFIG_NAME_USE_FORCE_AUTH = "forceAuth";
  private static final String CONFIG_NAME_USE_PASSIVE = "passive";
  private static final String CONFIG_NAME_NAMEID_POLICY_FORMAT = "nameIdPolicyFormat";
  private static Pac4jMessages log = MessagesFactory.get(Pac4jMessages.class);
  public static final String KEYSTORE_TYPE = "saml.keyStoreType";

  private static final List<String> FIPS_SIGNATURE_ALGORITHMS = Arrays.asList(
      "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
      "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
      "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512");
  private static final List<String> FIPS_DIGEST_METHODS = Arrays.asList(
      "http://www.w3.org/2001/04/xmlenc#sha256",
      "http://www.w3.org/2001/04/xmldsig-more#sha384",
      "http://www.w3.org/2001/04/xmlenc#sha512");
  private static final List<String> FIPS_BLACKLISTED_SIGNATURE_ALGORITHMS = Arrays.asList(
      "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
      "http://www.w3.org/2000/09/xmldsig#dsa-sha1",
      "http://www.w3.org/2001/04/xmldsig-more#rsa-md5",
      "http://www.w3.org/2001/04/xmldsig-more#rsa-ripemd160",
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1",
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384",
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512");
  private static final String FIPS_CERTIFICATE_SIGNATURE_ALG = "SHA256withRSA";

  @Override
  public void decorateClients(List<Client> clients, Map<String, String> properties) {
    for (Client client : clients) {
      if (SAML2_CLIENT_CLASS_NAME.equalsIgnoreCase(client.getName())) {
        final SAML2Client saml2Client = (SAML2Client) client;
        setUseNameQualifierFlag(properties, saml2Client);
        setForceAuthFlag(properties, saml2Client);
        setPassiveFlag(properties, saml2Client);
        setNameIdPolicyFormat(properties, saml2Client);
        setKeyStoreType(properties, saml2Client);
        setKeyStorePath(properties, saml2Client);
        applyFipsAlgorithmRestrictions(saml2Client);
      }
    }
  }

  /**
   * When Knox is running with a FIPS JCE provider, restrict the SAML2 client to
   * RSA + SHA-2 signing and SHA-2 digests. EC algorithms are blacklisted because
   * the FIPS JCE provider exposes neither BouncyCastle's EC API nor the
   * non-FIPS curve set that opensaml-security-impl would otherwise register.
   * The non-FIPS SHA-1 / MD5 / RIPEMD160 algorithms are blacklisted regardless.
   */
  private void applyFipsAlgorithmRestrictions(final SAML2Client saml2Client) {
    if (!FipsUtils.isFipsEnabledWithBCProvider()) {
      return;
    }
    saml2Client.getConfiguration().setSignatureAlgorithms(FIPS_SIGNATURE_ALGORITHMS);
    saml2Client.getConfiguration().setSignatureReferenceDigestMethods(FIPS_DIGEST_METHODS);
    saml2Client.getConfiguration().setBlackListedSignatureSigningAlgorithms(FIPS_BLACKLISTED_SIGNATURE_ALGORITHMS);
    saml2Client.getConfiguration().setCertificateSignatureAlg(FIPS_CERTIFICATE_SIGNATURE_ALG);
    log.pac4jSamlFipsAlgorithmsApplied();
  }

  private void setUseNameQualifierFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String useNameQualifier = properties.get(CONFIG_NAME_USE_NAME_QUALIFIER);
    if (StringUtils.isNotBlank(useNameQualifier)) {
      saml2Client.getConfiguration().setUseNameQualifier(Boolean.valueOf(useNameQualifier));
    }
  }

  private void setForceAuthFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String forceAuth = properties.get(CONFIG_NAME_USE_FORCE_AUTH);
    if (StringUtils.isNotBlank(forceAuth)) {
      saml2Client.getConfiguration().setForceAuth(Boolean.valueOf(forceAuth));
    }
  }

  private void setPassiveFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String passive = properties.get(CONFIG_NAME_USE_PASSIVE);
    if (StringUtils.isNotBlank(passive)) {
      saml2Client.getConfiguration().setPassive(Boolean.valueOf(passive));
    }
  }

  private void setNameIdPolicyFormat(Map<String, String> properties, final SAML2Client saml2Client) {
    final String nameIdPolicyFormat = properties.get(CONFIG_NAME_NAMEID_POLICY_FORMAT);
    if (StringUtils.isNotBlank(nameIdPolicyFormat)) {
      saml2Client.getConfiguration().setNameIdPolicyFormat(nameIdPolicyFormat);
    }
  }

  private void setKeyStoreType(Map<String, String> properties, final SAML2Client saml2Client) {
    final String keyStoreType = properties.get(KEYSTORE_TYPE);
    if (StringUtils.isNotBlank(keyStoreType)) {
      saml2Client.getConfiguration().setKeyStoreType(keyStoreType);
      log.pac4jSamlKeystoreType(keyStoreType);
    }
  }

  private void setKeyStorePath(Map<String, String> properties, final SAML2Client saml2Client) {
    final String keyStorePath = properties.get(PropertiesConstants.SAML_KEYSTORE_PATH);
    if (StringUtils.isNotBlank(keyStorePath)) {
      saml2Client.getConfiguration().setKeystorePath(keyStorePath);
      log.pac4jSamlKeystorePath(keyStorePath);
    }
  }
}
