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
package org.apache.knox.gateway.service.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.impl.GatewayStatusService;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.ProtectionDomain;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;


@Path(PingResource.RESOURCE_PATH)
public class PingResource {
  static final String VERSION_TAG = "v1";
  static final String RESOURCE_PATH = "/" + VERSION_TAG;
  public static final String OK = "OK";
  public static final String PENDING = "PENDING";
  private static HealthServiceMessages log = MessagesFactory.get(HealthServiceMessages.class);
  private static final String CONTENT_TYPE = "text/plain";
  private static final String CACHE_CONTROL = "Cache-Control";
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";

  @Context
  HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @GET
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  @Path("ping")
  public Response doGet() {
    return getPingResponse();
  }

  @POST
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  @Path("ping")
  public Response doPost() {
    return getPingResponse();
  }

  private Response getPingResponse() {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    try (PrintWriter writer = response.getWriter()) {
      writer.println(getPingContent());
    } catch (IOException ioe) {
      log.logException("ping", ioe);
      return Response.serverError().entity(String.format(Locale.ROOT, "Failed to reply correctly due to : %s ", ioe)).build();
    }
    return Response.ok().build();
  }

  String getPingContent() {
    return OK;
  }

  /**
   * FIPS diagnostic: reports whether bcprov/bcpkix classes are on the running
   * gateway's classpath and whether OpenSAML's NamedCurveRegistry got populated
   * at bootstrap. Uses reflection so this module does not require compile-time
   * deps on OpenSAML or BouncyCastle. GET /api/v1/bcprov-probe from any
   * topology that binds the HEALTH service.
   */
  private static final ObjectMapper BCPROV_PROBE_MAPPER = new ObjectMapper();

  @GET
  @Produces(APPLICATION_JSON)
  @Path("bcprov-probe")
  public Response bcprovProbe() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("bcprov", probeClass("org.bouncycastle.jce.ECNamedCurveTable"));
    report.put("bcpkix", probeClass("org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder"));
    report.put("openSaml", probeOpenSamlNamedCurveRegistry());
    try {
      return Response.ok(BCPROV_PROBE_MAPPER.writeValueAsString(report)).build();
    } catch (JsonProcessingException e) {
      return Response.serverError().entity("Failed to serialize probe: " + e.getMessage()).build();
    }
  }

  private Map<String, Object> probeClass(String fqn) {
    Map<String, Object> info = new LinkedHashMap<>();
    try {
      Class<?> c = Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
      info.put("class", fqn);
      info.put("onClasspath", true);
      info.put("source", codeSourceOf(c));
    } catch (ClassNotFoundException e) {
      info.put("class", fqn);
      info.put("onClasspath", false);
    } catch (Throwable t) {
      info.put("class", fqn);
      info.put("onClasspath", false);
      info.put("error", t.getClass().getName() + ": " + t.getMessage());
    }
    return info;
  }

  private Map<String, Object> probeOpenSamlNamedCurveRegistry() {
    Map<String, Object> info = new LinkedHashMap<>();
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class<?> ecSupport = Class.forName("org.opensaml.security.crypto.ec.ECSupport", true, cl);
      info.put("ecSupport", codeSourceOf(ecSupport));

      // Force OpenSAML bootstrap in THIS classloader so the NamedCurveRegistry
      // is populated. Without this, the registry may be empty simply because
      // no other code path has triggered init in the health service's webapp
      // classloader — masking the classpath question.
      try {
        Class<?> initSvc = Class.forName("org.opensaml.core.config.InitializationService", true, cl);
        initSvc.getMethod("initialize").invoke(null);
        info.put("bootstrap", "ok");
      } catch (Throwable t) {
        info.put("bootstrap", "failed: " + t.getClass().getName() + ": " + rootMessage(t));
      }

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
      kpg.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair kp = kpg.generateKeyPair();
      ECPublicKey ec = (ECPublicKey) kp.getPublic();

      Method getNamedCurve = ecSupport.getMethod("getNamedCurve", ECPublicKey.class);
      Object curve = getNamedCurve.invoke(null, ec);
      if (curve == null) {
        info.put("namedCurveRegistryPopulated", false);
        info.put("interpretation",
            "ECSupport.getNamedCurve(P-256) returned null even after forced "
                + "bootstrap. Either bcprov is missing (bootstrap error above), "
                + "or the NamedCurve SPI was stripped (FIPS shim active).");
      } else {
        info.put("namedCurveRegistryPopulated", true);
        Method getName = curve.getClass().getMethod("getName");
        info.put("resolvedNameForP256", getName.invoke(curve));
      }
    } catch (ClassNotFoundException e) {
      info.put("error", "OpenSAML security-api not on classpath: " + e.getMessage());
    } catch (Throwable t) {
      info.put("error", t.getClass().getName() + ": " + rootMessage(t));
    }
    return info;
  }

  private static String rootMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    return cur.getClass().getName() + ": " + cur.getMessage();
  }

  private static String codeSourceOf(Class<?> c) {
    ProtectionDomain pd = c.getProtectionDomain();
    if (pd == null) {
      return null;
    }
    CodeSource cs = pd.getCodeSource();
    if (cs == null || cs.getLocation() == null) {
      return null;
    }
    return cs.getLocation().toString();
  }

  @GET
  @Produces({TEXT_PLAIN})
  @Path("gateway-status")
  public Response status() {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    GatewayServices services = (GatewayServices) request.getServletContext()
            .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    GatewayStatusService statusService = services.getService(ServiceType.GATEWAY_STATUS_SERVICE);
    try (PrintWriter writer = response.getWriter()) {
      writer.println(statusService.status() ? OK : PENDING);
    } catch (IOException e) {
      log.logException("status", e);
      return Response.serverError().entity(String.format(Locale.ROOT, "Failed to reply correctly due to : %s ", e)).build();
    }
    return Response.ok().build();
  }

}
