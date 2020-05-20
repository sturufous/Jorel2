package ca.bc.gov.tno.jorel2.model;

import java.math.BigDecimal;

public interface ArticleFilter {

	public BigDecimal getRsn();
	public String getIssue();
	public String getWords();
	public String getWordsCaseSensitive();
	public BigDecimal getMinOccurance();
}
