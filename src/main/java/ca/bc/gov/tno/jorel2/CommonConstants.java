package ca.bc.gov.tno.jorel2;

public enum CommonConstants {

	SYSTEM_DESCRIPTOR("systemdescriptor.properties"),
	PROPERTIES_PATH("properties");

	private String value;

	CommonConstants(String value) {
		this.value = value;
	}

	String getValue() {
		return value;
	}
}
