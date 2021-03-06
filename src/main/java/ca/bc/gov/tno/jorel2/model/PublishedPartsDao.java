package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.Session;
import org.hibernate.query.Query;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * PublishedParts generated by hbm2java
 */
@Entity
@Table(name = "PUBLISHED_PARTS", schema = "TNO", uniqueConstraints = @UniqueConstraint(columnNames = "NAME"))
public class PublishedPartsDao extends Jorel2Root implements java.io.Serializable  {

	private static final long serialVersionUID = 1L;
	private BigDecimal partRsn;
	private String name;
	private String type;
	private Clob content;
	private String title;
	private String templatePart;
	private String image;

	public PublishedPartsDao() {
	}

	public PublishedPartsDao(BigDecimal partRsn, String name) {
		this.partRsn = partRsn;
		this.name = name;
	}

	public PublishedPartsDao(BigDecimal partRsn, String name, String type, Clob content, String title, String templatePart,
			String image) {
		this.partRsn = partRsn;
		this.name = name;
		this.type = type;
		this.content = content;
		this.title = title;
		this.templatePart = templatePart;
		this.image = image;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "next_rsn")
	@SequenceGenerator(name = "next_rsn", sequenceName = "NEXT_RSN", allocationSize=1)
	@Column(name = "PART_RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getPartRsn() {
		return this.partRsn;
	}

	public void setPartRsn(BigDecimal partRsn) {
		this.partRsn = partRsn;
	}

	@Column(name = "NAME", unique = true, nullable = false, length = 50)
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Column(name = "TYPE", length = 30)
	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Column(name = "CONTENT")
	public Clob getContent() {
		return this.content;
	}

	public void setContent(Clob content) {
		this.content = content;
	}

	@Column(name = "TITLE", length = 250)
	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Column(name = "TEMPLATE_PART", length = 50)
	public String getTemplatePart() {
		return this.templatePart;
	}

	public void setTemplatePart(String templatePart) {
		this.templatePart = templatePart;
	}

	@Column(name = "IMAGE", length = 100)
	public String getImage() {
		return this.image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	/**
	 * Get a published part by name. If there is no record in PUBLISHED_PARTS matching the name then return the default value
	 * provided by the caller in the deflt parameter.
	 *
	 * @param name The name of the published part.
	 * @param deflt The default value for the key <code>name</code>
	 * @param keyStr key to be associated with the result in the parts map.
	 * @param parts Map that contains all published parts obtained during this run.
	 * @param session The current Hibernate persistence context
	 */
	public static void getPublishedPartByName(String name, String deflt, String keyStr, Map<String, String> parts, Session session) {
		
		String sqlStmt = "from PublishedPartsDao where name=:name";
		
		Query<PublishedPartsDao> query = session.createQuery(sqlStmt, PublishedPartsDao.class);
		query.setParameter("name", name);
        List<PublishedPartsDao> results = query.getResultList();
        
        if(results.size() > 0) {
        	Clob clob = results.get(0).getContent();
            long len;
			try {
				String result = "";
				len = clob.length();
	            result = clob.getSubString(1, (int) len);
	            parts.put(keyStr, result);
			} catch (SQLException e) {
				decoratedError(INDENT0, "Extracting Clob content.", e);
			}
        } else {
        	parts.put(keyStr, deflt);
        }
	}
	
	/**
	 * Get a published part by name. If there is no record in PUBLISHED_PARTS matching the name then return the default value
	 * provided by the caller in the deflt parameter. Uses the Transactional annotation to ensure any open result sets on the
	 * session's connection are not closed.
	 *
	 * @param name The name of the published part.
	 * @param deflt The default value for the key <code>name</code>
	 * @param session The current Hibernate persistence context
	 * @return The published part that matches the name.
	 */
	public static String getPublishedPartByName(String name, String deflt, Session session) {
		
		String sqlStmt = "from PublishedPartsDao where name=:name";
		String result = "";
		
		Query<PublishedPartsDao> query = session.createQuery(sqlStmt, PublishedPartsDao.class);
		query.setParameter("name", name);
        List<PublishedPartsDao> results = query.getResultList();
        
        if(results.size() > 0) {
        	Clob clob = results.get(0).getContent();
            long len;
			try {
				len = clob.length();
	            result = clob.getSubString(1, (int) len);
			} catch (SQLException e) {
				decoratedError(INDENT0, "Extracting Clob content.", e);
			}
        } else {
        	result = deflt;
        }
        
		return result;
	}
	
	/**
	 * Get a published part by name. If there is no record in PUBLISHED_PARTS matching the name then return the default value
	 * provided by the caller in the deflt parameter. Uses the Transactional annotation to ensure any open result sets on the
	 * session's connection are not closed.
	 *
	 * @param name The name of the published part.
	 * @param deflt The default value for the key <code>name</code>
	 * @param session The current Hibernate persistence context
	 * @return The published part that matches the name.
	 */
	public static PublishedPartsDao getPublishedPartByName(String name, Session session) {
		
		String sqlStmt = "from PublishedPartsDao where name=:name";
		PublishedPartsDao result = null;
		
		Query<PublishedPartsDao> query = session.createQuery(sqlStmt, PublishedPartsDao.class);
		query.setParameter("name", name);
        List<PublishedPartsDao> results = query.getResultList();
        
        if(results.size() > 0) {
        	result = (PublishedPartsDao) results.get(0);
        }
        
		return result;
	}
}
