package ca.bc.gov.tno.jorel2.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.SourcesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Provides the functionality necessary to register TNO front page images for the products of various publishers.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

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

	private boolean gandmImageHandler(String jpgFileName, String fullPathName, Session session) {

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

					boolean failure = false;

					// Determine location in directory structure based on date
					String binaryDir = binaryRootHelper(localDate);
					String dirTargetName = binaryRoot + binaryDir;
					if (binaryDir.equalsIgnoreCase("")) {
						failure = true;
					}

					// Move the file to the new location in directory structure
					if (!failure) {

						File fileTarget = new File(dirTargetName + sep + jpgFileName);

						try {
							copyFile(c, fileTarget);
						} catch (IOException ex) {
							failure = true;
							decoratedError(INDENT1, "Copying ??? ", ex);
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
						//nii.insertSourceA1(sourceRsn, itemDate, wwwTargetName, jpgFileName, width, height);
					}

				}
			} catch (Exception ex) {
				//frame.addJLog(eventLog("DailyFunctions.gandmImage(): A1 image (source) error: "+ex.getMessage()));
			} 
		}

		return true; // move file
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
		int itemMonth = localDate.getMonth().getValue() + 1;
		int itemDay = localDate.getDayOfMonth();
		
		String dirTargetName = String.format("%02d%s%02d%s%d", itemYear, sep, itemMonth, sep, itemDay);
		//String wwwTargetName = frame.getWWWBinaryRoot()+itemYear+sep+itemMonthString+sep+itemDayString+sep;

		File dirTarget = new File(binaryRoot + dirTargetName);
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
