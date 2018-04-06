/*
  Copyright 1995-2015 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
 */

package com.esri.geoevent.processor.serviceareacalculator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import com.esri.core.geometry.MapGeometry;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.http.GeoEventHttpClient;
import com.esri.ges.core.http.GeoEventHttpClientService;
import com.esri.ges.core.http.KeyValue;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.manager.datastore.agsconnection.ArcGISServerConnection;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.manager.datastore.agsconnection.ArcGISServerConnectionManager;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManager;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManagerException;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.GeoEventProcessorBase;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.util.GeometryUtil;
import com.esri.ges.util.Validator;

public class ServiceAreaCalculator extends GeoEventProcessorBase
{
	final private static BundleLogger			LOGGER				= BundleLoggerFactory.getLogger(ServiceAreaCalculator.class);

	final static Object[]									wkidPath			= new Object[] { "spatialReference", "wkid" };
	final static Object[]									geometryPath	= new Object[] { "geometry" };

	private String												serviceAreaSolverPath;
	private int														driveTime;
	private GeoEventHttpClientService			httpService;
	private String												inputGeometryField;
	private String												outputGeometryField;
	private boolean												overrideInputField;
	private String												outputGEDName;
	private GeoEventCreator								geoEventCreator;
	private GeoEventDefinitionManager			geoEventDefinitionManager;
	private ArcGISServerConnectionManager	agsConnectionManager;
	private Map<String, String>						edMapper			= new ConcurrentHashMap<String, String>();
	private String												naConnectionName;
	private String												outputPolygonType;

	protected ServiceAreaCalculator(GeoEventProcessorDefinition definition, GeoEventHttpClientService httpService, GeoEventCreator geoEventCreator, GeoEventDefinitionManager geoEventDefinitionManager, ArcGISServerConnectionManager agsConnectionManager) throws ComponentException
	{
		super(definition);
		this.httpService = httpService;
		this.geoEventCreator = geoEventCreator;
		this.geoEventDefinitionManager = geoEventDefinitionManager;
		this.agsConnectionManager = agsConnectionManager;
	}

	@Override
	public GeoEvent process(GeoEvent geoEvent) throws Exception
	{
		try
		{
			if (geoEvent.getField(inputGeometryField) instanceof MapGeometry)
			{
				if (geoEvent != null && geoEventDefinitionManager != null)
				{
					Object obj = processGeometry(geoEvent, inputGeometryField);

					if (overrideInputField)
						geoEvent.setField(inputGeometryField, obj);
					else
					{
						FieldDefinition fd = geoEvent.getGeoEventDefinition().getFieldDefinition(outputGeometryField);
						if (fd != null && fd.getType() != FieldType.Geometry)
							throw new ValidationException(LOGGER.translate("PROCESSOR_OUTPUTFIELD_ERROR"));

						if (fd != null)
						{
							if (Validator.isEmpty(outputGEDName))
							{
								geoEvent.setField(outputGeometryField, obj);
							}
							else
							{
								GeoEventDefinition edOut = update(geoEvent.getGeoEventDefinition());
								geoEvent = populateGeoEvent(geoEvent, edOut, null);
								geoEvent.setField(outputGeometryField, obj);
							}
						}
						else
						{
							GeoEventDefinition edOut = update(geoEvent.getGeoEventDefinition());
							geoEvent = populateGeoEvent(geoEvent, edOut, obj);
						}
					}
					return geoEvent;
				}
			}
			else
				throw new ValidationException(LOGGER.translate("PROCESSOR_INPUTFIELD_ERROR"));
		}
		catch (Exception e)
		{
			LOGGER.error("PROCESSOR_UNABLE_ERROR", e.getMessage());
			LOGGER.info(e.getMessage(), e);
		}
		return null;
	}

	synchronized private void clearGeoEventDefinitionMapper()
	{
		if (!edMapper.isEmpty())
		{
			for (String guid : edMapper.values())
			{
				try
				{
					geoEventDefinitionManager.deleteGeoEventDefinition(guid);
				}
				catch (GeoEventDefinitionManagerException e)
				{
					LOGGER.warn("PROCESSOR_FAILED_TO_DELETE_GED", guid, e.getMessage());
				}
			}
			edMapper.clear();
		}
	}

	private GeoEvent populateGeoEvent(GeoEvent geoEvent, GeoEventDefinition edOut, Object outputGeometry) throws MessagingException
	{
		GeoEvent outGeoEvent;
		if (outputGeometry == null)
			outGeoEvent = geoEventCreator.create(edOut.getGuid(), geoEvent.getAllFields());
		else
			outGeoEvent = geoEventCreator.create(edOut.getGuid(), new Object[] { geoEvent.getAllFields(), outputGeometry });

		outGeoEvent.setProperty(GeoEventPropertyName.TYPE, geoEvent.getProperty(GeoEventPropertyName.TYPE));
		outGeoEvent.setProperty(GeoEventPropertyName.OWNER_ID, geoEvent.getProperty(GeoEventPropertyName.OWNER_ID));
		outGeoEvent.setProperty(GeoEventPropertyName.OWNER_URI, geoEvent.getProperty(GeoEventPropertyName.OWNER_URI));

		for (Map.Entry<GeoEventPropertyName, Object> property : geoEvent.getProperties())
		{
			if (!outGeoEvent.hasProperty(property.getKey()))
				outGeoEvent.setProperty(property.getKey(), property.getValue());
		}

		return outGeoEvent;
	}

