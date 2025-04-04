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
package org.apache.knox.gateway.filter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.knox.gateway.RemoteAuthMessages;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.logging.log4j.ThreadContext;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RemoteAuthFilter implements Filter {

  static final String REMOTE_AUTH = "remote.auth.";
  static final String CONFIG_REMOTE_AUTH_URL = REMOTE_AUTH + "url";
  static final String CONFIG_INCLUDE_HEADERS = REMOTE_AUTH + "include.headers";
  static final String CONFIG_CACHE_KEY_HEADER = REMOTE_AUTH + "cache.key";
  static final String CONFIG_EXPIRE_AFTER = REMOTE_AUTH + "expire.after";
  static final String DEFAULT_CACHE_KEY_HEADER = "Authorization";
  static final String CONFIG_USER_HEADER = REMOTE_AUTH + "user.header";
  static final String CONFIG_GROUP_HEADER = REMOTE_AUTH + "group.header";
  static final String DEFAULT_CONFIG_USER_HEADER = "X-Knox-Actor-ID";
  static final String DEFAULT_CONFIG_GROUP_HEADER = "X-Knox-Actor-Groups-*";
  static final String CONFIG_TRUSTSTORE_PATH = REMOTE_AUTH + "truststore.path";
  static final String CONFIG_TRUSTSTORE_PASSWORD = REMOTE_AUTH + "truststore.password";
  static final String CONFIG_TRUSTSTORE_TYPE = REMOTE_AUTH + "truststore.type";
  static final String DEFAULT_TRUSTSTORE_TYPE = "JKS";
  static final String WILDCARD = "*";
  static final String TRACE_ID = "trace_id";
  static final String REQUEST_ID_HEADER_NAME = "X-Request-Id";
  static final String TRUSTSTORE_CONFIGURATION_CANNOT_BE_RESOLVED_INTO_A_VALID_TRUSTSTORE = "Truststore configuration cannot be resolved into a valid truststore";

  private String remoteAuthUrl;
  private List<String> includeHeaders;
  private String cacheKeyHeader;
  private String userHeader;
  private List<String> groupHeaders;
  /*
  For Testing
   */
  HttpURLConnection httpURLConnection;

  private Cache<String, Subject> authenticationCache;

  private static final AuditService auditService = AuditServiceFactory.getAuditService();
  private static final Auditor auditor = auditService.getAuditor(
          AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );
  private final RemoteAuthMessages LOGGER = MessagesFactory.get( RemoteAuthMessages.class );

  private KeyStore trustStore;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    remoteAuthUrl = filterConfig.getInitParameter(CONFIG_REMOTE_AUTH_URL);
    if (remoteAuthUrl == null || remoteAuthUrl.isEmpty()) {
      LOGGER.missingRequiredParameter(CONFIG_REMOTE_AUTH_URL);
      throw new ServletException(CONFIG_REMOTE_AUTH_URL + " is a missing required param.");
    }
    includeHeaders = Arrays.asList(filterConfig.getInitParameter(CONFIG_INCLUDE_HEADERS).split(","));
    cacheKeyHeader = filterConfig.getInitParameter(CONFIG_CACHE_KEY_HEADER) != null ? filterConfig
            .getInitParameter(CONFIG_CACHE_KEY_HEADER) : DEFAULT_CACHE_KEY_HEADER;
    String cacheTime = filterConfig.getInitParameter(CONFIG_EXPIRE_AFTER);
    if (cacheTime != null) {
      int expireAfterMinutes = Integer.parseInt(cacheTime);
      authenticationCache = CacheBuilder.newBuilder()
              .expireAfterWrite(expireAfterMinutes, TimeUnit.MINUTES)
              .build();
    }

    userHeader = filterConfig.getInitParameter(CONFIG_USER_HEADER);
    if (userHeader == null || userHeader.isEmpty()) {
      userHeader = DEFAULT_CONFIG_USER_HEADER;
    }

    String groupHeaderParam = filterConfig.getInitParameter(CONFIG_GROUP_HEADER);
    if (groupHeaderParam == null || groupHeaderParam.isEmpty()) {
      groupHeaders = Arrays.asList(DEFAULT_CONFIG_GROUP_HEADER);
    } else {
      groupHeaders = Arrays.asList(groupHeaderParam.split("\\s*,\\s*"));
    }

    buildTrustStore(filterConfig);
  }

  private void buildTrustStore(FilterConfig filterConfig) throws ServletException {
    String truststorePath = filterConfig.getInitParameter(CONFIG_TRUSTSTORE_PATH);
    String truststorePassword = filterConfig.getInitParameter(CONFIG_TRUSTSTORE_PASSWORD);
    String truststoreType = filterConfig.getInitParameter(CONFIG_TRUSTSTORE_TYPE);
    if (truststoreType == null || truststoreType.isEmpty()) {
      truststoreType = DEFAULT_TRUSTSTORE_TYPE;
    }

    ServletContext context = filterConfig.getServletContext();
    if (context != null) {
      String topologyName = (String) context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (services != null) {
        try {
          final AliasService aliasService =  services.getService(ServiceType.ALIAS_SERVICE);
          if (truststorePath != null && !truststorePath.isEmpty()) {
            if (truststorePassword == null || truststorePassword.isEmpty()) {
              // let's check for an alias given the intent to specify a truststore path
              char[] passChars = aliasService.getPasswordFromAliasForCluster(topologyName,
                      CONFIG_TRUSTSTORE_PASSWORD, false);
              if (passChars != null) {
                truststorePassword = new String(passChars);
              }
              if (truststorePassword == null || truststorePassword.isEmpty()) {
                truststorePassword = new String(aliasService.getPasswordFromAliasForGateway(CONFIG_TRUSTSTORE_PASSWORD));
              }
            }
          }
          KeystoreService keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
          trustStore = getTrustStore(truststorePath, truststoreType, truststorePassword, keystoreService);
        } catch (AliasServiceException | IOException e) {
          throw new ServletException("Error while initializing RemoteAuthProvider", e);
        }
      }
    }
    if (trustStore == null) {
      // truststore details were explicitly configured but there is no servlet context available for gateway services
      throw new ServletException(TRUSTSTORE_CONFIGURATION_CANNOT_BE_RESOLVED_INTO_A_VALID_TRUSTSTORE);
    }
  }

  private KeyStore getTrustStore(String truststorePath, String truststoreType, String truststorePassword,
                                 KeystoreService keystoreService) throws IOException {
    KeyStore truststore = null;
    try {
      // Try topology-specific truststore first if configured
      if (truststorePath != null && !truststorePath.isEmpty()) {
        truststore = keystoreService.loadTruststore(truststorePath, truststoreType, truststorePassword);
        if (truststore == null) {
          // truststore details were explicitly configured but there is no truststore realized by that config
          throw new IOException(TRUSTSTORE_CONFIGURATION_CANNOT_BE_RESOLVED_INTO_A_VALID_TRUSTSTORE);
        }
      }
      // Fall back to gateway-level truststore
      if (truststore == null) {
        truststore = keystoreService.getTruststoreForHttpClient();
        if (truststore == null) {
          truststore = keystoreService.getKeystoreForGateway();
        }
      }
    } catch (KeystoreServiceException e) {
      LOGGER.failedToLoadTruststore(e.getMessage(), e);
      throw new IOException("Failed to load truststore: ", e);
    }
    return truststore;
  }

  public SSLSocketFactory createSSLSocketFactory(KeyStore trustStore) throws Exception {
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), null);

    return sslContext.getSocketFactory();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String cacheKey = httpRequest.getHeader(cacheKeyHeader);
    Subject cachedSubject = authenticationCache.getIfPresent(hashCacheKey(cacheKey));

    if (cachedSubject != null) {
      continueWithEstablishedSecurityContext(cachedSubject, httpRequest, httpResponse, filterChain);
      return;
    }

    try {
      HttpURLConnection connection = getHttpURLConnection();
      for (String header : includeHeaders) {
        String headerValue = httpRequest.getHeader(header);
        if (headerValue != null) {
          connection.addRequestProperty(header, headerValue);
        }
      }

      // Add trace ID to the outgoing request if it exists to correlate logs
      String traceId = ThreadContext.get(TRACE_ID);
      if (traceId != null) {
        connection.addRequestProperty(REQUEST_ID_HEADER_NAME, ThreadContext.get(TRACE_ID));
      }

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        String principalName = connection.getHeaderField(userHeader);
        Subject subject = new Subject();
        subject.getPrincipals().add(new PrimaryPrincipal(principalName));

        addGroupPrincipals(subject, connection);

        authenticationCache.put(hashCacheKey(cacheKey), subject);

        AuditContext context = auditService.getContext();
        if (context != null) {
          context.setUsername( principalName );
          auditService.attachContext(context);
          String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
          auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI,
                  ActionOutcome.SUCCESS, "Groups: " + Arrays.toString(subject.getPrincipals(GroupPrincipal.class)
                          .stream()
                          .map(GroupPrincipal::getName)
                          .toArray(String[]::new)));
        }

        continueWithEstablishedSecurityContext(subject, httpRequest, httpResponse, filterChain);
      } else {
        LOGGER.failedToAuthenticateToRemoteAuthServer();
        httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
      }
    } catch (Exception e) {
      LOGGER.errorReceivedWhileAuthenticatingRequest(e);
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing authentication request");
    }
  }

  private HttpURLConnection getHttpURLConnection() throws IOException {
    HttpURLConnection connection;
    if (httpURLConnection == null) {
      URL url = new URL(remoteAuthUrl);
      connection = (HttpURLConnection) url.openConnection();
      if (trustStore != null) {
        try {
          ((HttpsURLConnection) connection).setSSLSocketFactory(createSSLSocketFactory(trustStore));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    } else {
      connection = httpURLConnection;
    }
    return connection;
  }

  private void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request,
                                                      final HttpServletResponse response, final FilterChain chain)
          throws IOException, ServletException {
    try {
      Subject.doAs(
              subject,
              new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                  chain.doFilter(request, response);
                  return null;
                }
              }
      );
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
  }

  private void addGroupPrincipals(Subject subject, HttpURLConnection connection) {
    for (String headerPattern : groupHeaders) {
      if (headerPattern.endsWith(WILDCARD)) {
        // Handle wildcard pattern
        String prefix = headerPattern.substring(0, headerPattern.length() - 1);
        connection.getHeaderFields().forEach((key, value) -> {
          if (key != null && key.startsWith(prefix)) {
            addGroupsFromHeaderValue(subject, value);
          }
        });
      } else {
        // Handle exact header match
        String groupNames = connection.getHeaderField(headerPattern);
        if (groupNames != null && !groupNames.isEmpty()) {
          addGroupsFromHeaderValue(subject, Arrays.asList(groupNames));
        }
      }
    }
  }

  private void addGroupsFromHeaderValue(Subject subject, List<String> headerValues) {
    headerValues.forEach(headerValue -> {
      if (headerValue != null && !headerValue.isEmpty()) {
        Arrays.stream(headerValue.split(","))
              .map(String::trim)
              .filter(group -> !group.isEmpty())
              .forEach(groupName -> subject.getPrincipals().add(new GroupPrincipal(groupName)));
      }
    });
  }

  @Override
  public void destroy() {
  }

  // Add method to hash cache key
  private String hashCacheKey(String key) {
    return String.valueOf(key.hashCode());
  }

  // Change to package-private for testing
  void setCachedSubject(String cacheKey, Subject subject) {
    authenticationCache.put(hashCacheKey(cacheKey), subject);
  }
}
