package ca.bc.gov.tno.jorel2.controller;

import java.io.*;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.json.simple.*;
import org.json.simple.parser.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;

public class ChannelApi {

	// xml or json
	String format;
	
	// complete result returned by the API call
	String result;
	
	// information returned by twitter in the header
	public int twitter_rate_limit = -1;
	public int twitter_rate_remaining = -1;
	public int twitter_rate_reset = -1;

	// for XML
	DocumentBuilder xmlbuilder = null;
	Document xmldoc = null;
	
	// for JSON
	JSONParser jsonparser = null;
	JSONObject jsondoc = null;
	
	// keys - the defaults are for the TNOBC account (for the TNO Jorel application)
	private String consumerKey = "eP1hacPiHiPWRKKwtcw6bIub9";
	private String consumerSecret = "6YqLXXSrzuJa9u4M16c8os6vP7FmRK03MPR3S2mwkwGbn4SN0c"; //<--- DO NOT SHARE THIS VALUE

	// for Oauth - the defaults are for the TNOBC account (for the TNO Jorel application)
	private String oAuthAccessToken = "1489356456-1IkdUbYhws48aKGNnJjDosMZnfv6bREQWdRtO2B";
    private String oAuthAccessTokenSecret = "iEFMBd9YRa6sBKojSbfHcuwGI9dgOz2E9Gera7yvm4ayL"; //<--- DO NOT SHARE THIS VALUE
    private String oAuthSignatureMethod = "HMAC-SHA1";
    private String oAuthVersion = "1.0";

	// for App Auth
    private String aAuthBearerToken;
 
	private boolean debug = false;
	
	public ChannelApi(String resultformat) {
		format = resultformat;
		if (format.equalsIgnoreCase("xml")) {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setValidating(false);
			domFactory.setNamespaceAware(false);
			try {
				xmlbuilder = domFactory.newDocumentBuilder();
			} catch (ParserConfigurationException ex) {
				xmlbuilder = null;
				System.out.println("aktiv.API constructor error: "+ex.toString());
			}
		} else {
			jsonparser=new JSONParser();
		}
	}

	public void call(URI uri) {
		call(uri, false, false);
	}
	public void call(URI uri, boolean oauth) {
		call(uri, oauth, false);
	}
	public void call(URI uri, boolean oauth, boolean appauth) {
		if (format.equalsIgnoreCase("xml")) {
			xmldoc = null;
			if (xmlbuilder==null) return;
			try {
				InputStream is = uri.toURL().openStream();
				xmldoc = xmlbuilder.parse(is);
				is.close();
			} catch (Exception ex) {
				xmldoc = null;
				System.out.println("aktiv.API.call() XML error: "+ex.toString());
			}
		} else {
			jsondoc = null;
			if (jsonparser==null) return;
			try {
			    HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setRequestMethod("GET");
				if (oauth) {
					sign_oauth(connection, uri);
				} else if (appauth) {
					app_auth(connection, uri);
				}
		        connection.connect();
		        
		        // special for twitter
		        twitter_rate_limit = connection.getHeaderFieldInt("x-rate-limit-limit", twitter_rate_limit);
		        twitter_rate_remaining = connection.getHeaderFieldInt("x-rate-limit-remaining", twitter_rate_remaining);
		        twitter_rate_reset = connection.getHeaderFieldInt("x-rate-limit-reset", twitter_rate_reset);
		        
				BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder sb = new StringBuilder();
				int cp;
				while ((cp = rd.read()) != -1) {
					sb.append((char) cp);
				}
				result = sb.toString();
				if (result.startsWith("[")) {
					result = result.substring(1, result.length()-1);
				}
				jsondoc=(JSONObject)jsonparser.parse(result);				
				rd.close();
			} catch (Exception ex) {
				jsondoc = null;
				System.out.println("aktiv.API.call() JSON error: "+ex.toString());
			}
		}
	}
	
	public String get_result() {
		return result;
	}

	private String get_result(String tag) {
		if (format.equalsIgnoreCase("xml")) {
			if (xmldoc==null) return "";
			try {

				NodeList nl = xmldoc.getElementsByTagName(tag);
				if (nl.getLength()>0) {
					Element e = (Element)nl.item(0);
					nl = e.getChildNodes();
					return ((Node)nl.item(0)).getNodeValue().trim();
				}

			} catch (Exception ex) {
				System.out.println("aktiv.API.get_result() XML error: "+ex.toString());
			}
		} else {
			if (jsondoc==null) return "";
			try {

				Object o = jsondoc.get(tag);
				if (o!=null) {
					return o.toString();
				}

			} catch (Exception ex) {
				System.out.println("aktiv.API.get_result() JSON error: "+ex.toString());
			}
		}
		return "";
	}
	
	public JSONObject get_object(String tag) {
		if (jsondoc==null) return null; // json only
		try {
			return (JSONObject)jsondoc.get(tag);
		} catch (Exception ex) {
			System.out.println("aktiv.API.get_array() JSON error: "+ex.toString());
		}
		return null;
	}

	public JSONArray get_array(String tag) {
		if (jsondoc==null) return null; // json only
		try {
			return (JSONArray)jsondoc.get(tag);
		} catch (Exception ex) {
			System.out.println("aktiv.API.get_array() JSON error: "+ex.toString());
		}
		return null;
	}

