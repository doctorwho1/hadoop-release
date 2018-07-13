/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 * See License.txt in the project root for license information.
 */

package org.apache.hadoop.fs.azurebfs.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Hashtable;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.fs.azurebfs.services.ExponentialRetryPolicy;

/**
 * This class provides convenience methods to obtain AAD tokens. While convenient, it is not necessary to
 * use these methods to obtain the tokens. Customers can use any other method (e.g., using the adal4j client)
 * to obtain tokens.
 */

@InterfaceAudience.Private
@InterfaceStability.Evolving
public class AzureADAuthenticator {

  private static final Logger LOG = LoggerFactory.getLogger(AzureADAuthenticator.class);
  static private final String RESOURCE_NAME = "https://storage.azure.com/";
  private static final int CONNECT_TIMEOUT = 30 * 1000;
  private static final int READ_TIMEOUT = 30 * 1000;

  /**
   * gets Azure Active Directory token using the user ID and password of a service principal (that is, Web App
   * in Azure Active Directory).
   * <p>
   * Azure Active Directory allows users to set up a web app as a service principal. Users can optionally
   * obtain service principal keys from AAD. This method gets a token using a service principal's client ID
   * and keys. In addition, it needs the token endpoint associated with the user's directory.
   * </P>
   *
   * @param authEndpoint the OAuth 2.0 token endpoint associated with the user's directory
   *                     (obtain from Active Directory configuration)
   * @param clientId     the client ID (GUID) of the client web app obtained from Azure Active Directory configuration
   * @param clientSecret the secret key of the client web app
   * @return {@link AzureADToken} obtained using the creds
   * @throws IOException throws IOException if there is a failure in connecting to Azure AD
   */
  public static AzureADToken getTokenUsingClientCreds(String authEndpoint, String clientId, String clientSecret)
          throws IOException {
    QueryParams qp = new QueryParams();

    qp.add("resource", RESOURCE_NAME);
    qp.add("grant_type", "client_credentials");
    qp.add("client_id", clientId);
    qp.add("client_secret", clientSecret);
    LOG.debug("AADToken: starting to fetch token using client creds for client ID " + clientId);

    return getTokenCall(authEndpoint, qp.serialize(), null, null);
  }

  /**
   * Gets AAD token from the local virtual machine's VM extension. This only works on an Azure VM with MSI extension
   * enabled.
   *
   * @param tenantGuid  (optional) The guid of the AAD tenant. Can be {@code null}.
   * @param clientId    (optional) The clientId guid of the MSI service principal to use. Can be {@code null}.
   * @param bypassCache {@code boolean} specifying whether a cached token is acceptable or a fresh token
   *                    request should me made to AAD
   * @return {@link AzureADToken} obtained using the creds
   * @throws IOException throws IOException if there is a failure in obtaining the token
   */
  public static AzureADToken getTokenFromMsi(String tenantGuid, String clientId, boolean bypassCache) throws IOException {
    String authEndpoint = "http://169.254.169.254/metadata/identity/oauth2/token";

    QueryParams qp = new QueryParams();
    qp.add("api-version", "2018-02-01");
    qp.add("resource", RESOURCE_NAME);


    if (tenantGuid != null && tenantGuid.length() > 0) {
      String authority = "https://login.microsoftonline.com/" + tenantGuid;
      qp.add("authority", authority);
    }

    if (clientId != null && clientId.length() > 0) {
      qp.add("client_id", clientId);
    }

    if (bypassCache) {
      qp.add("bypass_cache", "true");
    }

    Hashtable<String, String> headers = new Hashtable<String, String>();
    headers.put("Metadata", "true");

    LOG.debug("AADToken: starting to fetch token using MSI");
    return getTokenCall(authEndpoint, qp.serialize(), headers, "GET");
  }

  private static class HttpException extends IOException {
    private int httpErrorCode;
    private String requestId;

    public int getHttpErrorCode() {
      return this.httpErrorCode;
    }

    public String getRequestId() {
      return this.requestId;
    }

    public HttpException(int httpErrorCode, String requestId, String message) {
      super(message);
      this.httpErrorCode = httpErrorCode;
      this.requestId = requestId;
    }
  }

  private static AzureADToken getTokenCall(String authEndpoint, String body, Hashtable<String, String> headers, String httpMethod)
          throws IOException {
    AzureADToken token = null;
    ExponentialRetryPolicy retryPolicy = new ExponentialRetryPolicy(3, 0, 1000, 2);

    int httperror = 0;
    String requestId;
    String httpExceptionMessage = null;
    IOException ex = null;
    boolean succeeded = false;
    int retryCount = 0;
    do {
      httperror = 0;
      requestId = "";
      ex = null;
      try {
        token = getTokenSingleCall(authEndpoint, body, headers, httpMethod);
      } catch (HttpException e) {
        httperror = e.httpErrorCode;
        requestId = e.requestId;
        httpExceptionMessage = e.getMessage();
      } catch (IOException e) {
        ex = e;
      }
      succeeded = ((httperror == 0) && (ex == null));
      retryCount++;
    } while (!succeeded && retryPolicy.shouldRetry(retryCount, httperror));
    if (!succeeded) {
      if (ex != null) {
        throw ex;
      }
      if (httperror != 0) {
        throw new IOException(httpExceptionMessage);
      }
    }
    return token;
  }

