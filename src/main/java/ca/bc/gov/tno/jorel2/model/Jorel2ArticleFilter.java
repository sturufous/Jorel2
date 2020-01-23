package ca.bc.gov.tno.jorel2.model;

import java.util.List;

import org.hibernate.Session;

public interface Jorel2ArticleFilter {

	public List<Jorel2ArticleFilter> getEnabledRecordList(Session session);
}
