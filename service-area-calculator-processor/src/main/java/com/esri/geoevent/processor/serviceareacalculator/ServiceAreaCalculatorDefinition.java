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

import java.util.ArrayList;
import java.util.List;

import com.esri.ges.core.property.LabeledValue;
import com.esri.ges.core.property.PropertyDefinition;
import com.esri.ges.core.property.PropertyType;
import com.esri.ges.processor.GeoEventProcessorDefinitionBase;

public class ServiceAreaCalculatorDefinition extends GeoEventProcessorDefinitionBase
{
	public final static String	NA_CONNECTION_PROPERTY				= "naConnectionName";
	public final static String	NA_PATH_PROPERTY							= "routeSolverPath";
	public final static String	DRIVE_TIME_PROPERTY						= "driveTimeMinutes";
	public final static String	OUTPUT_POLYGON_TYPE_PROPERTY	= "outputPolygonType";

	public ServiceAreaCalculatorDefinition()
	{
		try
		{
			propertyDefinitions.put("inputGeometryField", new PropertyDefinition("inputGeometryField", PropertyType.GeoEventField_Geometry, "GEOMETRY", "${com.esri.geoevent.processor.service-area-calculator-processor.INPUTGEOMETRYFIELD_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.INPUTGEOMETRYFIELD_DESC}", true, false));
			propertyDefinitions.put("replaceGeometry", new PropertyDefinition("replaceGeometry", PropertyType.Boolean, "true", "${com.esri.geoevent.processor.service-area-calculator-processor.REPLACEGEOMETRY_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.REPLACEGEOMETRY_DESC}", true, false));
			propertyDefinitions.put("outputGeometryField", new PropertyDefinition("outputGeometryField", PropertyType.String, "", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUTGEOMETRYFIELD_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUTGEOMETRYFIELD_DESC}", "replaceGeometry=false", false, false));
			propertyDefinitions.put("outputGEDName", new PropertyDefinition("outputGEDName", PropertyType.String, "", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUTGEDNAME_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUTGEDNAME_DESC}", "replaceGeometry=false", false, false));
			propertyDefinitions.put(NA_CONNECTION_PROPERTY, new PropertyDefinition(NA_CONNECTION_PROPERTY, PropertyType.ArcGISConnection, "", "${com.esri.geoevent.processor.service-area-calculator-processor.AGS_CONNECTION_NAME_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.AGS_CONNECTION_NAME_DESC}", true, false));

			propertyDefinitions.put(NA_PATH_PROPERTY, new PropertyDefinition(NA_PATH_PROPERTY, PropertyType.String, "rest/services/Network/USA/NAServer/Service%20Area/solveServiceArea", "${com.esri.geoevent.processor.service-area-calculator-processor.NA_SOLVER_PATH_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.NA_SOLVER_PATH_DESC}", false, false));
			propertyDefinitions.put(DRIVE_TIME_PROPERTY, new PropertyDefinition(DRIVE_TIME_PROPERTY, PropertyType.Integer, 2, "${com.esri.geoevent.processor.service-area-calculator-processor.DRIVE_TIME_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.DRIVE_TIME_DESC}", false, false));

			List<LabeledValue> outputPolygonType = new ArrayList<LabeledValue>(2);
			outputPolygonType.add(new LabeledValue("${com.esri.geoevent.processor.service-area-calculator-processor.POLYGON_TYPE_DETAILED_LBL}", "esriNAOutputPolygonDetailed"));
			outputPolygonType.add(new LabeledValue("${com.esri.geoevent.processor.service-area-calculator-processor.POLYGON_TYPE_SIMPLIFIED_LBL}", "esriNAOutputPolygonSimplified"));
			propertyDefinitions.put(OUTPUT_POLYGON_TYPE_PROPERTY, new PropertyDefinition(OUTPUT_POLYGON_TYPE_PROPERTY, PropertyType.String, "esriNAOutputPolygonSimplified", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUT_POLYGON_TYPE_LABEL}", "${com.esri.geoevent.processor.service-area-calculator-processor.OUTPUT_POLYGON_TYPE_DESC}", false, false, outputPolygonType));

		}
		catch (Exception e)
		{
			;
		}
	}

	@Override
	public String getName()
	{
		return "ServiceAreaCreator";
	}

	@Override
	public String getDomain()
	{
		return "com.esri.geoevent.processor.serviceareacalculator";
	}

	@Override
	public String getLabel()
	{
		return "${com.esri.geoevent.processor.service-area-calculator-processor.PROCESSOR_LBL}";
	}

	@Override
	public String getDescription()
	{
		return "${com.esri.geoevent.processor.service-area-calculator-processor.PROCESSOR_DESC}";
	}

	@Override
	public String getVersion()
	{
		return "10.5.0";
	}
}
