package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.FilesImportedDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemImagesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.SourcesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Provides the functionality necessary to register TNO front page images for the products of various publishers.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
class FrontPageImageHandler extends Jorel2Root {
	
	/** Full pathname of binary root directory */
	@Value("${binaryRoot}")
	private String binaryRoot;
	
	/** Web root used in constructing path to audio-video files (a key in NEWS_ITEM_IMAGES) */
	@Value("${wwwBinaryRoot}")
	private String wwwBinaryRoot;
	
	/**
	 * Manages the distribution and registration of a front page image for the Globe and Mail.
	 * 
	 * @param jpgFileName File name of the front page image.
	 * @param fullPathName Full path name for the front page image.
	 * @param session The current Hibernate persistence context.
	 * @return Boolean indicating whether the file should be moved or not.
	 */

	boolean gandmImageHandler(String jpgFileName, String fullPathName, Session session) {

		String sep = System.getProperty("file.separator");

		// Is this an A1 page image?
		String a1target = "-a001";
		String a1targetBc = "-s001";
		if ((jpgFileName.toLowerCase().indexOf(a1target)>=0) || (jpgFileName.toLowerCase().indexOf(a1targetBc)>=0)) {
			//frame.addJLog(eventLog("Found G&M A1 image "+jpg_file_name));

			File c = new File(fullPathName);

			try {
				List<SourcesDao> results = SourcesDao.getItemBySource("Globe and Mail", session);
				if (results.size() == 1) {
					BigDecimal sourceRsn = results.get(0).getRsn();   // rsn column from sources

					// Get date from file name
					String dateString = jpgFileName.substring(11,19);
					LocalDate localDate = DateUtil.localDateFromDdMmYyyy(dateString);

					// Delete previous source image records
					// commented out because there are two front pages, a1 and bc section a1
					// nii.deleteSourceA1(source, itemDate);

					// Determine location in directory structure based on date
					String binaryDir = binaryRootHelper(localDate);
					String dirTargetName = binaryRoot + sep + binaryDir;
					if (!binaryDir.equalsIgnoreCase("")) {
						// Move the file to the new location in directory structure
						File fileTarget = new File(dirTargetName + sep + jpgFileName);

						try {
							copyFile(c, fileTarget);
							
							// Get image dimensions
							long width = 0;
							long height = 0;
							
							ImageDimensions id = getImageDimensions(c);
							width = id.width;
							height = id.height;

							String wwwTargetName = wwwBinaryRoot + sep + binaryDir + sep;
							NewsItemImagesDao niiRecord = NewsItemFactory.createNewsItemImage(sourceRsn, wwwTargetName, jpgFileName, width, height);
							
			        		session.beginTransaction();
			        		session.persist(niiRecord);
			        		session.getTransaction().commit();
			        		
						} catch (IOException ex) {
							decoratedError(INDENT1, "Copying from " + c + " to " + fileTarget, ex);
						}

					}
				}
			} catch (Exception ex) {
				//frame.addJLog(eventLog("DailyFunctions.gandmImage(): A1 image (source) error: "+ex.getMessage()));
			} 
		}

		return true; // move file
	}
	
