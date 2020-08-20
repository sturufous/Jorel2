package ca.bc.gov.tno.jorel2.model;

public class LogMessage {
	java.util.Date date = new java.util.Date();
	String message;
	public LogMessage(String msg) {
		date = new java.util.Date();
		message=msg;
	}
	public java.util.Date getDate() { return date; }
	public String getMessage() { return message; }
}
