package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * LastDosyncindex generated by hbm2java
 */
@Entity
@Table(name = "LAST_DOSYNCINDEX", schema = "TNO")
public class LastDosyncindexDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private Serializable dosyncindex;

	public LastDosyncindexDao() {
	}

	public LastDosyncindexDao(Serializable dosyncindex) {
		this.dosyncindex = dosyncindex;
	}

	@Id

	@Column(name = "DOSYNCINDEX")
	public Serializable getDosyncindex() {
		return this.dosyncindex;
	}

	public void setDosyncindex(Serializable dosyncindex) {
		this.dosyncindex = dosyncindex;
	}

}
