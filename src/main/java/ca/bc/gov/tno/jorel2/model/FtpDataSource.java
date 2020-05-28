package ca.bc.gov.tno.jorel2.model;

import java.io.*;
import java.util.Vector;

// FTP
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// SFTP
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

@Service
public class FtpDataSource {
	
	/** The name of the FTP server to connect to, e.g., alfredo.tno.gov.bc.ca */
	@Value("${ftp.host}")
	String host;
	
	/** The user name to use when connecting to the FTP server */
	@Value("${ftp.userName}")
	String userName;
	
	/** The password to use when connecting to the FTP server */
	@Value("${ftp.password}")
	String password;
	
	/** Indicates whether this FTP connection should be secure or not */
	@Value("${ftp.secure}")
	String secureFlag;
	
	/** Indicates whether this FTP connection is active or passive */
	@Value("${ftp.type}")
	String type;
	
	String ftpError;
	
	// FTP
	FTPClient ftp;

	// SFTP
	ChannelSftp channel;
	Session session;
	boolean secure;

	public FtpDataSource() {
	}

	public boolean connect() {
		
		secure = secureFlag.contentEquals("yes");
		if (secure) {
			JSch jsch=new JSch();

			try {
				// create session
				session=jsch.getSession(userName, host, 22);
				session.setPassword(password);

				// configure : do not do strict host checking
				java.util.Hashtable<String,String> config = new java.util.Hashtable<>();
				config.put("StrictHostKeyChecking", "no");
				session.setConfig(config);

				// connect
				session.connect();
				channel=(ChannelSftp)session.openChannel("sftp");
				channel.connect();
			} catch (Exception e) {
				setError("jorelFTP.connect(s): Error "+e.toString());
				return false;
			}
		} else {
			try {
				ftp = new FTPClient();
				ftp.connect(host);
				int reply = ftp.getReplyCode();
				if (!FTPReply.isPositiveCompletion(reply) ) {
					setError("jorelFTP.connect(): Cannot connect to FTP server");
					return false;
				} else {
					if (! ftp.login(userName, password) ) {
						setError("jorelFTP.connect(): Cannot connect to FTP server");
						return false;
					}
				}	
			} catch (Exception e) {
				setError("jorelFTP.connect(): Error "+e.toString());
				return false;
			}
		}
		return true;
	}

	public boolean upload(String localPath, String remotePath) {
		
		if (secure) {
			try {
				channel.put(localPath, remotePath, ChannelSftp.OVERWRITE);
			} catch(Exception e) {
				setError("jorelFTP.upload(s): Error " + e.toString());
				return false;
			}
		} else {
			try {
				FileInputStream in = new FileInputStream(localPath);
				if (! ftp.storeFile(remotePath, in)) {
					setError("jorelFTP.upload(): Cannot upload file");
					return false;
				}
				in.close();
			} catch(Exception e) {
				setError("jorelFTP.upload(): Error " + e.toString());
				return false;
			}
		}
		return true;
	}

	public long filesize(String remotePath)
	{
		long fs = 0;
		if(secure)
		{
			try
			{
				String ls = channel.ls(remotePath).toString();
				//System.out.println("ls "+ls);
				ls = ls.replaceAll(" +", " ");
				String[] parts = ls.split(" ");
				
				String fs_part = parts[4];
				
				fs = Long.parseLong(fs_part) / 1024;	//kb
				
			} catch(Exception e)
			{
				setError("jorelFTP.filesize(): Error " + e.toString());
			}
		}
		return fs;
	}

	public boolean download(String localPath, String remotePath) {
		if (secure) {
			try {
				channel.get(remotePath, localPath);
			} catch(Exception e) {
				setError("jorelFTP.download(s): Error " + e.toString());
				return false;
			}
		} else {
			try {
				FileOutputStream out = new FileOutputStream(localPath);
				if (! ftp.retrieveFile(remotePath, out) ) {
					setError("jorelFTP.download(): Cannot download file");
					return false;
				}
				out.close();
			} catch(Exception e) {
				setError("jorelFTP.download(): Error " + e.toString());
				return false;
			}
		}
		return true;
	}

	public boolean delete(String path) {
		if (secure) {
			try {
				channel.rm(path);
			} catch(Exception e) {
				setError("jorelFTP.delete(s): Error " + e.toString());
				return false;
			}
		} else {
			try {
				if (! ftp.deleteFile(path)) {
					setError("jorelFTP.delete(): Cannot delete file");
					return false;
				}
			} catch(Exception e) {
				setError("jorelFTP.delete(): Error " + e.toString());
				return false;
			}
		}
		return true;
	}

	public boolean exists(String path) {
		if (secure) {
			try {
				Vector<ChannelSftp.LsEntry> list = channel.ls(path);
				for (ChannelSftp.LsEntry entry : list) {
					return true;
				}
			} catch(Exception e) { ; }
		} else {
			try {
				String files[] = ftp.listNames(path);
				if (files != null) {
					if (files.length > 0) {
						return true;
					}
				}
			} catch(Exception e) { ; }
		}
		return false;
	}

	public boolean isConnected() {
		if (secure) {
			try {
				ChannelSftp c=(ChannelSftp)channel;
				if (c.isConnected()) {
					return true;
				}
			} catch(Exception e) { ; }
		} else {
			try {
				if (ftp.isConnected()) {
					return true;
				}
			} catch(Exception e) { ; }
		}
		return false;
	}

	public void disconnect() {
		if (secure) {
			try {
				channel.disconnect();
				session.disconnect();
			} catch(Exception e) { ; }
		} else {
			try {
				ftp.disconnect();
			} catch(Exception e) { ; }
		}
	}
	
	public void setModePassive() {
		if (!secure) {
			try {
				ftp.enterLocalPassiveMode();
			} catch(Exception e) { ; }
		}
	}
	
	public void setModeActive() {
		if (!secure) {
			try {
				ftp.enterLocalActiveMode();
			} catch(Exception e) { ; }
		}
	}
	
	public void setTypeBinary() {
		if (!secure) {
			try {
				ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
			} catch(Exception e) { ; }
		}
	}
	
	public void setTypeAscii() {
		if (!secure) {
			try {
				ftp.setFileType(org.apache.commons.net.ftp.FTP.ASCII_FILE_TYPE);
			} catch(Exception e) { ; }
		}
	}
	
	public void setError(String err) {
		ftpError = err;
	}
	public String getError() {
		return ftpError;
	}
}