package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Profile("prod")
class ProdDataSourceConfig implements DataSourceConfig {

	public String systemName = "scorelli.tno.gov.bc.ca";
	
	ProdDataSourceConfig() {
		System.out.println("Setup for prod profile.");
	}
	
	public void setup() {
	}
	
	public String getSystemName() {
		
		return systemName;
	}
}
