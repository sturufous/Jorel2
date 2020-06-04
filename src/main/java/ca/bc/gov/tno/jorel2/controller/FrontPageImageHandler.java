package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import ca.bc.gov.tno.jorel2.model.SourcePaperImagesDao;
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
	
	/** Platform dependent file separator character */
	private String sep = System.getProperty("file.separator");
	
	/**
	 * Manages the distribution and registration of a front page image for the Globe and Mail.
	 * 
	 * @param jpgFileName File name of the front page image.
	 * @param fullPathName Full path name for the front page image.
	 * @param session The current Hibernate persistence context.
	 * @return Boolean indicating whether the file should be moved or not.
	 */

	boolean gandmImageHandler(String jpgFileName, String fullPathName, Session session) {

		boolean success = true;
		
		// Is this an A1 page image?
		String a1target = "-a001";
		String a1targetBc = "-s001";
		if ((jpgFileName.toLowerCase().indexOf(a1target)>=0) || (jpgFileName.toLowerCase().indexOf(a1targetBc)>=0)) {
			//frame.addJLog(eventLog("Found G&M A1 image "+jpg_file_name));

			File c = new File(fullPathName);

			try {
				List<SourcesDao> results = SourcesDao.getItemBySource(GANDM_ID_STRING, session);
				if (results.size() == 1) {
					BigDecimal sourceRsn = results.get(0).getRsn();   // rsn column from sources

					// Get date from file name
					String dateString = jpgFileName.substring(11,19);
					LocalDate localDate = DateUtil.localDateFromDdMmYyyy(dateString);

					// Determine location in directory structure based on date
					String binaryDir = binaryRootHelper(localDate);
					String dirTargetName = binaryRoot + sep + binaryDir;
					if (!binaryDir.equalsIgnoreCase("")) {
						// Move the file to the archive location
						File archiveTarget = new File(dirTargetName + sep + jpgFileName);
						
						try {
							if (copyFile(c, archiveTarget)) {
								ImageDimensions id = getImageDimensions(c);
								String wwwTargetName = wwwBinaryRoot + sep + binaryDir + sep;
								success = createNewsItemImage(sourceRsn, id, wwwTargetName, jpgFileName, c, session);
							}
						} catch (IOException ex) {
							decoratedError(INDENT1, "Copying from " + c + " to " + archiveTarget, ex);
							success = false;
						}
					}
				}
			} catch (Exception ex) {
				decoratedError(INDENT1, "Processing Globe and Mail front page image.", ex);
				success = false;
			} 
		}

		return success; // move file
	}
	
	/**
	 * Manages the distribution and registration of a front page images for Infomart.
     *
	 * @param zipFileName Name of zip file to process.
	 * @param fullFileName Full path name of the zip file.
	 * @param session Current Hibernate persistence context.
	 * @return Whether process is successful.
	 */
	
	boolean infomartImageHandler(String zipFileName, String fullFileName, Session session) {
		
		boolean success = true;
		File importZipFile = new File(fullFileName);

		try {
			// Only import if the corresponding fms file has been imported already	
			if (targetWasImported(zipFileName, session)) {
				String zipTarget = System.getProperty("java.io.tmpdir") + zipFileName.substring(0,zipFileName.length() - 4) + sep;
				File zipDir = new File(zipTarget);
				String fmsFile = zipFileName.substring(0, zipFileName.length() -3 ) + "fms";
				
				if (extractArchiveToTempDir(fullFileName, zipDir, zipTarget, session)) {

					// Process each image
					for (File c : zipDir.listFiles()) {

						String fileName = c.getName();

						// Is this image referenced in the FMS file? If so there will already be a news_item_image record for it
				        List<Object[]> niiResults = NewsItemImagesDao.getImageRecordsByFileNameAndSource(fileName, fmsFile, session);
						
						if (niiResults.size() > 0) { // Image record exists
							success = updateExistingNiiImage(niiResults, fileName, c, session);
						} else {
							success = createNewNiiImage(zipFileName, fileName, fmsFile, c, session);
						}
					}
					
					importZipFile.delete();
					NewsItemImagesDao.updateProcessedByImportFile(fmsFile, session);
				} else {
					importZipFile.delete();
					success = false;
				}
			} else {
				importZipFile.delete();
				success = false;
			}
		} catch (Exception ex) {
			decoratedError(INDENT1, "Processing Infomart Image", ex);
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Manages the distribution and registration of a front page images for the van24. Partially implemented and untested. This functionality 
	 * is not currently needed as the Van24 is no longer available.
     *
	 * @param pdfFileName Name of zip file to process.
	 * @param fullFileName Full path name of the zip file.
	 * @param session Current Hibernate persistence context.
	 * @return Whether process is successful.
	 */
	
	boolean van24ImageHandler(String pdfFileName, String fullFileName, Session session) {
		
		boolean success = true;
		
		String a1target = "24v";
		if (pdfFileName.toLowerCase().indexOf(a1target) >= 0) {
			File c = new File(fullFileName);

			try {
				List<SourcesDao> results = SourcesDao.getItemBySource(VAN24_ID_STRING, session);
				if (results.size() == 1) {
					//BigDecimal sourceRsn = results.get(0).getRsn();   // rsn column from sources

					// Get date from file name
					int curYear = LocalDate.now().getYear();
					String dateString = pdfFileName.substring(9,13) + curYear;
					LocalDate itemDate = DateUtil.localDateFromMmDdYYYY(dateString);

					String jpgFileName = pdfFileName.substring(0, pdfFileName.length() - 3) + "jpg";

					// Delete previous source image records
					//nii.deleteSourceA1(sourceRsn, itemDate);

					// Determine location in directory structure based on date
					String binaryDir = binaryRootHelper(itemDate);
					
					if (movePdfImageTo(binaryDir, fullFileName, jpgFileName, c)) {
						//ImageDimensions id = getImageDimensions(c);
						//String wwwTargetName = wwwBinaryRoot + binaryDir + sep;
						//success = createNewsItemImage(sourceRsn, id, wwwTargetName, jpgFileName, session);
						//nii.insertSourceA1(sourceRsn, itemDate, wwwTargetName, jpgFileName, width, height);
					}

				}
			} catch (Exception ex) {
				decoratedError(INDENT1, "Processing Van24 front page image.", ex);
			}
		}

		return success;
	}
	
	/**
	 * Copies an unzipped image file from the temporary directory to the binary root directory matching the date extracted 
	 * from the zip file name (e.g. 2020/06/01). Also updates the stored width and height of the image.
	 * 
	 * @param niiResults Array containing NewsItemImagesDao record to be updated.
	 * @param fileName Name of the image, from the zip file, that's currently being processed.
	 * @param tempPath Full path name of the temp location for the image file.
	 * @param session Current Hibernate persistence context.
	 * @return Whether the file was successfully copied and the image record updated.
	 */
	
	private boolean updateExistingNiiImage(List<Object[]> niiResults, String fileName, File tempPath, Session session) {
		
		boolean success = true;
		
		try {
	        Object[] entityPair = niiResults.get(0);
			NewsItemImagesDao niiRecord = (NewsItemImagesDao) entityPair[0];
			NewsItemsDao niRecord = (NewsItemsDao) entityPair[1];
			
			Date itemDate = niRecord.getItemDate();
			String binaryDir = binaryRootHelper(itemDate);
	
			if (copyFileToTargetDir(fileName, tempPath, binaryDir)) {
				String wwwTargetName = wwwBinaryRoot + binaryDir + sep;
				ImageDimensions id = getImageDimensions(tempPath);
				success = updateNewsItemImage(niiRecord, id, wwwTargetName, session);
			}
		} catch (Exception e) {
			decoratedError(INDENT1, "Updating NewsItem Image.", e);
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Copies an unzipped image file from the temporary directory to the binary root directory matching the date extracted 
	 * from the zip file name (e.g. 2020/06/01). Also creates a new NewsItemImagesDao record representing the current image.
	 * 
	 * @param zipFileName Name of the zip file that was extracted earlier.
	 * @param fileName Name of the image, from the zip file, that's currently being processed.
	 * @param fmsFile Name of the fms file that relates to this zip file.
	 * @param archivePath Abstract representation of the full path name of archive location for the image file.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the file was successfully copied and the image record created.
	 */
	
	private boolean createNewNiiImage(String zipFileName, String fileName, String fmsFile, File archivePath, Session session) {
		
		boolean success = true;
		
		try {
			// Not referenced in the FMS file. Is this an A1 page image?
			if (isAnA1Image(zipFileName, fileName)) {
				//frame.addJLog(eventLog("Found A1 image "+file_name));
	
				List<Object[]> results = SourcesDao.getRsnByImportFilename(fmsFile, session);
				if (results.size() > 0) { // Retrieved the sources.rsn successfully
			        Object[] entityPair = results.get(0);
					NewsItemsDao ni = (NewsItemsDao) entityPair[0];
					BigDecimal sourceRsn = ni.getRsn();
	
					// Get date from file name
					String dateString = fileName.substring(4,12);
					LocalDate localDate = DateUtil.localDateFromYyyyMmDd(dateString);
	
					// Delete previous A1 image records
					SourcePaperImagesDao.deleteByRsnAndDate(sourceRsn, localDate, session);
	
					String binaryDir = binaryRootHelper(localDate);
	
					if (copyFileToTargetDir(fileName, archivePath, binaryDir)) {
						String wwwTargetName = wwwBinaryRoot + sep + binaryDir + sep;
						ImageDimensions id = getImageDimensions(archivePath);
						success = createNewsItemImage(sourceRsn, id, wwwTargetName, fileName, archivePath, session);
					}
				} else {
					success = false;
				}
			} else {
				success = false;
			}
		} catch (Exception e) {
			decoratedError(INDENT1, "Creating NewsItemImage record.", e);
			success = false;
		}

		return success;
	}
	
	/**
	 * Determines whether a zip file has already been imported by looking for a record in the FILES_IMPORTED table. If there is no record
	 * for and fms file with the same name as the zip file then check for the existence of a cor file.
	 * 
	 * @param zipFileName The name of a zip file in the import directory for the current event.
	 * @param session The current Hibernate persistence context.
	 * @return Whether there is a record in FILES_IMPORTED that matches the name of this zip file.
	 */
	private boolean targetWasImported(String zipFileName, Session session) {
		
		boolean wasImported = false;
		
		String fmsFile = zipFileName.substring(0, zipFileName.length() - 3) + "fms";
		List<FilesImportedDao> imported = FilesImportedDao.getFilesImportedByFileName(fmsFile, session);
		
		wasImported = imported.size() > 0;
		if (!wasImported) { // no .fms file... was it a .cor file?
			fmsFile = zipFileName.substring(0,zipFileName.length() - 3) + "cor";
			imported = FilesImportedDao.getFilesImportedByFileName(fmsFile, session);
			wasImported = imported.size() > 0;
		}

		return wasImported;
	}
	
	/**
	 * If the binaryDir parameter is not empty, this method copies the file <code>c</code> to the binary root directory identified by binaryDir.
	 * 
	 * @param fileName The name of the destination file (minus any path information).
	 * @param tempPath The <code>File</code> object representing the file to copy to the destination.
	 * @param binaryDir The YYYY/MM/DD formatted directory name into which the file should be copied.
	 * @return Whether the operation was successful.
	 */
	
	private boolean copyFileToTargetDir(String fileName, File tempPath, String binaryDir) {
		
		boolean success = true;
		
		// Determine location in directory structure based on date
		if (binaryDir.equalsIgnoreCase("")) {
			success = false;
		}
	
		// Move the file to the new location in directory structure
		if (success) {
	
			File fileTarget = new File(binaryRoot + sep + binaryDir + sep + fileName);
			
			try {
				success = copyFile(tempPath, fileTarget);
			} catch (IOException ex) {
				decoratedError(INDENT1, "Copying image file to target directory.", ex);
				success = false;
				//frame.addJLog(eventLog("DailyFunctions.infomartImages(): File move error: "+ex.getMessage()));
			}
		}
		
		return success;
	}

	/**
	 * Extracts the contents of the zip file identified by the <code>fullFileName</code> parameter into the temporary directory relating to
	 * the <code>zipdir</code> parameter. 
	 * 
	 * @param fullFileName The full filename of the zip file in the import directory.
	 * @param tempZipDir The full path of the temporary directory into which the zip file is extracted.
	 * @param zipTarget The full path of the temporary directory as a string.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the unarchive process was successful.
	 */
	
	private boolean extractArchiveToTempDir(String fullFileName, File tempZipDir, String zipTarget, Session session) {
		
		boolean success = true;
		
		try {
			InputStream in = new BufferedInputStream(new FileInputStream(fullFileName));
			ZipInputStream zin = new ZipInputStream(in);
			ZipEntry zentry;

			// Extract the images to a temporary folder
			boolean dirok = tempZipDir.mkdir();
			if (dirok) {
				while((zentry = zin.getNextEntry()) != null) {
					unzip(zin, zipTarget + zentry.getName());
				}
			} else {
				IOException e = new IOException("Unable to create the directory: " + tempZipDir);
				decoratedError(INDENT1, "Creating temporary unzip directory.", e);
				success = false;
			}

			zin.close();
			
		} catch (Exception ex) {
			decoratedError(INDENT1, "Extracting archive to temp directory.", ex);
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Takes an existing NewsItemImageDao object, updates the binaryPath field and image dimensions, and persists to the database.
	 * 
	 * @param niiRecord The existing NewsItemImageDao object.
	 * @param id An ImageDimension object containing the current images width and height.
	 * @param wwwTargetName The path of the wwwBinaryRoot directory.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the update was successful.
	 */
	
	private boolean updateNewsItemImage(NewsItemImagesDao niiRecord, ImageDimensions id, String wwwTargetName, Session session) {
		
		boolean success = true;
		
		try {
			niiRecord.setBinaryPath(wwwTargetName);
			niiRecord.setWidth(BigDecimal.valueOf(id.width));
			niiRecord.setHeight(BigDecimal.valueOf(id.height));
			
			session.beginTransaction();
			session.persist(niiRecord);
			session.getTransaction().commit();
			success = true;
		} catch (Exception e) {
			decoratedError(INDENT1, "Updating news item image.", e);
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Creates a new NewsItemImagesDao object with a primary key matching the key of the corresponding NewsItemsDao record. Also
	 * copies the image to its web viewable location.
	 * 
	 * @param sourceRsn The rsn of the corresponding NewsItemsDao record.
	 * @param id The ImageDimensions object containing the width and height of the 
	 * @param wwwTargetName The path of the wwwBinaryRoot directory.
	 * @param fileName The file name of the associated image.
	 * @param c Abstract representation of the archive path name.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the creation of the record was successful.
	 */
	
	private boolean createNewsItemImage(BigDecimal sourceRsn, ImageDimensions id, String wwwTargetName, String fileName, File c, Session session) {
		
		boolean success = true;
		
		try {
			File avTargetDir = new File(wwwTargetName);
			File avTargetFile = new File(wwwTargetName + sep + fileName);
			
			// Create av target directories if they don't already exist
			try {
				if (!avTargetDir.exists()) {
					avTargetDir.mkdirs(); 
				}
			} catch (Exception err) { 
				throw new IOException("Creating av target directory " + avTargetDir, err);
			}
			
			// Copy the source front page image to the av directory for web access and create the news_item_images record
			if(copyFile(c, avTargetFile)) {
				NewsItemImagesDao niiRecord = NewsItemFactory.createNewsItemImage(sourceRsn, wwwTargetName, fileName, id.width, id.height);
				
				session.beginTransaction();
				session.persist(niiRecord);
				session.getTransaction().commit();
				
				success = true;
				c.delete();
			} else {
				IOException e = new IOException("Unable to copy " + c + " to " + avTargetFile);
				decoratedError(INDENT2, "Copying front page image from home directory to avTarget.", e);
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Creating news item image.", e);
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Determines, from the file name, whether the image represented is an A1 (section A page one) image.
	 * 
	 * @param zipFileName The name of the zip file from which the image was extracted.
	 * @param fileName The name of the image being processed.
	 * @return Whether the image represented by <code>fileName</code> is an A1 image.
	 */
	
	private boolean isAnA1Image(String zipFileName, String fileName) {
		boolean a1image = false;

		String a1target = zipFileName.substring(0,zipFileName.length() - 4) + "_page_A1_" + zipFileName.substring(4,12) + "_320.jpg";
		if (fileName.equalsIgnoreCase(a1target)) a1image = true;
		for (int i=2;i<=9;i++) {
			// versioned file?
			a1target = zipFileName.substring(0,zipFileName.length() - 4) + "_page_A1_" + i + "_" + zipFileName.substring(4,12) + "_320.jpg";
			if (fileName.equalsIgnoreCase(a1target)) a1image = true;
		}
		
		return a1image;
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
		
		LocalDate itemLocalDate = Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
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
	 * @param sourceFile The file to read from.
	 * @param destinationFile the file to write to.
	 * @throws IOException On error copying the file.
	 * @return Whether the operation was successful.
	 */
	
	private boolean copyFile(File sourceFile, File destinationFile) throws IOException {	
		
		boolean success = true;
		
		FileInputStream source = new FileInputStream(sourceFile);
		FileOutputStream destination = new FileOutputStream(destinationFile);
		
		try {
			if (destinationFile.exists()) {
				destinationFile.delete();
			}
	
			byte[] buffer = new byte[1024];
			int bytes_read = 0;
	
			while (bytes_read != -1) {
				bytes_read = source.read(buffer);
				if (bytes_read != -1)
					destination.write(buffer, 0, bytes_read);
			}
			
			success = true;
		} catch (Exception e) {
			decoratedError(INDENT1, "Copying " + sourceFile + " to " + destinationFile, e);
			success = false;
		}

		source.close();
		destination.close();
		
		return success;
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
				final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
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
	
	/**
	 * Unzips the file connected to the ZipImputStream to the temporary directory pointed to by tempPath.
	 * 
	 * @param zin The ZipInputStream from which to read the contents of the current zipped file.
	 * @param tempPath The full path name of the file to which the contents of <code>zin</code> should be written to. 
	 * @throws IOException On error reading from the zip file or writing to the temp directory.
	 */
	
	private static void unzip(ZipInputStream zin, String tempPath) throws IOException {
		
		FileOutputStream out = new FileOutputStream(tempPath, true);
		byte [] b = new byte[512];
		int len = 0;
		while ( (len = zin.read(b)) != -1 ) {
			out.write(b,0,len);
		}
		out.close();
	}
	
	/**
	 * Partially implemented and untested. This functionality not currently needed as Van24 is no longer in use.
	 * 
	 * @param binaryDir Not used
	 * @param fullFileName Not used
	 * @param jpgFileName Not used
	 * @param c Not used
	 * @return Not used
	 */
	private boolean movePdfImageTo(String binaryDir, String fullFileName, String jpgFileName, File c) {
		
		boolean success = true;
		
		String dirTargetName = binaryRoot + sep + binaryDir;
		if (binaryDir.equalsIgnoreCase("")) {
			success = false;
		}

		// Move the file to the new location in directory structure + convert to jpg
		if (success) {
			try {
				pdfToJpg(fullFileName, dirTargetName + sep + jpgFileName, c);
			} catch (Exception ex) {
				decoratedError(INDENT1, "Extracting Van24 front page image from PDF.", ex);
				success = false;
			}
		}
		
		return success;
	}
	
	/**
	 * Extract the contents of a pdf file and save them as a Jpg. Partially implemented and untested. This functionality not currently needed.
	 * 
	 * @param pdfFileName The full path name of the pdf file to process.
	 * @param jpgFileName The full path name of the jpg file to create.
	 * @param fileObj An abstract representation of the file to be converted.
	 */
	private void pdfToJpg(String pdfFileName, String jpgFileName, File fileObj) {
		
		//PDDocument document = PDDocument.load(fileObj);
		
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