	// handle the Infomart image zip file
	private boolean infomartImages(String zipFileName, String fullFileName, Session session) {

		try {

			String sep = System.getProperty("file.separator");
			boolean failure = false;

			// Only import if the corresponding fms file has been imported already	
			String fmsFile = zipFileName.substring(0,zipFileName.length() - 3) + "fms";
			List<FilesImportedDao> imported = FilesImportedDao.getFilesImportedByFileName(fmsFile, session);
			
			boolean targetNotImported = imported.size() == 0;
			if (targetNotImported) { // no .fms file... was it a .cor file?
				fmsFile = zipFileName.substring(0,zipFileName.length() - 3) + "cor";
				imported = FilesImportedDao.getFilesImportedByFileName(fmsFile, session);
				targetNotImported = imported.size() == 0;
			}

			if (targetNotImported) {

				String zipTarget = System.getProperty("java.io.tmpdir") + zipFileName.substring(0,zipFileName.length() - 4) + sep;
				File zipDir = new File(zipTarget);

				try {

					InputStream in = new BufferedInputStream(new FileInputStream(fullFileName));
					ZipInputStream zin = new ZipInputStream(in);
					ZipEntry zentry;

					// Extract the images to a temporary folder
					//frame.addJLog(eventLog("DailyFunctions.infomartImages(): Unzip "+zip_file_name));
					boolean dirok = zipDir.mkdir();
					if (dirok) {
						while((zentry = zin.getNextEntry()) != null) {
							//if (false) frame.addJLog(eventLog("unzipping " + zipTarget + zentry.getName()));
							unzip(zin, zipTarget + zentry.getName());
						}
					} else {
						failure = true;
						//frame.addJLog(eventLog("DailyFunctions.infomartImages(): Could not create unzip folder "+zipTarget), true);
					}

					zin.close();

				} catch (Exception ex) {
					failure = true;
					//frame.addJLog(eventLog("DailyFunctions.infomartImages(): Zip file error "+zip_file_name+": "+ex.toString()), true);
				}

				if (!failure) {

					Calendar calendar = Calendar.getInstance();

					// Process each image
					for (File c : zipDir.listFiles()) {

						String fileName = c.getName();

						// Is this image referenced in the FMS file? If so there will already be a news_item_image record for it
						List<NewsItemImagesDao> niiResults = NewsItemImagesDao.getImageRecordsByFileName(fileName, fmsFile, session);
						boolean imageExists = niiResults.size() > 0;
						
						if (imageExists) {
							Date itemDate = null;
							NewsItemImagesDao niiRecord = niiResults.get(0);
							
							//frame.addJLog(eventLog("Found image record for "+c.getName()));

							BigDecimal niRsn = niiResults.get(0).getItemRsn();
							List<NewsItemsDao> niResults = NewsItemsDao.getItemByRsn(niRsn, session);
							
							if (niResults.size() == 1) {
								itemDate = niResults.get(0).getItemDate();
							}

							failure = false;

							// Determine location in directory structure based on date
							String binaryDir = binaryRootHelper(itemDate);
							String dirTargetName = binaryRoot + binaryDir;
							if (binaryDir.equalsIgnoreCase("")) {
								failure = true;
							}

							// Move the file to the new location in directory structure
							if (!failure) {

								File fileTarget = new File(dirTargetName+sep + fileName);

								try {
									copyFile(c, fileTarget);
								} catch (IOException ex) {
									failure = true;
									//frame.addJLog(eventLog("DailyFunctions.infomartImages(): File move error: "+ex.getMessage()));
								}
							}

							// Get image dimensions
							long width = 0;
							long height = 0;
							if (!failure) {
								ImageDimensions id = getImageDimensions(c);
								width = id.width;
								height = id.height;
							}

							// Move the THUMBNAIL to the new location in directory structure
							if (!failure) {

								int p = fileName.lastIndexOf('.');
								if (p>0) {

									String thumbName = fileName.substring(0, p) + "-thumb" + fileName.substring(p);
									File thumb = new File(zipTarget + thumbName);

									if (thumb.exists()) {

										File thumbFileTarget = new File(dirTargetName + sep + thumbName);

										try {
											copyFile(thumb, thumbFileTarget);
										} catch (Exception ex) {
											failure = true;
											//frame.addJLog(eventLog("DailyFunctions.infomartImages(): Thumbnail file move error: "+ex.getMessage()));
										}
									}
								}
							}

							if (!failure) {
								String wwwTargetName = wwwBinaryRoot + binaryDir + sep;
								niiRecord.setBinaryPath(wwwTargetName);
								niiRecord.setWidth(BigDecimal.valueOf(width));
								niiRecord.setHeight(BigDecimal.valueOf(height));
								
				        		session.beginTransaction();
				        		session.persist(niiRecord);
				        		session.getTransaction().commit();
							}

						} else {

							// Not referenced in the FMS file. Is this an A1 page image?
							boolean a1image = false;

							String a1target = zipFileName.substring(0,zipFileName.length() - 4) + "_page_A1_" + zipFileName.substring(4,12) + "_320.jpg";
							if (fileName.equalsIgnoreCase(a1target)) a1image = true;
							for (int i=2;i<=9;i++) {
								// versioned file?
								a1target = zipFileName.substring(0,zipFileName.length() - 4) + "_page_A1_" + i + "_" + zipFileName.substring(4,12) + "_320.jpg";
								if (fileName.equalsIgnoreCase(a1target)) a1image = true;
							}

							if (a1image) {
								//frame.addJLog(eventLog("Found A1 image "+file_name));

								//select s.rsn from tno.news_items n, sources s where s.source = n.source and n.importedfrom = ?
								//ResultSet niRS = ni.selectSourceByImport(fmsFile);
								try {
									if (true) { //niRS.next()) {
										long source = 1L; //niRS.getLong(1);   // rsn column from sources

										// Get date from file name
										String dateString = fileName.substring(4,12);
										java.text.DateFormat formatter = new SimpleDateFormat("yyyyMMdd");
										java.util.Date tempItemDate = formatter.parse(dateString);
										java.sql.Date itemDate = new java.sql.Date(tempItemDate.getTime());		

										// Delete previous A1 image records
										//nii.deleteSourceA1(source, itemDate);

										failure = false;

										// Determine location in directory structure based on date
										String binaryDir = binaryRootHelper(itemDate);
										String dirTargetName = binaryRoot + binaryDir;
										if (binaryDir.equalsIgnoreCase("")) {
											failure = true;
										}

										// Move the file to the new location in directory structure
										if (!failure) {

											File fileTarget = new File(dirTargetName + sep + fileName);

											try {
												copyFile(c, fileTarget);
											} catch (IOException ex) {
												failure = true;
												//frame.addJLog(eventLog("DailyFunctions.infomartImages(): A1 file (source) move error: "+ex.getMessage()));
											}
										}

										// Get image dimensions
										long width = 0;
										long height = 0;
										if (!failure) {
											ImageDimensions id = getImageDimensions(c);
											width = id.width;
											height = id.height;
										}

										if (!failure) {
											String wwwTargetName = wwwBinaryRoot + binaryDir + sep;
											//nii.insertSourceA1(source, itemDate, wwwTargetName, fileName, width, height);
											BigDecimal temp = BigDecimal.valueOf(1L);
											NewsItemImagesDao niiRecord = NewsItemFactory.createNewsItemImage(temp, wwwTargetName, fileName, width, height);
											
							        		session.beginTransaction();
							        		session.persist(niiRecord);
							        		session.getTransaction().commit();
										}

									}
								} catch (ParseException ex) {
									//frame.addJLog(eventLog("DailyFunctions.infomartImages(): A1 image (source) error: "+ex.getMessage()));
								}
							}
						}
					}

					//nii.updateProcessed(fmsFile);
					//nii.deleteUnprocessed();
				}

				return true; // move file

			}

			return false; // don't move file

		} catch (Exception ex) {
			//frame.addJLog(eventLog("DailyFunctions.infomartImages(): unknown error: "+ex.getMessage()));
			return false;
		}
	}
		