	public String get_string(String tag) {
		return get_result(tag);
	}

	public long get_long(String tag) {
		String res = get_result(tag);
		try {
			return Long.parseLong(res);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	public float get_float(String tag) {
		String res = get_result(tag);
		try {
			return Float.parseFloat(res);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}
	
	private void sign_oauth(HttpURLConnection connection, URI uri) {
		try {
		    String method = "GET";
		    String url = uri.getScheme()+"://"+uri.getHost()+uri.getPath();
		    
			long millis = System.currentTimeMillis();
			long time = millis / 1000;
		    String oAuthNonce = String.valueOf(System.currentTimeMillis());
		    String oAuthTimestamp = String.valueOf( time );
		    
		    // oauth parameters
		    List<Map.Entry<String, String>> parameters;
	        parameters = new ArrayList<Map.Entry<String, String>>();
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_nonce", oAuthNonce));
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_signature_method", oAuthSignatureMethod));
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_timestamp", oAuthTimestamp));
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_token", oAuthAccessToken));
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_version", oAuthVersion));
	        parameters.add(new AbstractMap.SimpleEntry<String, String>("oauth_consumer_key", consumerKey));
	       
		    // add the other parameters
	        if (uri.getQuery()!=null) {
	        	String[] pairs = uri.getQuery().split("&");
	        	for (String pair : pairs) {
	        		int idx = pair.indexOf("=");
	        		parameters.add(new AbstractMap.SimpleEntry<String, String>(pair.substring(0, idx), pair.substring(idx + 1)));
	        	}
	        }
	        
		    // sort the parameters alphabetically
	        Collections.sort(parameters, new Comparator<Map.Entry<String, String>>() {
	        	@Override
	        	public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2) {
	        		return o1.getKey().compareTo(o2.getKey());
	        	}
	        });
	        
	        // generate the "signature base string"
		    String signatureBaseString1 = method;
		    String signatureBaseString2 = url;
		    String signatureBaseString3 = "";
		    for (Map.Entry<String, String> me : parameters) {
		    	signatureBaseString3 = signatureBaseString3+"&"+me.getKey()+"="+me.getValue();
	        }
		    signatureBaseString3 = signatureBaseString3.substring(1); //get rid of first &
		    String signatureBaseString =  String.format("%s&%s&%s", 
		    											URLEncoder.encode(signatureBaseString1, "UTF-8"), 
		    											URLEncoder.encode(signatureBaseString2, "UTF-8"),
		    											URLEncoder.encode(signatureBaseString3, "UTF-8"));
	 
		    // generate the signature
		    String compositeKey = URLEncoder.encode(consumerSecret, "UTF-8") + "&" + URLEncoder.encode(oAuthAccessTokenSecret, "UTF-8");
		    String oAuthSignature = computeSignature(signatureBaseString, compositeKey);
		    String oAuthSignatureEncoded = URLEncoder.encode(oAuthSignature, "UTF-8");

		    // generate the header
		    String authorizationHeaderValueTempl = "OAuth oauth_consumer_key=\"%s\", oauth_nonce=\"%s\", oauth_signature=\"%s\", oauth_signature_method=\"%s\", oauth_timestamp=\"%s\", oauth_token=\"%s\", oauth_version=\"%s\"";
		    String authorizationHeaderValue = String.format(authorizationHeaderValueTempl,
		    													consumerKey,
		                                                        oAuthNonce,
		                                                        oAuthSignatureEncoded,
		                                                        oAuthSignatureMethod,
		                                                        oAuthTimestamp,
		                                                        oAuthAccessToken,
		                                                        oAuthVersion);

		    // add the header
	    	connection.setRequestProperty("Authorization", authorizationHeaderValue);
		} catch (Exception ex) {
			System.out.println("aktiv.API.sign_oauth()" + ex.toString());
		}
	}
	
	private String computeSignature(String baseString, String keyString) throws GeneralSecurityException, UnsupportedEncodingException, Exception {
	    SecretKey secretKey = null;
	    byte[] keyBytes = keyString.getBytes();
	    secretKey = new SecretKeySpec(keyBytes, "HmacSHA1");
	    Mac mac = Mac.getInstance("HmacSHA1");
	    mac.init(secretKey);
	    byte[] text = baseString.getBytes();
	    return new String(Base64.encodeBase64(mac.doFinal(text))).trim();
	}

	private void app_auth(HttpURLConnection connection, URI uri) {
		try {
			connection.setRequestProperty("Host", "api.twitter.com");
			connection.setRequestProperty("User-Agent", "Jorel");
	        connection.setRequestProperty("Authorization", "Bearer " + aAuthBearerToken);
			connection.setUseCaches(false);
		} catch (Exception ex) {
			System.out.println("aktiv.API.app_auth()" + ex.toString());
		}
	}

	public void setConsumerKey(String s) { consumerKey = s; }
	public void setConsumerSecret(String s) { consumerSecret = s; }
	public void setOAuthAccessToken(String s) { oAuthAccessToken = s; }
	public void setOAuthAccessTokenSecret(String s) { oAuthAccessTokenSecret = s; }
	public void setOAuthSignatureMethod(String s) { oAuthSignatureMethod = s; }
	public void setOAuthVersion(String s) { oAuthVersion = s; }
	public void setBearerToken(String s) { aAuthBearerToken = s; }
}