	synchronized private GeoEventDefinition update(GeoEventDefinition edIn) throws Exception
	{
		FieldDefinition fd = edIn.getFieldDefinition(outputGeometryField);

		GeoEventDefinition edOut = edMapper.containsKey(edIn.getGuid()) ? geoEventDefinitionManager.getGeoEventDefinition(edMapper.get(edIn.getGuid())) : null;
		if (edOut == null)
		{
			if (fd == null)
			{
				FieldDefinition newfd = new DefaultFieldDefinition(outputGeometryField, FieldType.Geometry);
				edOut = edIn.augment(Arrays.asList(newfd));
			}
			else
			{
				edOut = (GeoEventDefinition) edIn.clone();
			}

			edOut.setOwner(getId());

			if (!outputGEDName.isEmpty())
			{
				edOut.setName(outputGEDName);
				geoEventDefinitionManager.addTemporaryGeoEventDefinition(edOut, false);
			}
			else
			{
				geoEventDefinitionManager.addTemporaryGeoEventDefinition(edOut, true);
			}

			edMapper.put(edIn.getGuid(), edOut.getGuid());
		}

		return edOut;
	}

	@Override
	public void afterPropertiesSet()
	{
		super.afterPropertiesSet();
		inputGeometryField = hasProperty("inputGeometryField") ? getProperty("inputGeometryField").getValueAsString().trim() : "";
		overrideInputField = Boolean.parseBoolean(getProperty("replaceGeometry").getValueAsString());
		if (overrideInputField)
		{
			geoEventMutator = true;
			outputGeometryField = inputGeometryField;
		}
		else
		{
			outputGeometryField = getProperty("outputGeometryField").getValueAsString().trim();
			outputGEDName = getProperty("outputGEDName").getValueAsString().trim();
			if (outputGEDName.isEmpty())
				geoEventMutator = true;
			else
				geoEventMutator = false;
		}

		naConnectionName = getProperty(ServiceAreaCalculatorDefinition.NA_CONNECTION_PROPERTY).getValueAsString();
		serviceAreaSolverPath = getProperty(ServiceAreaCalculatorDefinition.NA_PATH_PROPERTY).getValueAsString();
		outputPolygonType = getProperty(ServiceAreaCalculatorDefinition.OUTPUT_POLYGON_TYPE_PROPERTY).getValueAsString();
		driveTime = Integer.parseInt(getProperty(ServiceAreaCalculatorDefinition.DRIVE_TIME_PROPERTY).getValueAsString());
	}

	protected Object processGeometry(GeoEvent geoevent, String geometryField) throws Exception
	{
		MapGeometry geomout = null;
		try
		{
			MapGeometry geom = (MapGeometry) geoevent.getField(geometryField);

			geomout = getAreaAroundPoint(geom);
		}
		catch (Exception e)
		{
			if (geoevent.getTrackId() == null)
				throw new Exception(LOGGER.translate("SERVICE_AREA_UNABLE_ERROR1", e.getMessage()), e);
			else
				throw new Exception(LOGGER.translate("SERVICE_AREA_UNABLE_ERROR2", geoevent.getTrackId(), e.getMessage()), e);
		}
		return geomout;
	}

	//Handle the incompatibility with 10.5.0 Patch 1 release
	public Collection<KeyValue> getDefaultParamsForRequest(ArcGISServerConnection agsConnection)
	{
	  Collection<KeyValue> params = new ArrayList<KeyValue>();
	  params.add(new KeyValue("f", "json"));
	  String token = agsConnection.getDecryptedToken();
	  if (token != null)
	    params.add(new KeyValue("token", token));

	  return params;
	} 
	
	public HttpPost createPostRequest(URL url, Collection<KeyValue> parameters) throws IOException
  {
    List<NameValuePair> formParams = new ArrayList<NameValuePair>();
    if (parameters != null)
    {
      for (KeyValue parameter : parameters)
      {
        formParams.add(new BasicNameValuePair(parameter.getKey(), parameter.getValue()));
        LOGGER.debug("HTTP_ADDING_PARAM", parameter.getKey(), parameter.getValue());
      }
    }
    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formParams, "UTF-8");

    HttpPost httpPost;
    try
    {
      httpPost = new HttpPost(url.toURI());
    }
    catch (URISyntaxException e)
    {
      throw new RuntimeException(e);
    }
    httpPost.setEntity(entity);
    httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
    httpPost.setHeader("charset", "utf-8");

