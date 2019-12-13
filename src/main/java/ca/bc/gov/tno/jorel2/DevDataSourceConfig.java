package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
class DevDataSourceConfig implements DataSourceConfig {

	public String systemName = "vongole.tno.gov.bc.ca";
	
	DevDataSourceConfig() {
		System.out.println("Setup for dev profile.");
	}
	
	@Override
	public void setup() {
	}
	
	public String getSystemName() {
		
		return systemName;
	}
}