	/**
	 * Version of binaryRootHelper that takes a java.util.Date object as its single parameter instead of a 
	 * java.time.LocalDate object. This method overloads the LocalDate version and performs a translation from
	 * Date to LocalDate before calling the standard version of binaryRootHelper.
	 * 
	 * @param date The date to use when constructing the local root directory name.
	 * @return The target directory name.
	 */
		
	private String binaryRootHelper(Date date) {
		
		LocalDate itemLocalDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return binaryRootHelper(itemLocalDate);
	}
	
	/**
	 * Performs the following three steps:
	 * <ol>
	 * <li>Determines correct image destination in binary root folder based on date</li>
	 * <li>Creates path if necessary</li>
	 * <li>Returns path if exists or created successfully. returns blank otherwise</li>
	 * </ol>
	 * 
	 * @param localDate A date from which the image directory name is calculated.
	 * @return The target directory name.
	 */
	private String binaryRootHelper(LocalDate localDate) {

		String sep = System.getProperty("file.separator");
		int itemYear = localDate.getYear();
		int itemMonth = localDate.getMonth().getValue();
		int itemDay = localDate.getDayOfMonth();
		
		String dirTargetName = String.format("%04d%s%02d%s%02d", itemYear, sep, itemMonth, sep, itemDay);
		//String wwwTargetName = frame.getWWWBinaryRoot()+itemYear+sep+itemMonthString+sep+itemDayString+sep;

		File dirTarget = new File(binaryRoot + sep + dirTargetName);
		if (!dirTarget.isDirectory()) {
			try {
				if (!(dirTarget.mkdirs())) {
					IOException e = new IOException("Could not create directory " + binaryRoot + dirTargetName);
					decoratedError(INDENT1, "Creating new image directory.", e);
					dirTargetName = "";
				}
			} catch (Exception ex) {
				decoratedError(INDENT1, "Creating new image directory.", ex);
				dirTargetName = "";
			}
		}

		return dirTargetName;
	}
	
