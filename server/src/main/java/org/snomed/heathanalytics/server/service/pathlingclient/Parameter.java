package org.snomed.heathanalytics.server.service.pathlingclient;

import java.util.ArrayList;
import java.util.List;

public class Parameter {

	private String name;
	private String valueCode;
	private String valueString;
	private Boolean valueBoolean;
	private Integer valueUnsignedInt;
	private List<Parameter> part;

	public Parameter() {
	}

	public Parameter(String name) {
		this.name = name;
	}

	public Parameter valueCode(String valueCode) {
		this.valueCode = valueCode;
		return this;
	}

	public Parameter valueString(String valueString) {
		this.valueString = valueString;
		return this;
	}

	public Parameter addPart(Parameter part) {
		if (this.part == null) {
			this.part = new ArrayList<>();
		}
		this.part.add(part);
		return this;
	}

	public String getName() {
		return name;
	}

	public String getValueCode() {
		return valueCode;
	}

	public String getValueString() {
		return valueString;
	}

	public Boolean getValueBoolean() {
		return valueBoolean;
	}

	public Integer getValueUnsignedInt() {
		return valueUnsignedInt;
	}

	public List<Parameter> getPart() {
		return part;
	}
}