    return httpPost;
  }

	public MapGeometry getAreaAroundPoint(MapGeometry point)
	{
		ArcGISServerConnection agsConnection = agsConnectionManager.getArcGISServerConnection(naConnectionName);

		Collection<KeyValue> params = new ArrayList<KeyValue>();
		params.addAll(getDefaultParamsForRequest(agsConnection));
    //params.addAll(agsConnection.getDefaultParamsForRequest());
		params.add(new KeyValue("facilities", generateFacilitiesJson(point)));
		params.add(new KeyValue("defaultBreaks", (new Integer(driveTime)).toString()));
		params.add(new KeyValue("travelDirection", "esriNATravelDirectionFromFacility"));

		if (!Validator.isEmpty(outputPolygonType))
			params.add(new KeyValue("outputPolygons", outputPolygonType));

		try (GeoEventHttpClient http = httpService.createNewClient())
		{
			StringBuffer urlString = new StringBuffer();
			urlString.append(agsConnection.getUrl().toExternalForm());
			urlString.append(serviceAreaSolverPath);

			URL url = new URL(urlString.toString());
			//HttpPost postRequest = http.createPostRequest(url, params);
      HttpPost postRequest = createPostRequest(url, params);
			postRequest.addHeader("Referer", agsConnection.getReferer());
			String responseString = http.executeAndReturnBody(postRequest, GeoEventHttpClient.DEFAULT_TIMEOUT);
			/*
			HttpGet getRequest = http.createGetRequest(url, params);
			getRequest.addHeader("Referer", agsConnection.getReferer());
      String responseString = http.executeAndReturnBody(getRequest, GeoEventHttpClient.DEFAULT_TIMEOUT);
			*/
			return parseAreaSolverReply(responseString);
		}
		catch (Exception e)
		{
			LOGGER.debug("SERVICE_AREA_UNABLE_ERROR1", e.getMessage());
			LOGGER.info(e.getMessage(), e);
		}

		return null;
	}

	private MapGeometry parseAreaSolverReply(String reply)
	{
		try
		{
			JsonNode response = (new ObjectMapper()).readTree(reply);
			JsonNode saPolygon = response.get("saPolygons");
			if (saPolygon == null)
			{
				LOGGER.error("SERVICE_AREA_NO_SAPOLYGON", reply);
				return null;
			}

			List<MapGeometry> polygons = getGeometriesFromNAReply(saPolygon);
			if (polygons.size() > 1)
			{
				LOGGER.info("SERVICE_AREA_MULTIPLE_RESULT ", reply);
			}
			if (polygons.size() == 0)
			{
				LOGGER.error("SERVICE_AREA_NO_GEOMETRY_IN_RESULT", reply);
				return null;
			}

			return polygons.get(0);
		}
		catch (Exception e)
		{
			throw new RuntimeException(LOGGER.translate("SERVICE_AREA_UNABLE_ERROR1", e.getMessage()), e);
		}
	}

	private List<MapGeometry> getGeometriesFromNAReply(JsonNode jsonNode) throws Exception
	{
		if (jsonNode == null)
		{
			LOGGER.error("SERVICE_AREA_NO_ROUTES_IN_RESULT");
			return null;
		}

		int wkid = getNodeFollowingPath(jsonNode, wkidPath).asInt();
		String wkidStr = Integer.toString(wkid);
		String geometryString;
		List<MapGeometry> retList = new ArrayList<MapGeometry>();
		MapGeometry mapGeometry;
		for (JsonNode feature : getNodeFollowingPath(jsonNode, new Object[] { "features" }))
		{
			geometryString = geometryStringFromJsonNode(getNodeFollowingPath(feature, geometryPath), wkidStr);
			mapGeometry = GeometryUtil.fromJson(geometryString);
			retList.add(mapGeometry);
		}

		return retList;
	}

	private JsonNode getNodeFollowingPath(JsonNode jsonNode, Object[] nodePath)
	{
		for (Object property : nodePath)
		{
			if (property instanceof String)
			{
				jsonNode = jsonNode.get((String) property);
			}
			else if (property instanceof Integer)
			{
				Integer index = (Integer) property;
				jsonNode = jsonNode.get(index);
			}
			if (jsonNode == null)
			{
				break;
			}
		}
		return jsonNode;
	}

	private String geometryStringFromJsonNode(JsonNode geometry, String outSR)
	{
		String geometryString = geometry.toString();
		return geometryString.substring(0, geometryString.length() - 1) + ",\"spatialReference\":{\"wkid\":" + outSR + "}}";
	}

	private String generateFacilitiesJson(MapGeometry point)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("{\"type\":\"features\",\"features\":[{\"geometry\":");
		sb.append(removeZFromGeom(GeometryUtil.toJson(point)));
		sb.append("}]}");
		return sb.toString();
	}

	private String removeZFromGeom(String geomString)
	{
		geomString = new String(geomString);
		JsonFactory factory = new JsonFactory();
		ObjectMapper mapper = new ObjectMapper(factory);
		JsonParser parser;
		try
		{
			parser = factory.createJsonParser(geomString.getBytes());
			TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() { };
			HashMap<String, Object> o = mapper.readValue(parser, typeRef);
			if (o.containsKey("z"))
			{
				o.remove("z");
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				mapper.writeValue(baos, o);
				geomString = baos.toString();
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(LOGGER.translate("SERVICE_AREA_ERROR_REMOVING_Z", e.getMessage()), e);
		}
		return geomString;
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
		clearGeoEventDefinitionMapper();
	}
}