	/**
	 * Copies the contents of sourceFile to destinationFile.
	 * 
	 * @param sourcefile The file to read from.
	 * @param destinationfile the file to write to.
	 * @throws IOException
	 */
	
	private void copyFile(File sourceFile, File destinationFile) throws IOException {		
		if (destinationFile.exists()) {
			destinationFile.delete();
		}

		FileInputStream source = new FileInputStream(sourceFile);
		FileOutputStream destination = new FileOutputStream(destinationFile);
		byte[] buffer = new byte[1024];
		int bytes_read = 0;

		while (bytes_read != -1) {
			bytes_read = source.read(buffer);
			if (bytes_read != -1)
				destination.write(buffer, 0, bytes_read);
		}

		source.close();
		destination.close();
	}
	
	/**
	 * Uses Java's ImageIO library to calculate the width and height of the image identified by the <code>image</code> parameter.
	 * The results are returned as an instance of the inner class <code>ImageDimensions</code>.
	 * 
	 * @param image An abstract representation of the image file to be examined for dimensions.
	 * @return The width and height of the image contained in an <code>ImageDimensions</code> object.
	 */
	
	private ImageDimensions getImageDimensions(File image) {
		int width = 0;
		int height = 0;

		try {
			ImageInputStream in = ImageIO.createImageInputStream(image);
			try {
				final Iterator readers = ImageIO.getImageReaders(in);
				if (readers.hasNext()) {
					ImageReader reader = (ImageReader) readers.next();
					try {
						reader.setInput(in);
						width = reader.getWidth(0);
						height = reader.getHeight(0);
					} finally {
						reader.dispose();
					}
				}
			} finally {
				if (in != null) in.close();
			}
		} catch (IOException ex) { ; }

		return new ImageDimensions(width, height);
	}
	
	private static void unzip(ZipInputStream zin, String s) throws IOException {
		FileOutputStream out = new FileOutputStream(s, true);
		byte [] b = new byte[512];
		int len = 0;
		while ( (len=zin.read(b))!= -1 ) {
			out.write(b,0,len);
		}
		out.close();
	}
	
	/**
	 * Inner class to represent the width and height of an image.
	 * 
	 * @author StuartM
	 */
	
	private class ImageDimensions {
		public long width;
		public long height;
		
		private ImageDimensions(long w, long h) {
			width = w;
			height = h;
		}
	}
}