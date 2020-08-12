package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.query.Query;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Words generated by hbm2java
 */
@Entity
@Table(name = "WORDS", schema = "TNO")
public class WordsDao extends Jorel2Root implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private String word;
	private BigDecimal type;

	public WordsDao() {
	}

	public WordsDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public WordsDao(BigDecimal rsn, String word, BigDecimal type) {
		this.rsn = rsn;
		this.word = word;
		this.type = type;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "next_rsn")
	@SequenceGenerator(name = "next_rsn", sequenceName = "NEXT_RSN", allocationSize=1)
	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "WORD", length = 100)
	public String getWord() {
		return this.word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	@Column(name = "TYPE", precision = 38, scale = 0)
	public BigDecimal getType() {
		return this.type;
	}

	public void setType(BigDecimal type) {
		this.type = type;
	}
	
	/**
	 * Returns all words of a particular type from the WORDS table.
	 * 
	 * @param process Name of the currently executing process, e.g. "jorel", "jorelMini3"
	 * @param wordType Type of word, e.g. "verb=1", "noise=2", "title=3"
	 * @param session - The currently active Hibernate DB session
	 * @return List of EventsDao objects that match the Events_FindRssEvents named query.
	 */
	public static List<WordsDao> getWords(Jorel2ServerInstance process, WordType wordType, Session session) {

		BigDecimal type = BigDecimal.valueOf(wordType.ordinal()+1);
		
		@SuppressWarnings("unchecked")
		Query<WordsDao> query = session.createNamedQuery("Quotes_FindWordsByType");
		query.setParameter("type", type);
        List<WordsDao> results = query.getResultList();
        
        return results;
	}
}
