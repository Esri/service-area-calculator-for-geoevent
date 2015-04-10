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

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.http.GeoEventHttpClientService;
import com.esri.ges.manager.datastore.agsconnection.ArcGISServerConnectionManager;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManager;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.processor.GeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorServiceBase;

public class ServiceAreaCalculatorService extends GeoEventProcessorServiceBase
{

	private GeoEventHttpClientService			httpService;
	private GeoEventCreator								geoEventCreator;
	private GeoEventDefinitionManager			geoEventDefinitionManager;
	private ArcGISServerConnectionManager	agsConnectionManager;

	public ServiceAreaCalculatorService()
	{
		definition = new ServiceAreaCalculatorDefinition();
	}

	@Override
	public GeoEventProcessor create() throws ComponentException
	{
		return new ServiceAreaCalculator(definition, httpService, geoEventCreator, geoEventDefinitionManager, agsConnectionManager);
	}

	public void setHttpService(GeoEventHttpClientService httpService)
	{
		this.httpService = httpService;
	}

	public void setMessaging(Messaging messaging)
	{
		this.geoEventCreator = messaging.createGeoEventCreator();
	}

	public void setGeoEventDefinitionManager(GeoEventDefinitionManager geoEventDefinitionManager)
	{
		this.geoEventDefinitionManager = geoEventDefinitionManager;
	}

	public void setAgsConnectionManager(ArcGISServerConnectionManager agsConnectionManager)
	{
		this.agsConnectionManager = agsConnectionManager;
	}
}
