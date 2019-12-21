package ca.bc.gov.tno.jorel2.model;

import java.io.Serializable;
import javax.persistence.*;

@Entity
@Table(name = "PREFERENCES")
public class PreferencesDao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Id
	@Column(name = "RSN")
	long rsn;
	
	@Column(name = "APPLICATION_TITLE")
	private String applicationTitle;
	
	public PreferencesDao() {
	}
	 
	public long getRsn() {
		return rsn;
	}
	
	public void setRsn(long rsn) {
		this.rsn = rsn;
	}
	
	public long getApplicationTitle() {
		return rsn;
	}
	
	public void setApplicationTitle(String title) {
		this.applicationTitle = title;
	}
	
	@Override
	public String toString(){
		return "id="+rsn+", title="+applicationTitle;
	}
}
