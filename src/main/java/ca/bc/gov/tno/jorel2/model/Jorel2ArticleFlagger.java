package ca.bc.gov.tno.jorel2.model;

import java.math.BigDecimal;
import java.util.Date;

public interface Jorel2ArticleFlagger {

	public void setItemRsn(BigDecimal itemRsn);
	public void setIssueRsn(BigDecimal issueRsn);
	public void setEntryDate(Date date);
}