  private static AzureADToken getTokenSingleCall(String authEndpoint, String payload, Hashtable<String, String> headers, String httpMethod)
          throws IOException {

    AzureADToken token = null;
    HttpURLConnection conn = null;
    String urlString = authEndpoint;

    httpMethod = (httpMethod == null) ? "POST" : httpMethod;
    if (httpMethod.equals("GET")) {
      urlString = urlString + "?" + payload;
    }

    try {
      URL url = new URL(urlString);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod(httpMethod);
      conn.setReadTimeout(READ_TIMEOUT);
      conn.setConnectTimeout(CONNECT_TIMEOUT);

      if (headers != null && headers.size() > 0) {
        for (String name : headers.keySet()) {
          conn.setRequestProperty(name, headers.get(name));
        }
      }
      conn.setRequestProperty("Connection", "close");

      if (httpMethod.equals("POST")) {
        conn.setDoOutput(true);
        conn.getOutputStream().write(payload.getBytes("UTF-8"));
      }

      int httpResponseCode = conn.getResponseCode();
      String requestId = conn.getHeaderField("x-ms-request-id");
      String responseContentType = conn.getHeaderField("Content-Type");
      long responseContentLength = conn.getHeaderFieldLong("Content-Length", 0);

      requestId = requestId == null ? "" : requestId;
      if (httpResponseCode == HttpURLConnection.HTTP_OK  && responseContentType.startsWith("application/json") && responseContentLength > 0) {
        InputStream httpResponseStream = conn.getInputStream();
        token = parseTokenFromStream(httpResponseStream);
      } else {
        String responseBody = consumeInputStream(conn.getInputStream(), 1024);
        String proxies = "none";
        String httpProxy = System.getProperty("http.proxy");
        String httpsProxy = System.getProperty("https.proxy");
        if (httpProxy != null || httpsProxy != null) {
          proxies = "http:" + httpProxy + ";https:" + httpsProxy;
        }
        String logMessage =
                "AADToken: HTTP connection failed for getting token from AzureAD. Http response: "
                        + httpResponseCode + " " + conn.getResponseMessage()
                        + " Content-Type: " + responseContentType
                        + " Content-Length: " + responseContentLength
                        + " Request ID: " + requestId.toString()
                        + " Proxies: " + proxies
                        + " First 1K of Body: " + responseBody;
        LOG.debug(logMessage);
        throw new HttpException(httpResponseCode, requestId, logMessage);
      }
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
    return token;
  }

  private static AzureADToken parseTokenFromStream(InputStream httpResponseStream) throws IOException {
    AzureADToken token = new AzureADToken();
    try {
      int expiryPeriod = 0;

      JsonFactory jf = new JsonFactory();
      JsonParser jp = jf.createParser(httpResponseStream);
      String fieldName, fieldValue;
      jp.nextToken();
      while (jp.hasCurrentToken()) {
        if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
          fieldName = jp.getCurrentName();
          jp.nextToken();  // field value
          fieldValue = jp.getText();

          if (fieldName.equals("access_token")) {
            token.setAccessToken(fieldValue);
          }
          if (fieldName.equals("expires_in")) {
            expiryPeriod = Integer.parseInt(fieldValue);
          }
        }
        jp.nextToken();
      }
      jp.close();
      long expiry = System.currentTimeMillis();
      expiry = expiry + expiryPeriod * 1000L; // convert expiryPeriod to milliseconds and add
      token.setExpiry(new Date(expiry));
      LOG.debug("AADToken: fetched token with expiry " + token.getExpiry().toString());
    } catch (Exception ex) {
      LOG.debug("AADToken: got exception when parsing json token " + ex.toString());
      throw ex;
    } finally {
      httpResponseStream.close();
    }
    return token;
  }

  private static String consumeInputStream(InputStream inStream, int length) throws IOException {
    byte[] b = new byte[length];
    int totalBytesRead = 0;
    int bytesRead = 0;

    do {
      bytesRead = inStream.read(b, totalBytesRead, length - totalBytesRead);
      if (bytesRead > 0) {
        totalBytesRead += bytesRead;
      }
    } while (bytesRead >= 0 && totalBytesRead < length);

    return new String(b, 0, totalBytesRead);
  }
}


