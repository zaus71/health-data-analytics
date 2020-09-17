package org.snomed.heathanalytics.server.service.pathlingclient;

import java.util.ArrayList;
import java.util.List;

public class AggregateRequest {

	private final String resourceType = "Parameters";

	private List<Parameter> parameters;

	public AggregateRequest(String subjectResource) {
		parameters = new ArrayList<>();
		addParameter(new Parameter("subjectResource").valueCode(subjectResource));
	}

	public AggregateRequest addAggregation(String label, String expression) {
		addParameter(new Parameter("aggregation")
				.addPart(new Parameter("label").valueString(label))
				.addPart(new Parameter("expression").valueString(expression))
		);
		return this;
	}
	public AggregateRequest addGrouping(String label, String expression) {
		addParameter(new Parameter("grouping")
				.addPart(new Parameter("label").valueString(label))
				.addPart(new Parameter("expression").valueString(expression))
		);
		return this;
	}

	public AggregateRequest addFilter(String valueString) {
		addParameter(new Parameter("filter").valueString(valueString));
		return this;
	}

	private void addParameter(Parameter parameter) {
		parameters.add(parameter);
	}

	public String getResourceType() {
		return resourceType;
	}

	public List<Parameter> getParameter() {
		return parameters;
	}
}
