package ca.bc.gov.tno.jorel2.controller;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import oracle.sql.BLOB;
import oracle.sql.CLOB;

import org.hibernate.Session;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.title.*;
import org.jfree.data.category.*;
import org.jfree.data.general.*;
import org.jfree.data.time.*;
import org.jfree.chart.labels.*;
import org.jfree.ui.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

@SuppressWarnings({"deprecation", "unused"})
public class AnalysisHandler extends Jorel2Root {
	
	// Time based constants
	private static final int DAY = 1;
	private static final int TRIDAY = 2;
	private static final int WEEK = 3;
	private static final int BIWEEK = 4;
	private static final int MONTH = 5;
	private static final int YEAR = 6;

	private String message="";
	private String graph_detail="";
	private String map_detail="";
	private String map_count_detail="";

	long analysis_rsn;
	long user_rsn;
	long current_period;
	int image_size;
	int font_size;
	boolean auto_run;

	public AnalysisHandler(long analysis_rsn, long user_rsn, long current_period){
		this(analysis_rsn, user_rsn, current_period, 1024, 24, false);
	}
	public AnalysisHandler(long analysis_rsn, long user_rsn, long current_period, boolean auto_run){
		this(analysis_rsn, user_rsn, current_period, 1024, 24, auto_run);
	}
	public AnalysisHandler(long analysis_rsn, long user_rsn, long current_period, int image_size, int font_size){
		this(analysis_rsn, user_rsn, current_period, image_size, font_size, false);
	}
	public AnalysisHandler(long analysis_rsn, long user_rsn, long current_period, int image_size, int font_size, boolean auto_run){
		this.analysis_rsn = analysis_rsn;
		this.user_rsn = user_rsn;
		this.current_period = current_period;
		this.image_size = image_size;
		this.font_size = font_size;
		this.auto_run = auto_run;
	}

	@SuppressWarnings("unused")
	public void draw(boolean use_auto_run_if_available, boolean set_auto_run_in_saved_analysis, Session session){

		// if we are not autorunning
		if (!auto_run) {
			// and an autorun saved graph exists
			if (report_auto_run(session)) {
				//then do nothing
				return;
			}
		}

		boolean error = false;
		String min_date = "";
		String max_date = "";
		Calendar min_cal_date = null;
		Calendar max_cal_date = null;
		boolean summarize_by_section = false;

		// Initialize the fields from the analysis table
		String name = "";
		String title = "";
		String xml = "";

		/* ------------------------------------------------------------------
		 * Get the media analysis report record to be created
		 */
		ResultSet rs = null;
		Document doc = null;
		try{
			rs = DbUtil.runSql("select * from analysis where rsn = " + analysis_rsn + " and user_rsn = " + user_rsn, session);
			if(rs.next()){
				name = rs.getString(3);
				title = name;

				// For some of the form data (most actually), the form data is
				//    in an XML string
				Clob cl = rs.getClob(8);
				long l = cl.length();
				if(l > 0){
					xml = cl.getSubString(1, (int) l); // scares me here too

					// All data for the form must come from the XML data, so
					// ... parse the XML
					doc = parseXML(xml);
					if(doc == null)
						error = true;
				}
			}
		} catch(Exception err){ error=true;}
		try{ if(rs != null) rs.close(); } catch(SQLException err){;}
		if(error){
			errorImage("Parse Error!", session);
			return;
		}

		/*
		 * My Report rsns
		 */
		String report_rsn = "0";
		String report_section_rsn = "0";

		String searchType = getValueByTagName(doc, "a_searchtype");
		if (searchType.equalsIgnoreCase("report"))
		{
			String sum_by_section = getValueByTagName(doc,"b_sum_by_section");
			if(sum_by_section.equalsIgnoreCase("1"))
			{
				summarize_by_section = true;
			}
		}
		String a_report = getValueByTagName(doc,"a_report");
		int p = a_report.indexOf(".");
		if(p > 0) {
			report_rsn = a_report.substring(0,p);
			report_section_rsn = a_report.substring(p + 1);
		}

		/*
		 * Start extracting data from the parsed XML document
		 */
		String reporttype = getValueByTagName(doc,"b_reporttype");
		String over_time = getValueByTagName(doc,"b_over_time");
		String sub_zero = getValueByTagName(doc,"b_sub_zero");
		String line_width = getValueByTagName(doc,"c_line_width");
		String prefer_pie = getValueByTagName(doc,"c_prefer_pie");
		String prefer_3d = getValueByTagName(doc,"c_prefer_3d");

		String tonePoolString = getValueByTagName(doc,"b_tonepool");
		String tonePoolName = getTonePoolName(tonePoolString, session);

		String yaxislabel = "";
		String xaxislabel = "";
		String xaxis_over_time = "";
		if (reporttype.equalsIgnoreCase("a")) {
			yaxislabel = "Count";
			xaxislabel = "Source Type";
		} else if (reporttype.equalsIgnoreCase("b")) {
			yaxislabel = "Count";
			xaxislabel = "Source";
		} else if (reporttype.equalsIgnoreCase("c")) {
			yaxislabel = "Count";
			xaxislabel = "Byline/Program";

		} else if (reporttype.equalsIgnoreCase("d")) {
			yaxislabel = "Circ./Reach/Pg Views";
			xaxislabel = "Source Type";
		} else if (reporttype.equalsIgnoreCase("e")) {
			yaxislabel = "Circ./Reach/Pg Views";
			xaxislabel = "Source";
		} else if (reporttype.equalsIgnoreCase("f")) {
			yaxislabel = "Circ./Reach/Pg Views";
			xaxislabel = "Byline/Program";

		} else if (reporttype.equalsIgnoreCase("g")) {
			yaxislabel = "Earned Media";
			xaxislabel = "Source Type";
		} else if (reporttype.equalsIgnoreCase("h")) {
			yaxislabel = "Earned Media";
			xaxislabel = "Source";
		} else if (reporttype.equalsIgnoreCase("i")) {
			yaxislabel = "Earned Media";
			xaxislabel = "Byline/Program";

		} else if (reporttype.equalsIgnoreCase("j")) {
			yaxislabel = "Tone";
			xaxislabel = "Source Type";
		} else if (reporttype.equalsIgnoreCase("k")) {
			yaxislabel = "Tone";
			xaxislabel = "Source";
		} else if (reporttype.equalsIgnoreCase("l")) {
			yaxislabel = "Tone";
			xaxislabel = "Byline/Program";
		} else if (reporttype.equalsIgnoreCase("m")) {
			yaxislabel = "Tone";
			xaxislabel = "Report Section";

			summarize_by_section = false;

		} else if (reporttype.equalsIgnoreCase("n")) {
			yaxislabel = "Klout";
			xaxislabel = "Source Type";
		} else if (reporttype.equalsIgnoreCase("o")) {
			yaxislabel = "Klout";
			xaxislabel = "Source";
		} else if (reporttype.equalsIgnoreCase("p")) {
			yaxislabel = "Klout";
			xaxislabel = "Byline/Program";
		}

		if(over_time.equalsIgnoreCase("1")){
			xaxis_over_time = " (Over Time)";

			summarize_by_section = false;
		}
		int xaxis_over_time_groupby = DAY;

		/* ------------------------------------------------------------------
		 * Create a SQL command to perform the search
		 *   the sql will have everything in it, all the correct tables
		 *   required, the sort order required
		 */
		long s1 = (new java.util.Date()).getTime();

		String mainSQL = "";
		String sqlDescription = "";
		if(doc!=null){
			Properties pm = new Properties();
			mainSQL = createSQL(pm, doc, xaxislabel, yaxislabel, session);
			if(mainSQL.length() < 2)
				error = true;

			// For none time based grouping, sort it
			if (xaxis_over_time.equalsIgnoreCase("")) {
				if (xaxislabel.equalsIgnoreCase("Source")) {
					mainSQL = mainSQL+" order by 3";
				} else if (xaxislabel.equalsIgnoreCase("Source Type")) {
					mainSQL = mainSQL+" order by 4";
				} else if (xaxislabel.equalsIgnoreCase("Byline/Program")) {
					mainSQL = mainSQL+" order by 5";
				}
			}

			sqlDescription = pm.getProperty("sqlDescription").replaceAll("~","\n");

			/* ------------------------------------------------------------------
			 * Get the max and min date for the query
			 */
			if (! xaxis_over_time.equalsIgnoreCase("")) {
				min_date = getMinDate(pm, session);
				max_date = getMaxDate(pm, session);
				if(over_time.equalsIgnoreCase("1")){
					title = title + " ("+min_date+" to "+max_date+")";
				}

				min_cal_date = DateUtil.calendarDate(min_date);
				max_cal_date = DateUtil.calendarDate(max_date);

				int dateDiff = AnalysisHandler.getDaysBetween(min_cal_date, max_cal_date);
				if(dateDiff > (365 * 2)){
					xaxis_over_time_groupby=YEAR;
				} else if (dateDiff > (7*14)) {
					xaxis_over_time_groupby=MONTH;
				} else if (dateDiff > (7*7)) {
					xaxis_over_time_groupby=BIWEEK;
				} else if (dateDiff > 7*3) {
					xaxis_over_time_groupby=WEEK;
				} else if (dateDiff > 7) {
					xaxis_over_time_groupby=TRIDAY;
				}
			}
		} else{
			error=true;
			decoratedTrace(INDENT2, "Analysis.draw3(): doc is null");
		}
		long s2 = (new java.util.Date()).getTime();

		/* ------------------------------------------------------------------
		 * Try out the SQL
		 */
		if(!error){
			try{
				rs = DbUtil.runSqlFlags(mainSQL, session);
			} catch(Exception err){
				decoratedError(INDENT0, "Analysis.draw3():", err);
				error=true;
			}
		}
		if(error){
			errorImage("SQL Error! " + mainSQL, session);
			decoratedTrace(INDENT2, "Analysis.draw3() mainSQL: " + mainSQL, session);
			return;
		}
		long s3 = (new java.util.Date()).getTime();

		/*
		 * Initialize the series, they all go in the Hashtable for later retrieval
		 */
		LinkedHashMap<String,Object> reportHashtable=new LinkedHashMap<String,Object>();
		LinkedHashMap<String,Object> countHashtable=new LinkedHashMap<String,Object>();
		LinkedHashMap<String,Object> reportCsvHashtable=new LinkedHashMap<String,Object>(); // hashtable for the downloadable report - slightly different in the way it stores tone values
		LinkedHashMap<String,Object> countCsvHashtable=new LinkedHashMap<String,Object>(); // tone hashtable for the downloadable report

		/* ------------------------------------------------------------------
		 * Loop thru all the news item records
		 */
		try{
			while(rs.next()) {
				long item = rs.getLong(1);
				String itemDate = rs.getString(2);
				String source=rs.getString(3);
				String source_type = rs.getString(4);
				String series=rs.getString(5);
				if(series == null) series = "";
				String day_of_week = rs.getString(6);
				String itemTitle = rs.getString(7);
				long number1 = rs.getLong(8);
				long number2 = rs.getLong(9);
				long length = rs.getLong(10);
				long tone = -99; // if tone is null, then set to -99
				if(rs.getObject(11) != null) tone = Math.round(rs.getFloat(11));

				// get the Section name
				String section_name = getReportSection(item, report_rsn, session);

				String graph_record = "\t" + itemDate + "\t" + itemTitle + "\t" + source_type + "\t" + source + "\t" + series;
				if(summarize_by_section)
				{
					graph_record = graph_record + "\t" + section_name;
				}
				graph_record = graph_record + "\n";

				String xaxis = "";
				if(xaxislabel.equalsIgnoreCase("Source Type")) {							// ***** source type
					xaxis = source_type;
					if(xaxis.equalsIgnoreCase("Transcript"))
					{
						// the transcript will be for some other type (either a TV item or Radio item)
						// see if we can figure out the other type and then use it for this item
					}
				} else if (xaxislabel.equalsIgnoreCase("Source")) {
					xaxis = source;
				} else if (xaxislabel.equalsIgnoreCase("Byline/Program")) {
					xaxis = series;
				} else if (xaxislabel.equalsIgnoreCase("Report Section")) {
					xaxis = getReportSection(item, report_rsn, session);
				}

				/*
				 * The value is what we are summing up, reach, earned media, tone or a simple count
				 * the only value that makes no sense to sum up is tone.  Tone requires an average.
				 */
				long value = 1;
				if(yaxislabel.equalsIgnoreCase("Circ./Reach/Pg Views")) {					// ***** reach
					value = getReach(xaxislabel,source_type,source,series,day_of_week,number2, session);

				} else if(yaxislabel.equalsIgnoreCase("Earned Media")) {					// ***** earned media
					float adValue = getAdValue(xaxislabel, source_type, source, series, day_of_week, number1, number2, length, session);
					long weight = 5;
					if (tone == 5)
						weight = 100;
					else if(tone == 4)
						weight = 90;
					else if(tone == 3)
						weight = 80;
					else if(tone == 2)
						weight = 70;
					else if(tone == 1)
						weight = 60;
					else if((tone == 0) || (tone == -99))									// for now; no tone = neutral
						weight = 50;
					else if(tone == -1)
						weight = 40;
					else if(tone == -2)
						weight = 30;
					else if(tone == -3)
						weight = 20;
					else if(tone == -4)
						weight = 10;
					else if(tone == -5)
						weight = 5;
					value = Math.round(adValue * (float)weight / 100.0);											// formula for earned media!!!

				} else if(yaxislabel.equalsIgnoreCase("Tone")) {							// ***** tone
					value = tone;								// get the tone for this news item

				} else if(yaxislabel.equalsIgnoreCase("Klout")) {							// ***** klout
					value = getKlout(number1, source_type);

				} else {																	// ***** count
					value = 1;

				}

				/*
				 * keep the totals as we loop thru the records
				 */
				if (xaxis_over_time.equalsIgnoreCase("")) {
					try {
						if ( (! xaxislabel.equalsIgnoreCase("Report Section")) & (! prefer_pie.equalsIgnoreCase("1")) )
						{
							if(summarize_by_section) 
							{
								xaxis = section_name + "~" + xaxis;
							}
						}
						x_sum(reportHashtable, countHashtable, xaxis, yaxislabel, value);
						x_sum(reportCsvHashtable, countCsvHashtable, xaxis, yaxislabel, value); // for csv download
					} catch (Exception err) {
						throw new Exception("x_sum() " + err.toString());
					}
				} else {

					/*
					 * Time based chart...tone is special
					 */
					if(yaxislabel.startsWith("Tone")){
						if (value == -99) {
							value = 0;
						} else {
							// these are simply running counts used to compute the average
							incrementTimeSeries(countHashtable, xaxis, xaxis_over_time_groupby, itemDate, 1, min_cal_date, max_cal_date, new Long(0)); 	
							incrementTimeSeries(countCsvHashtable, xaxis, xaxis_over_time_groupby, itemDate, 1, min_cal_date, max_cal_date, new Long(0));			
						}
					}
					incrementTimeSeries(reportHashtable, xaxis, xaxis_over_time_groupby, itemDate, value, min_cal_date, max_cal_date, new Long(0));
					incrementTimeSeries(reportCsvHashtable, xaxis, xaxis_over_time_groupby, itemDate, value, min_cal_date, max_cal_date, null); // for csv download
				}

				/*
				 * update the detail string used when a user down loads the tab delim file
				 */
				if(yaxislabel.startsWith("Tone") && (value == -99)) {
					graph_record = "?" + graph_record;
				} else {
					graph_record = "" + value + graph_record;
				}
				graph_detail = graph_detail + graph_record;				
			}
		} catch(Exception err) {
			name="draw3() generation error: " + err.toString();
			decoratedError(INDENT0, "draw3() generation error", err);
			error=true;
		}
		try{ if(rs != null) rs.close(); } catch(SQLException err) {;}
		if(error) {
			errorImage(name, session);
			return;
		}
		long s4 = (new java.util.Date()).getTime();

		String xaxis = xaxislabel + xaxis_over_time;

		map_detail = reportHashtable.toString();
		map_count_detail = countHashtable.toString();

		/* ------------------------------------------------------------------
		 * Create the graph
		 */
		JFreeChart jfc = null;
		try{
			boolean prefer3d = prefer_3d.equalsIgnoreCase("1");
			if (xaxis_over_time.equalsIgnoreCase("")) {
				if ((prefer_pie.equalsIgnoreCase("1")) && (!yaxislabel.startsWith("Tone"))) {
					jfc = simple_pie_chart(title, xaxis, yaxislabel, prefer3d, reportHashtable, countHashtable);
				} else {
					jfc = simple_bar_chart(title, xaxis, yaxislabel, prefer3d, reportHashtable, countHashtable);
				}
			} else {
				jfc = simple_time_series_chart(title, xaxis, yaxislabel, line_width, prefer3d, reportHashtable, countHashtable, xaxis_over_time_groupby );
			}			
		} catch(Exception err){
			errorImage("draw3() creation error: " + err.toString(), session);
			decoratedError(INDENT0, "draw3() creation error.", err);
			return;
		}

		try{
			long s5 = (new java.util.Date()).getTime();
			report_save_graph(jfc, set_auto_run_in_saved_analysis, session);
			long s6 = (new java.util.Date()).getTime();
			message = message + " 1: " + (s2-s1) + " 2:" + (s3-s2) + " 3:" + (s4-s3) + " 4:" + (s5-s4) + " 5:" + (s6-s5);
		} catch(Exception err){
			errorImage("draw3() save error: " + err.toString(), session);
			decoratedError(INDENT0, "draw3() save error.", err);
			return;
		}

		try{
			reportCsvSave(reportCsvHashtable, countCsvHashtable, xaxis_over_time, name, yaxislabel, xaxis, sqlDescription, tonePoolName, sub_zero, session);
		} catch(Exception err){
			errorImage("draw3() csv error: " + err.toString(), session);
			decoratedError(INDENT0, "draw3() csv error.", err);
			return;
		}
	}

	private void x_sum(Map<String,Object> the_hashtable, Map<String,Object> the_counthashtable, String xaxis, String yaxis, long value) {
		Long the_increment_value;
		if (yaxis.startsWith("Tone") && (value == -99)) {
			the_increment_value = null;
		} else {
			the_increment_value = new Long(value);
		}

		if (the_hashtable.containsKey(xaxis)){
			Long cs=(Long)the_hashtable.get(xaxis);
			if (cs!=null) {
				if (the_increment_value!=null) {
					the_hashtable.put(xaxis,new Long(cs + the_increment_value));
				}
			} else {
				the_hashtable.put(xaxis, the_increment_value);
			}
		} else {
			the_hashtable.put(xaxis, the_increment_value);
		}

		// for tone, we need a Tone average... so keep a separate count
		if(yaxis.startsWith("Tone") && (value != -99))
		{
			if(the_counthashtable.containsKey(xaxis)){
				Long cs=(Long)the_counthashtable.get(xaxis);
				the_counthashtable.put(xaxis,new Long(cs+1));                 				
			} else {
				the_counthashtable.put(xaxis, new Long(1));
			}
		}
	}


	private JFreeChart simple_bar_chart(String title, String xaxislabel, String yaxislabel, boolean prefer3d, Map<String,Object> the_hashtable, Map<String,Object> the_counthashtable ) throws Exception {
		long sa = (new java.util.Date()).getTime();
		DefaultCategoryDataset cds=new DefaultCategoryDataset();

		boolean stacked_chart = false;
		Map<String, Object> treeMap = new TreeMap<String, Object>(the_hashtable);

		Iterator<Map.Entry<String, Object>> it = treeMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> pairs = it.next();
			String key=pairs.getKey();
			Long value=(Long)pairs.getValue();

			String a = "";
			String b = key;

			int pos = key.indexOf("~");
			if(pos != -1)
			{
				stacked_chart = true;

				b = key.substring(0, pos);
				a = key.substring(pos+1);
			}

			//if (key.equalsIgnoreCase("")) key = "(none)";

			if(yaxislabel.startsWith("Tone"))
			{
				if (value!=null) {
					if(the_counthashtable.containsKey(key))
					{
						Long c=(Long)the_counthashtable.get(key);
						if(value!=0) value = value / c;
						if(value<-5) value = new Long(-5);
						if(value> 5) value =  new Long(5);
					}
					if (value==0) {
						cds.addValue(0,a,b);
					} else {
						cds.addValue(value,a,b);
					}
				} else {
					cds.addValue(0, a, b);
				}
			} else {
				if (value!=null) {
					cds.addValue(value,a,b);
				} else {
					cds.addValue(0, a, b);
				}
			}
		}

		long sb = (new java.util.Date()).getTime();
		JFreeChart jfc=null;
		if (prefer3d)
		{
			if(stacked_chart)
			{
				if(yaxislabel.startsWith("Tone"))
				{
					jfc=ChartFactory.createBarChart3D(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,true,false,false);
				}
				else
				{
					jfc=ChartFactory.createStackedBarChart3D(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,true,false,false);
				}
			}
			else
			{
				jfc=ChartFactory.createBarChart3D(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,false,false,false);
			}
		} 
		else 
		{
			if(stacked_chart)
			{
				if(yaxislabel.startsWith("Tone"))
				{
					jfc=ChartFactory.createBarChart(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,true,false,false);
				}
				else
				{
					jfc=ChartFactory.createStackedBarChart(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,true,false,false);
				}
			}
			else
			{
				jfc=ChartFactory.createBarChart(title,xaxislabel,yaxislabel,cds,PlotOrientation.VERTICAL,false,false,false);
			}
		}
		long sc = (new java.util.Date()).getTime();

		try {
			if(yaxislabel.startsWith("Tone"))
			{
				CategoryPlot plot=(CategoryPlot)jfc.getPlot();
				plot.setOrientation(PlotOrientation.HORIZONTAL);

				TextTitle tt=(TextTitle)jfc.getTitle();
				tt.setFont(tt.getFont().deriveFont((float)font_size+3));

				CategoryAxis axis=(CategoryAxis)plot.getDomainAxis();
				axis.setLabelFont(axis.getLabelFont().deriveFont((float)font_size));
				axis.setTickLabelFont(axis.getTickLabelFont().deriveFont((float)font_size-1));
				
				plot.setBackgroundPaint(Color.getHSBColor(0f, 0f, 0.9f));
				plot.setDomainGridlinesVisible(true);  
				plot.setRangeGridlinesVisible(true);  
				plot.setRangeGridlinePaint(Color.gray);  
				plot.setDomainGridlinePaint(Color.gray);

				final BarRenderer renderer = (BarRenderer) plot.getRenderer();
				renderer.setDrawBarOutline(true);
				renderer.setShadowVisible(false);
				renderer.setItemMargin(0.0);
				renderer.setMaximumBarWidth(.05);
				renderer.setMinimumBarLength(2.0);

				NumberAxis rangeAxis=(NumberAxis)plot.getRangeAxis();
				rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
				rangeAxis.setLabelFont(rangeAxis.getLabelFont().deriveFont((float)font_size));
				rangeAxis.setTickLabelFont(rangeAxis.getTickLabelFont().deriveFont((float)font_size-1));

				rangeAxis.setRange(-5.1,5.1);

				/*
				 * 	ToneRenderer classes are defined at the bottom of this source file
				 */
				if (!prefer3d) {
					final Marker start = new ValueMarker(0.0);
					start.setPaint(Color.gray);
					plot.addRangeMarker(start, Layer.BACKGROUND);

					if(stacked_chart)
					{
						StackedToneRenderer trenderer = new StackedToneRenderer();
						plot.setRenderer(trenderer);
					}
					else
					{
						ToneRenderer trenderer = new ToneRenderer();
						plot.setRenderer(trenderer);
					}
				} else {
					if(stacked_chart)
					{
						StackedToneRenderer trenderer = new StackedToneRenderer();
						plot.setRenderer(trenderer);
					}
					else
					{
						ToneRenderer trenderer = new ToneRenderer();
						plot.setRenderer(trenderer);
					}
				}
			
				LegendTitle chart_title = jfc.getLegend();
				if (chart_title!=null) {
					chart_title.setItemFont(chart_title.getItemFont().deriveFont((float)font_size));
				}
			}
			else
			{
				CategoryPlot plot=(CategoryPlot)jfc.getPlot();
				plot.setOrientation(PlotOrientation.HORIZONTAL);

				TextTitle tt=(TextTitle)jfc.getTitle();
				tt.setFont(tt.getFont().deriveFont((float)font_size+3));

				CategoryAxis axis=(CategoryAxis)plot.getDomainAxis();
				axis.setLabelFont(axis.getLabelFont().deriveFont((float)font_size));
				axis.setTickLabelFont(axis.getTickLabelFont().deriveFont((float)font_size-1));

				plot.setBackgroundPaint(Color.getHSBColor(0f, 0f, 0.9f));
				plot.setDomainGridlinesVisible(true);  
				plot.setRangeGridlinesVisible(true);  
				plot.setRangeGridlinePaint(Color.gray);  
				plot.setDomainGridlinePaint(Color.gray);

				NumberAxis rangeAxis=(NumberAxis)plot.getRangeAxis();
				rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
				rangeAxis.setLabelFont(rangeAxis.getLabelFont().deriveFont((float)font_size));
				rangeAxis.setTickLabelFont(rangeAxis.getTickLabelFont().deriveFont((float)font_size-1));
				if(yaxislabel.startsWith("Tone")){
					rangeAxis.setRange(-5.1,5.1);

					if (!prefer3d) {
						final Marker start = new ValueMarker(0.0);
						start.setPaint(Color.gray);
						plot.addRangeMarker(start, Layer.BACKGROUND);

						ToneRenderer trenderer = new ToneRenderer();
						plot.setRenderer(trenderer);
					} else {
						ToneRenderer3D trenderer = new ToneRenderer3D();
						plot.setRenderer(trenderer);
					}
				}
				if(yaxislabel.startsWith("Earned")){
					NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("ENGLISH", "CANADA"));
					currencyFormat.setMaximumFractionDigits(0);

					NumberFormat nf10 = new DecimalFormat("$###,###,###,###");

					rangeAxis.setNumberFormatOverride(nf10);
				}

				// disable bar outlines...
				final BarRenderer renderer = (BarRenderer) plot.getRenderer();
				renderer.setBarPainter(new StandardBarPainter());
				renderer.setDrawBarOutline(true);
				renderer.setShadowVisible(false);
				renderer.setMaximumBarWidth(.05);
				if(yaxislabel.startsWith("Tone")){
					renderer.setMinimumBarLength(2.0);
				}

				LegendTitle chart_title = jfc.getLegend();
				if (chart_title!=null) {
					chart_title.setItemFont(chart_title.getItemFont().deriveFont((float)font_size));
				}
			}

		} catch (Exception ex) { throw new Exception("simple_bar_chart: "+ex.toString()); }
		long sd = (new java.util.Date()).getTime();
		message = message+" [a: "+(sb-sa)+" b: "+(sc-sb)+" c: "+(sd-sc)+"] ";
		return jfc;
	}

	private JFreeChart simple_pie_chart(String title, String xaxislabel, String yaxislabel, boolean prefer3d, Map<String,Object> the_hashtable, Map<String,Object> the_counthashtable ) throws Exception{
		long sa = (new java.util.Date()).getTime();
		DefaultPieDataset cds=new DefaultPieDataset();
		Iterator<Map.Entry<String, Object>> it = the_hashtable.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> pairs = it.next();
			String key=pairs.getKey();
			Long value=(Long)pairs.getValue();

			if (value!=null) {
				cds.setValue(key, (float)value);
			} else {
				cds.setValue(key, 0f);
			}
			System.out.println(key+" "+value);
		}

		long sb = (new java.util.Date()).getTime();
		JFreeChart jfc=null;
		if (prefer3d) {
			jfc=ChartFactory.createPieChart3D(title,cds,false,false,false);
		} else {
			jfc=ChartFactory.createPieChart(title,cds,false,false,false);
		}
		long sc = (new java.util.Date()).getTime();

		try {

			PiePlot plot = (PiePlot) jfc.getPlot();
			if(yaxislabel.startsWith("Earned")){
				PieSectionLabelGenerator generator = new StandardPieSectionLabelGenerator("{0} ({1})", new DecimalFormat("$0"), new DecimalFormat("%0.00"));
				plot.setLabelGenerator(generator); 
			} else {
				PieSectionLabelGenerator generator = new StandardPieSectionLabelGenerator("{0} ({1})", new DecimalFormat("0"), new DecimalFormat("%0.00"));
				plot.setLabelGenerator(generator); 
			}

			TextTitle tt=(TextTitle)jfc.getTitle();
			tt.setFont(tt.getFont().deriveFont((float)font_size+3));

			plot.setBackgroundPaint(Color.getHSBColor(0f, 0f, 0.9f));
			plot.setShadowXOffset(0);
			plot.setShadowYOffset(0);

			plot.setLabelFont(plot.getLabelFont().deriveFont((float)font_size));
			plot.setInteriorGap(0);
			plot.setCircular(true);

			if (prefer3d) {
				plot.setForegroundAlpha(0.5f);
			}

			LegendTitle chart_title = jfc.getLegend();
			if (chart_title!=null) {
				chart_title.setItemFont(chart_title.getItemFont().deriveFont((float)font_size));
			}

		} catch (Exception ex) { throw new Exception("simple_pie_chart: "+ex.toString()); }
		long sd = (new java.util.Date()).getTime();
		message = message+" [a: "+(sb-sa)+" b: "+(sc-sb)+" c: "+(sd-sc)+"] ";
		return jfc;
	}

	private JFreeChart simple_time_series_chart(String title, String xaxislabel, String yaxislabel, String line_width, boolean prefer3d, Map<String,Object> the_hashtable, Map<String,Object> the_counthashtable, int groupby ) throws Exception {
		long sa = (new java.util.Date()).getTime();
		/*
		 * Average tone over time, the main hashtable contains the sum of all the tone values
		 * averageTones replaces the values in the_hashtable!!!!
		 */
		if(yaxislabel.startsWith("Tone")){
			averageTones(the_hashtable,the_counthashtable);
		}

		TimeSeriesCollection dataset=new TimeSeriesCollection();

		Iterator<Map.Entry<String, Object>> it = the_hashtable.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> pairs = it.next();
			TimeSeries s1=(TimeSeries)pairs.getValue();
			dataset.addSeries(s1);
		}
		long sb = (new java.util.Date()).getTime();
		JFreeChart jfc=ChartFactory.createTimeSeriesChart(title,xaxislabel,yaxislabel,dataset,true,false,false);
		long sc = (new java.util.Date()).getTime();

		float linewidth = 0;
		try {
			linewidth = Float.parseFloat(line_width);
		} catch (Exception ex) { ; }
		if ((linewidth<=0) || linewidth>100) linewidth = 3;

		try {
			XYPlot plot=jfc.getXYPlot();

			TextTitle tt=(TextTitle)jfc.getTitle();
			tt.setFont(tt.getFont().deriveFont((float)font_size+3));

			DateAxis axis=(DateAxis)plot.getDomainAxis();
			axis.setLabelFont(axis.getLabelFont().deriveFont((float)font_size));
			axis.setTickLabelFont(axis.getTickLabelFont().deriveFont((float)font_size-1));

			plot.setBackgroundPaint(Color.getHSBColor(0f, 0f, 0.9f));
			plot.setDomainGridlinesVisible(true);  
			plot.setRangeGridlinesVisible(true);  
			plot.setRangeGridlinePaint(Color.gray);  
			plot.setDomainGridlinePaint(Color.gray);

			final XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
			int hashtable_size = the_hashtable.size();
			for (int i = 0; i < hashtable_size; i++) { 
				renderer.setSeriesStroke(i, new BasicStroke( linewidth ));	
				renderer.setSeriesShapesVisible(i, true);
			}

			NumberAxis rangeAxis=(NumberAxis)plot.getRangeAxis();
			rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			rangeAxis.setLabelFont(rangeAxis.getLabelFont().deriveFont((float)font_size));
			rangeAxis.setTickLabelFont(rangeAxis.getTickLabelFont().deriveFont((float)font_size-1));

			if(yaxislabel.startsWith("Tone")){
				rangeAxis.setRange(-5.1,5.1);
			}
			if(yaxislabel.startsWith("Earned")){
				NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("ENGLISH", "CANADA"));
				currencyFormat.setMaximumFractionDigits(0);

				NumberFormat nf10 = new DecimalFormat("$###,###,###,###");

				rangeAxis.setNumberFormatOverride(nf10);
			}

			final TickUnits standardUnits = new TickUnits();
			if (groupby==YEAR) {
				standardUnits.add( new DateTickUnit(DateTickUnitType.YEAR, 1, new SimpleDateFormat("yyyy")) );
			} else if (groupby==MONTH) {
				standardUnits.add( new DateTickUnit(DateTickUnitType.MONTH, 1, new SimpleDateFormat("M-yyyy")) );
			} else if (groupby==BIWEEK) {
				standardUnits.add( new DateTickUnit(DateTickUnitType.DAY, 14, new SimpleDateFormat("d-M-yyyy")) );
			} else if (groupby==WEEK) {
				standardUnits.add( new DateTickUnit(DateTickUnitType.DAY, 7, new SimpleDateFormat("d-M-yyyy")) );
			} else if (groupby==TRIDAY) {
				standardUnits.add( new DateTickUnit(DateTickUnitType.DAY, 3, new SimpleDateFormat("d-M")) );
			} else {
				standardUnits.add( new DateTickUnit(DateTickUnitType.DAY, 1, new SimpleDateFormat("d-M")) );
			}
			axis.setStandardTickUnits(standardUnits);

			LegendTitle chart_title = jfc.getLegend();
			if (chart_title!=null) {
				chart_title.setItemFont(chart_title.getItemFont().deriveFont((float)font_size));
			}
		} catch (Exception ex) { throw new Exception("simple_time_series_chart: "+ex.toString()); }
		long sd = (new java.util.Date()).getTime();
		message = message+" [a: "+(sb-sa)+" b: "+(sc-sb)+" c: "+(sd-sc)+"] ";
		return jfc;
	}

	public void report_display_graph(OutputStream out, Session session) {
		ResultSet rs=null;
		try{
			String sqlString="select data from analysis_graphs where analysis_rsn = "+analysis_rsn+" and image_size = "+image_size+" and font_size = "+font_size;
			rs = DbUtil.runSql(sqlString, session);
			if(rs.next()){
				BLOB bl=(BLOB)rs.getBlob(1);

				InputStream is = bl.getBinaryStream();
				BufferedImage buffer = ImageIO.read(is);

				//response.setContentType("image/gif");
				ImageIO.write(buffer,"gif",out);
				out.flush();
				out.close();
			}
		} catch(Exception err){;}
		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
	}

	public void report_write_file(String randomStr, Session session) {
		ResultSet rs=null;
		try{
			String sqlString="select data from analysis_graphs where analysis_rsn = " + analysis_rsn + " and image_size = " + image_size + " and font_size = " + font_size;
			rs = DbUtil.runSql(sqlString, session);
			if(rs.next()){
				BLOB bl=(BLOB)rs.getBlob(1);

				InputStream is = bl.getBinaryStream();
				BufferedImage buffer = ImageIO.read(is);

				String tempdir = System.getProperty("java.io.tmpdir");
				if ( !(tempdir.endsWith("/") || tempdir.endsWith("\\")) )
					tempdir = tempdir + System.getProperty("file.separator");

				File file = new File(tempdir + analysis_rsn+"_" + image_size+"_" + font_size+"_" + randomStr + ".gif");

				ImageIO.write(buffer, "gif", file);
			}
		} catch(Exception err){;}
		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
	}

	private void report_save_graph(JFreeChart jfc, boolean set_auto_run, Session session) {
		/*
		 * Delete and then add the record for the newly created analysis
		 */
		ResultSet rs = null;
		ResultSet rs2 = null;
		long rsn = 0;

		try{
			rs2 = DbUtil.runSql("select tno.next_rsn.nextval from dual", session);
			if (rs2.next()) rsn = rs2.getLong(1); else rsn = 0;
			rs2.close();

			if (rsn != 0) {
				report_delete_graph(false, session); // delete any previous saved graphs
				DbUtil.runUpdateSql("insert into analysis_graphs (rsn, analysis_rsn, image_size, font_size, created, data, was_auto_run) values (" + rsn + "," + analysis_rsn + "," + image_size + "," + font_size+", current_date, empty_blob()," + ((set_auto_run) ? "1" : "0") + ")", session);
			}
		} catch(Exception err) {;}
		try{ if(rs != null) rs.close(); } catch(SQLException err) {;}

		if (rsn != 0) {
			/*
			 * Update the ANALYSIS_GRAPHS record with the new data
			 */
			try{
				String sqlString = "select data from analysis_graphs where rsn = " + rsn + " for update";
				rs = DbUtil.runSql(sqlString, session);
				if(rs.next()) {
					long sa = (new java.util.Date()).getTime();
					BLOB bl = (BLOB)rs.getBlob(1);
					OutputStream outstream = bl.getBinaryOutputStream();

					long sb = (new java.util.Date()).getTime();
					BufferedImage buffer = jfc.createBufferedImage(image_size, image_size);
					long sc = (new java.util.Date()).getTime();

					ImageIO.setUseCache(false);
					ImageIO.write(buffer, "gif", outstream);
					outstream.close();
					long sd = (new java.util.Date()).getTime();
					message = message + " [i: " + (sb - sa) + " ii: " + (sc-sb) + " iii: " + (sd - sc) + "] ";
				}
			} catch(Exception err) {;}
		}
	}

	public void report_delete_graph(boolean all, Session session) {
		/*
		 * Delete and the record for the saved analysis graph
		 */
		try{
			if (all) {
				DbUtil.runUpdateSql("delete from analysis_graphs where analysis_rsn = " + analysis_rsn, session);
			} else {
				DbUtil.runUpdateSql("delete from analysis_graphs where analysis_rsn = " + analysis_rsn + " and image_size = " + image_size + " and font_size = " + font_size, session);
			}
		} catch(Exception err){;}
	}

	public boolean report_auto_run(Session session) {
		ResultSet rs = null;
		boolean was_auto_run = false;
		try{
			String sqlString="select rsn from analysis_graphs where analysis_rsn = " + analysis_rsn + " and image_size = " + image_size + " and font_size = " + font_size + " and was_auto_run = 1";
			rs = DbUtil.runSql(sqlString, session);
			if(rs.next()){
				was_auto_run = true;
			}
		} catch(Exception err){;}
		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		return was_auto_run;
	}


	//
	// For Tones, there are 2 series of values, one is the sum of all the tones
	//     the other is the count of tone values.  This method creates an average
	//     and replaces the series of values used to keep count
	private void averageTones(Map<String,Object> sumHashtable, Map<String,Object> countHashtable){
		Iterator<Map.Entry<String, Object>> it = sumHashtable.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> pairs = it.next();
			String key=pairs.getKey();
			TimeSeries s1=(TimeSeries)pairs.getValue();

			if(countHashtable.containsKey(key)){
				TimeSeries s2=(TimeSeries)countHashtable.get(key);

				int ic=s1.getItemCount();
				for(int nr=0;nr<ic;nr++){
					RegularTimePeriod rtp=s1.getTimePeriod(nr);
					Number ssum=(Number)s1.getValue(nr);
					double s=0;
					double c = 0;
					double avg = 0;
					if(ssum!=null) s=ssum.doubleValue();
					if(s==0){
						s1.update(nr,ssum);
					} else{
						Number scount=null;
						try{
							scount=(Number)s2.getValue(rtp);
						} catch(IndexOutOfBoundsException b){;}

						if(scount!=null){
							c = scount.doubleValue();
							if(c > 0) {
								avg=(long)((s/c)*10.0)/10.0;
								if(avg<-5)	avg=-5;
								if(avg>5)	avg=5;
							}
						}
						s1.update(nr,new Double(avg));
					}
				}
			}
		}
		return;
	}

	private void errorImage(String msg, Session session) {
		try{
			DefaultCategoryDataset cds = new DefaultCategoryDataset();
			JFreeChart jfc=ChartFactory.createBarChart("Error! " + msg, "", "", cds, PlotOrientation.VERTICAL, false, false, false);
			jfc.setBackgroundPaint(Color.red);

			report_save_graph(jfc, false, session);

			return;
		} catch(Exception err){;}
		return;
	}

	private void reportCsvSave(Map<String,Object> the_hashtable, Map<String,Object> the_counthashtable,String xaxis_over_time,String name,String yaxis,String xaxis,String sqlDescription,String tonePoolName,String sub_zero, Session session){

		/* ------------------------------------------------------------------
		 * Save the data in CSV for possible later download
		 */
		StringBuilder csv=new StringBuilder();
		Properties csvp=new Properties();
		int seriescount=0;
		int ic=0;

		csv.append(name + "\n");
		csv.append(yaxis + " vs " + xaxis + "\n");
		csv.append(sqlDescription + "\n");

		if (yaxis.startsWith("Tone") || yaxis.startsWith("Earned Media")) {
			csv.append("Using tone pools: " + tonePoolName+"\n");
		}

		/* ------------------------------------------------------------------
		 * Which type of chart was created (time vs bar chart)
		 */
		try{
			if (xaxis_over_time.equalsIgnoreCase("")) {

				Iterator<Map.Entry<String, Object>> it = the_hashtable.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Object> pairs = it.next();
					String key=pairs.getKey();
					Long cv=(Long)pairs.getValue();
					String sv="?";

					if (cv==null) {
						if (sub_zero.equals("1")) {
							sv = "0";
						} else {
							sv = "";
						}
					} else {
						if(yaxis.startsWith("Tone") && (cv.longValue() == -99)) {
							sv = "0";
						} else {
							sv=""+cv.floatValue();
						}
					}

					csv.append(key+"\t"+sv);

					if(yaxis.startsWith("Tone")){
						if(the_counthashtable.containsKey(key)){
							double count=(Long)the_counthashtable.get(key);
							double avg = 0.0;
							if (cv!=null) {
								double dvalue = cv;
								if (count!=0) 
									avg = (long)((dvalue/count)*10.0)/10.0;
							}
							if(avg<-5) avg = -5;
							if(avg> 5) avg =  5;
							csv.append("\t"+count+"\t"+avg);
						}
					}
					csv.append("\n");
				}
			} else {

				/*
				 * Average tone over time, the main hashtable contains the sum of all the tone values
				 * averageTones replaces the values in the_hashtable!!!!
				 */
				if(yaxis.startsWith("Tone")){
					averageTones(the_hashtable,the_counthashtable);
				}

				Iterator<Map.Entry<String, Object>> it = the_hashtable.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Object> pairs = it.next();
					String key=pairs.getKey();
					TimeSeries s1=(TimeSeries)pairs.getValue();

					ic=s1.getItemCount();

					/*
					 * Header for CSV file
					 */
					if(seriescount==0)
						csvp.put("0","Date\t"+key);
					else
						csvp.put("0",csvp.getProperty("0")+"\t"+key);

					/*
					 * Columns for CSV file
					 */
					for(int nr=0;nr<ic;nr++){
						RegularTimePeriod rtp=s1.getTimePeriod(nr);
						String timePeriodString=rtp.toString();
						if (timePeriodString.startsWith("Week ")) {
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
							timePeriodString = sdf.format(rtp.getStart());
						}
						Number cv=(Number)s1.getValue(nr);
						String sv="?";

						if(cv==null){
							if (sub_zero.equals("1")) {
								sv = "0";
							} else {
								sv = "";
							}
						} else{
							if(yaxis.startsWith("Tone") && (cv.longValue() == -99)) {
								sv = "0";
							} else {
								sv=""+cv.floatValue();
							}
						}

						int r=nr+1;
						if(seriescount==0)
							csvp.put(""+r,""+timePeriodString+"\t"+sv);
						else
							csvp.put(""+r,csvp.getProperty(""+r)+"\t"+sv);
					}
					seriescount++;

				}

				/*
				 * Save the CSV file into a string buffer
				 */
				if(seriescount > 0){
					for(int nr = 0; nr <= ic; nr++){
						csv.append(csvp.getProperty(""+nr)+"\n");
					}
				}
			}
		} catch(Exception err){
			csv.append("Error creating report\n"+err.toString());}

		if(graph_detail.length() > 0){
			csv.append(yaxis + "\n");
			csv.append(graph_detail + "\n");
		}
		if(message.length()>0)
			csv.append(message + "\n");

		/*
		 * Delete and then add the record for the newly created report
		 */
		ResultSet rs=null;
		try{
			DbUtil.runUpdateSql("delete from analysiscsv where rsn = " + analysis_rsn, session);
			DbUtil.runUpdateSql("insert into analysiscsv values (" + analysis_rsn + ", empty_clob())", session);
		} catch(Exception err){;}
		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}

		/*
		 * Update the ANALYSISCSV record with the new data
		 */
		try{
			String sqlString="select data from analysiscsv where rsn = " + analysis_rsn + " for update";
			rs = DbUtil.runSql(sqlString, session);
			if(rs.next()){
				CLOB cl=(CLOB)rs.getClob(1);
				Writer out=cl.getCharacterOutputStream();
				out.write(csv.toString());
				out.close();
			}
		} catch(Exception err){;}

		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		return;
	}

	static public Document parseXML(String xml){
		Document doc=null;
		try{
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			DocumentBuilder db=dbf.newDocumentBuilder();
			doc=db.parse(new InputSource(new StringReader(xml)));
		} catch(Exception err){
			;}
		return doc;
	}

	static public String getValueByTagName(Document doc,String tag){
		String value="";
		if(doc==null) return value;
		try{
			NodeList tagNodeList=doc.getElementsByTagName(tag);
			if(tagNodeList!=null){
				if(tagNodeList.getLength()>0){
					Element tagElement=(Element)tagNodeList.item(0);
					if(tagElement!=null){
						NodeList textTagList=tagElement.getChildNodes();
						if(textTagList!=null){
							if(textTagList.getLength()>0)
								value=((Node)textTagList.item(0)).getNodeValue();
						}
					}
				}
			}
		} catch(Exception err){
			value="";}
		return value;
	}

	private Week whichWeek(Calendar theDate)
	{
		int weekOfDate = theDate.get(Calendar.WEEK_OF_YEAR);
		int monthOfDate = theDate.get(Calendar.MONTH)+1;
		int yearOfDate = theDate.get(Calendar.YEAR);

		// how many weeks in this year?
		Calendar weektest = Calendar.getInstance();
		weektest.set(Calendar.YEAR, 2009);
		weektest.set(Calendar.MONTH, Calendar.DECEMBER);
		weektest.set(Calendar.DAY_OF_MONTH, 28);
		int ordinalDay = weektest.get(Calendar.DAY_OF_YEAR);
		int weekDay = weektest.get(Calendar.DAY_OF_WEEK);
		int weeksThisYear = (int)Math.floor((ordinalDay - weekDay + 10) / 7); // either 52 or 53

		// make sure the end of Dec is in week 52 of that year
		if(weekOfDate == 1)
		{
			if(monthOfDate == 12)
			{
				weekOfDate = weeksThisYear;
			}
		}
		else if (weekOfDate == weeksThisYear)
		{
			// and make sure start of Jan is in week 1 of that year
			if(monthOfDate == 1)
			{
				weekOfDate = 1;
			}
		}
		return new Week(weekOfDate,yearOfDate);
	}

	private void incrementTimeSeries(LinkedHashMap<String,Object> the_hashtable,String the_key,int groupbytype, String itemDate,long value, Calendar min_date, Calendar max_date, Long init_value){
		Calendar date = DateUtil.calendarDate(itemDate);

		if(! the_hashtable.containsKey(the_key)){
			/**** initialization ****/
			if ((groupbytype==DAY) || (groupbytype==TRIDAY)) {
				TimeSeries temp=new TimeSeries(the_key);
				if((min_date != null) && (max_date != null)) {
					Calendar d1=(java.util.Calendar)min_date.clone();
					do{
						Day d=new Day(d1.get(Calendar.DAY_OF_MONTH),d1.get(Calendar.MONTH)+1,d1.get(Calendar.YEAR));
						initializeSeriesValue(temp,d,init_value);

						d1.add(java.util.Calendar.DATE,1);
					} while(d1.equals(max_date) || d1.before(max_date));
				}
				the_hashtable.put(the_key, temp);

			} else if ((groupbytype==WEEK) || (groupbytype==BIWEEK)) {
				TimeSeries temp=new TimeSeries(the_key);
				if((min_date != null) && (max_date != null)) {
					Calendar d1=(java.util.Calendar)min_date.clone();
					Week w=whichWeek(d1);
					Week maxunit=whichWeek(max_date);
					do{
						initializeSeriesValue(temp,w,init_value);

						d1.add(java.util.Calendar.WEEK_OF_YEAR,1);
						w=whichWeek(d1); //new  Week(date.get(Calendar.WEEK_OF_YEAR),date.get(Calendar.YEAR));
					} while(w.compareTo(maxunit)<0);
					initializeSeriesValue(temp,w,init_value);
				}
				the_hashtable.put(the_key, temp);

			} else if (groupbytype==YEAR) {
				TimeSeries temp=new TimeSeries(the_key);
				if((min_date != null) && (max_date != null)) {
					Calendar d1=(java.util.Calendar)min_date.clone();
					Year y=new Year(d1.get(Calendar.YEAR));
					Year maxunit=new Year(max_date.get(Calendar.YEAR));
					do{
						initializeSeriesValue(temp,y,init_value);

						d1.add(java.util.Calendar.YEAR,1);
						y=new Year(d1.get(Calendar.YEAR));
					} while(y.compareTo(maxunit)<0);
					initializeSeriesValue(temp,y,init_value);
				}
				the_hashtable.put(the_key, temp);

			} else {
				TimeSeries temp=new TimeSeries(the_key);
				if((min_date != null) && (max_date != null)) {
					Calendar d1=(java.util.Calendar)min_date.clone();
					Month m=new Month(d1.get(Calendar.MONTH)+1,d1.get(Calendar.YEAR));
					Month maxunit=new Month(max_date.get(Calendar.MONTH)+1,max_date.get(Calendar.YEAR));
					do{
						initializeSeriesValue(temp,m,init_value);

						d1.add(java.util.Calendar.MONTH,1);
						m=new Month(d1.get(Calendar.MONTH)+1,d1.get(Calendar.YEAR));
					} while(m.compareTo(maxunit)<0);
					initializeSeriesValue(temp,m,init_value);
				}
				the_hashtable.put(the_key, temp);	       
			}
			/**** end initialization ****/
		}

		TimeSeries s1=(TimeSeries)the_hashtable.get(the_key);
		if(s1!=null){
			if ((groupbytype==DAY) || (groupbytype==TRIDAY)) {
				Day d=new Day(date.get(Calendar.DAY_OF_MONTH),date.get(Calendar.MONTH)+1,date.get(Calendar.YEAR));
				incrementSeriesValue(s1,d,value);
			} else
				if ((groupbytype==WEEK) || (groupbytype==BIWEEK)) {
					Week w=whichWeek(date); //new  Week(date.get(Calendar.WEEK_OF_YEAR),date.get(Calendar.YEAR));
					incrementSeriesValue(s1,w,value);
				} else
					if(groupbytype==YEAR){
						Year y=new Year(date.get(Calendar.YEAR));
						incrementSeriesValue(s1,y,value);
					} else{ // month
						Month m=new Month(date.get(Calendar.MONTH)+1,date.get(Calendar.YEAR));
						incrementSeriesValue(s1,m,value);
					}
		}
		return;
	}

	private void initializeSeriesValue(TimeSeries s1,RegularTimePeriod r, Long q){
		s1.addOrUpdate(r,q);
	}

	private void incrementSeriesValue(TimeSeries s1,RegularTimePeriod r,long q){
		Number x=(Number)s1.getValue(r);
		Long value=new Long(0);
		if(x==null)
			value=new Long(q);
		else
			value=new Long(x.longValue()+q);
		s1.addOrUpdate(r,value);
		return;
	}

	public String createSQL(Properties pm, Document doc, String xaxis, String yaxis, Session session) {

		String where="";
		String searchType=getValueByTagName(doc,"a_searchtype");

		/*
		 *  Check for a filter query first, the user is using one of their own filters
		 */
		if(searchType.equalsIgnoreCase("filter")){
			String rsn = getValueByTagName(doc, "a_filter");

			String orderBy = "";
			String name = "?";

			Properties filterProperties = new Properties();

			DbUtil.filterSQLWhere(rsn, orderBy, current_period, filterProperties, session);

			if(!filterProperties.isEmpty()){
				where = filterProperties.getProperty("defaultWhere");
				name = filterProperties.getProperty("name");
			}
			where = StringUtil.replacement(where, "c.", "n.");

			pm.put("sqlDescription", "Filter: " + name);

		} else if (searchType.equalsIgnoreCase("folder")) {
			String rsn = getValueByTagName(doc,"a_folder");

			String name="?";

			// load the filter, the SQL where clause, SQL for other files required and the text description of the where clause
			ResultSet rs=null;
			try{
				String sqlString="select folder_name from tno.folder where rsn = " + rsn + " and user_rsn = " + user_rsn;
				rs = DbUtil.runSql(sqlString, session);
				if(rs.next()){
					name=rs.getString(1);
				}
			} catch(SQLException err){
				;}
			try{
				if(rs!=null)
					rs.close();
			} catch(SQLException err){
				;}

			where=" and n.rsn in (select fi.item_rsn from folder f, folder_item fi " +
			"where f.rsn = " + rsn + " and f.rsn = fi.folder_rsn and f.user_rsn = " + user_rsn + ")";

			pm.put("sqlDescription","Folder: "+name);

		} else if (searchType.equalsIgnoreCase("report")) {
			String report_rsn = "0";
			String report_section_rsn = "0";

			String rsn=getValueByTagName(doc,"a_report");
			int p = rsn.indexOf(".");
			if(p > 0) {
				report_rsn = rsn.substring(0,p);
				report_section_rsn = rsn.substring(p+1);
			}

			String name="?";

			// load the report, the SQL where clause, SQL for other files required and the text description of the where clause
			ResultSet rs = null;
			try{
				if(report_section_rsn.equals("0")) {
					String sqlString = "select name from tno.reports where rsn = " + report_rsn + " and user_rsn = " + user_rsn;
					rs = DbUtil.runSql(sqlString, session);
					if(rs.next()){
						name=rs.getString(1);
					}	
				} else {
					String sqlString="select r.name, s.name from tno.reports r, tno.report_sections s where r.rsn = s.report_rsn and r.rsn = " + report_rsn + " and s.rsn = " + report_section_rsn + " and r.user_rsn = " + user_rsn;
					rs = DbUtil.runSql(sqlString, session);
					if(rs.next()){
						name=rs.getString(1)+" - "+rs.getString(2);
					}				
				}
			} catch(SQLException err){;}
			try{ if(rs!=null) rs.close(); } catch(SQLException err){;}

			if(report_section_rsn.equals("0")) {
				where = " and n.rsn in (select r2.item_rsn from reports r, report_stories r2 " +
				"where r.rsn = " + report_rsn + " and r.rsn = r2.report_rsn and r.user_rsn = " + user_rsn + ")";
			} else {
				where = " and n.rsn in (select r2.item_rsn from reports r, report_stories r2 "+
				"where r.rsn = r2.report_rsn and r.rsn = " + report_rsn + " and r2.report_section_rsn = " + report_section_rsn + " and r.user_rsn = " + user_rsn + ")";
			}
			pm.put("sqlDescription","Report: "+name);

		} else{
			String sqlDescription="";
			where = where + " and s.provincewide = 0 and n.rsn in (-1)";

			pm.put("sqlDescription",sqlDescription);
		}
		pm.put("sql_where", where);

		String tonePoolString = getValueByTagName(doc, "b_tonepool");
		String tonePoolUserRSNs = getTonePool(tonePoolString, session);

		String tonesql = "0";
		if (yaxis.startsWith("Tone") || yaxis.startsWith("Earned Media")) { // only need tone for "tone" and "earned media" reports. Otherwise don't bother.
			tonesql = "select avg (tn.tone) from users_tones tn where tn.item_rsn = n.rsn";
			if(tonePoolUserRSNs.length()>0) {
				tonesql=tonesql+" and tn.user_rsn in ("+tonePoolUserRSNs+")";
			}
		}

		/*
		 *  Full sql statement
		 */
		String sql = "select n.rsn,to_char(n.item_date,'yyyy-mm-dd'),n.source,n.type,n.string6, " +
		"to_char(n.item_date,'DY'),n.title,n.number1,n.number2,length(n.text), (" + tonesql + ") " +
		"from news_items n, sources s where n.source = s.source(+) " + where;
		sql = sql + " union all ";
		sql = sql + "select n.rsn,to_char(n.item_date,'yyyy-mm-dd'),n.source,n.type,n.string6, " +
		"to_char(n.item_date,'DY'),n.title,n.number1,n.number2,length(n.text), (" + tonesql + ") " +
		"from hnews_items n, sources s where n.source = s.source(+) " + where;

		return sql;
	}

	private String getMinDate(Properties pm, Session session){
		String min = "";
		if(pm.containsKey("sql_where")){
			String where = pm.getProperty("sql_where");

			String sql = "select to_char(min(n.item_date),'yyyy-mm-dd') from news_items n, sources s where n.source = s.source(+) " + where;
			sql = sql + " union all ";
			sql = sql + "select to_char(min(n.item_date),'yyyy-mm-dd') from hnews_items n, sources s where n.source = s.source(+) " + where;
			sql = sql + " order by 1 asc";

			ResultSet rs=null;
			try{
				rs = DbUtil.runSql(sql, session);
				while(rs.next()){
					String d=rs.getString(1);
					if(d.length() >= 10) {
						min = d;
						rs.next();
					}
				}
			} catch(Exception err){
				decoratedError(INDENT0, "Analysis handler getMinDate()", err);
			}
			try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		}
		return min;
	}

	private String getMaxDate(Properties pm, Session session){
		String max = "";
		if(pm.containsKey("sql_where")){
			String where = pm.getProperty("sql_where");

			String sql = "select to_char(max(n.item_date),'yyyy-mm-dd') from news_items n, sources s where n.source = s.source(+) " + where;
			sql=sql + " union all ";
			sql=sql + "select to_char(max(n.item_date),'yyyy-mm-dd') from hnews_items n, sources s where n.source = s.source(+) " + where;
			sql=sql + " order by 1 desc";

			ResultSet rs=null;
			try{
				rs = DbUtil.runSql(sql, session);
				while(rs.next()){
					String d=rs.getString(1);
					if(d!=null){	
						if(d.length() >= 10) {
							max = d;
							rs.next();
						}
					}
				}
			} catch(Exception err){
				decoratedError(INDENT0, "Analysis Handler getMaxDate()", err);
			}
			try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		}
		return max;
	}

	private boolean isCategoryMatch(long rsn,String category, Session session){
		String name = StringUtil.replacement(category,"'","^^");
		name = StringUtil.replacement(name,"^^","''");

		boolean ok = false;
		ResultSet rs=null;
		try{
			rs = DbUtil.runSql("select count(*) from news_item_categories n, categories c where n.category_rsn = c.rsn and n.item_rsn = " + rsn + " and c.category = '" + name + "'", session);
			if(rs.next()){
				int c=rs.getInt(1);
				if(c>0)
					ok=true;
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		
		return ok;
	}

	private boolean isQuoted(long rsn, String quotedName, Session session){
		String name = StringUtil.replacement(quotedName,"'","^^");
		name = StringUtil.replacement(name,"^^","''");

		boolean quoted=false;
		ResultSet rs=null;
		try{
			rs = DbUtil.runSql("select count(*) from news_item_quotes where item_rsn = " + rsn + " and name = '" + name + "'", session);
			if(rs.next()){
				int quotes=rs.getInt(1);
				if(quotes>0)
					quoted=true;
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}

		return quoted;
	}

	private long countQuotes(long rsn,String quotedName, Session session){

		String sql="select count(*) from news_item_quotes where item_rsn = "+rsn;
		if(quotedName.length()>0){
			String name = StringUtil.replacement(quotedName, "'", "^^");
			name = StringUtil.replacement(name,"^^","''");
			sql=sql + " and name = '" + name + "'";
		}

		long quotes=0;
		ResultSet rs=null;
		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				quotes=rs.getInt(1);
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}

		return quotes;
	}

	private String getTonePool(String rsn_list, Session session) {
		String tonePoolRSNs="";

		String rsns[] = rsn_list.split(",");

		for(int i=0; i<rsns.length; i++) {
			String rsn = rsns[i];

			// all pools
			if (rsn.equalsIgnoreCase("all"))
				return "";

			// editor pool
			if ((rsn.equalsIgnoreCase("")) || (rsn.equalsIgnoreCase("0"))) {
				if (!tonePoolRSNs.equals("")) tonePoolRSNs += ",";
				tonePoolRSNs += "0";
			} else {

				String sql="select users from tone_pool where user_rsn = "+user_rsn+" and rsn = "+rsn;
				ResultSet rs=null;
				try{
					rs = DbUtil.runSql(sql, session);
					if(rs.next()){
						Clob cl=rs.getClob(1);
						long l=cl.length();
						if(l>0){
							if (!tonePoolRSNs.equals("")) tonePoolRSNs += ",";
							tonePoolRSNs += cl.getSubString(1,(int)l); // scares me here too
						}
					}
				} catch(Exception err){ ;}
				try{
					if(rs!=null) rs.close();
				} catch(SQLException err){ ;}
			}
		}
		return tonePoolRSNs;
	}

	private String getTonePoolName(String rsn_list, Session session){
		String name="";

		String rsns[] = rsn_list.split(",");

		for(int i=0; i<rsns.length; i++) {
			String rsn = rsns[i];

			// all pools
			if (rsn.equalsIgnoreCase("all"))
				return "All";

			// editor pool
			if ((rsn.equalsIgnoreCase("")) || (rsn.equalsIgnoreCase("0"))) {
				if (!name.equals("")) name += ", ";
				name += "TNO Editors";
			} else {

				String sql = "select name from tone_pool where user_rsn = "+user_rsn+" and rsn = " + rsn;
				ResultSet rs = null;
				try{
					rs = DbUtil.runSql(sql, session);
					if(rs.next()){
						if (!name.equals("")) name += ", ";
						name += rs.getString(1);
					}
				} catch(Exception err){ ;}
				try{
					if(rs!=null) rs.close();
				} catch(SQLException err){ ;}
			}
		}
		return name;
	}

	private long getReach(String label, String source_type, String source, String series, String dayOfWeek, long number2, Session session){
		if (source.equalsIgnoreCase("social media")) {
			return number2;
		}

		long reach = 0;
		ResultSet rs = null;
		String reachField = "reach_" + dayOfWeek;
		String sql = "";
		if(label.equalsIgnoreCase("byline/program")){
			//'keith baldrey' like replace(lower(name),' ','%') 
			String series_lowercase = series.toLowerCase();
			sql="select "+reachField+" from series where '"+series_lowercase+"' like replace(lower(name),' ','%') and use_in_analysis = 1";		
		} else {
			sql="select "+reachField+" from sources where source = '"+source+"' and use_in_analysis = 1";		
		}
		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				reach=rs.getLong(1);
			}
		} catch(Exception err){;}
		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		return reach;
	}

	private float getAdValue(String label, String source_type, String source, String series, String dayOfWeek, long number1, long number2, long contentLen, Session session){
		if (source.equalsIgnoreCase("social media")) {
			return calculate_bcpoli_advalue(number2);
		}

		float adValue = 0;
		float adValueRaw = 0;
		long charsColumnInch = 0;
		ResultSet rs=null;
		boolean print=true;

		if (source_type.equalsIgnoreCase("radio news") || 
				source_type.equalsIgnoreCase("talk radio") || 
				source_type.equalsIgnoreCase("tv news")) {
			print = false;
		}

		String adValueField = "ad_value_"+dayOfWeek;
		String sql = "";
		if (label.equalsIgnoreCase("byline/program") && !print){
			//'keith baldrey' like replace(lower(name),' ','%') 
			String series_lowercase = series.toLowerCase();
			sql="select " + adValueField + ", 0 from series where '" + series_lowercase + "' like replace(lower(name),' ','%') and use_in_analysis = 1";		
		} else {
			sql="select " + adValueField + ", chars_per_column_inch from sources where source = '" + source + "' and use_in_analysis = 1";		
		}

		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				adValueRaw=rs.getFloat(1);
				charsColumnInch=rs.getLong(2);
			}
		} catch(Exception err){;}

		if (print) {
			if (source_type.equalsIgnoreCase("internet")) {
				// INTERNET
				adValue = adValueRaw;
			} else {
				// PRINT
				// ad value raw is cost per column inch
				// ceiling is 60 column inches
				if (charsColumnInch==0) {
					adValue = 0;
				} else {
					adValue = Math.min((float)contentLen/(float)charsColumnInch, 60) * adValueRaw;
				}
			}
		} else {
			// TV / RADIO
			// ad value raw is cost per 30 seconds
			// ceiling is 10 minutes (20*30 seconds)
			adValue = Math.min((float)number1/30, 20) * adValueRaw;
		}

		try{ if(rs!=null) rs.close(); } catch(SQLException err){;}
		return adValue;
	}

	private long getKlout(long number1, String source_type) {
		if (source_type.equalsIgnoreCase("social media")) {
			return number1;
		} else {
			return 0;
		}
	}

	private String getReportSection(long rsn, String report_rsn, Session session){
		String report_section = "";
		ResultSet rs = null;

		String sql = "select name from report_sections s1, report_stories s2 where s1.rsn = s2.report_section_rsn and s2.item_rsn = " + rsn + " and s1.report_rsn = " + report_rsn;		
		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				report_section = rs.getString(1);
			}
		} catch(Exception err){;}
		try{ if(rs != null) rs.close(); } catch(SQLException err){;}
		return report_section;
	}

	private static int getDaysBetween(java.util.Calendar d1,java.util.Calendar d2){
		if(d1.after(d2)){ // swap dates so that d1 is start and d2 is end
			java.util.Calendar swap=d1;
			d1=d2;
			d2=swap;
		}
		int days=d2.get(java.util.Calendar.DAY_OF_YEAR)-d1.get(java.util.Calendar.DAY_OF_YEAR);
		int y2=d2.get(java.util.Calendar.YEAR);
		if(d1.get(java.util.Calendar.YEAR)!=y2){
			d1=(java.util.Calendar)d1.clone();
			do{
				days+=d1.getActualMaximum(java.util.Calendar.DAY_OF_YEAR);
				d1.add(java.util.Calendar.YEAR,1);
			} while(d1.get(java.util.Calendar.YEAR)!=y2);
		}
		return days;
	} // getDaysBetween()

	private boolean isSourceMatch(String source,String groupby,String matchitem, Session session){
		boolean match=false;
		ResultSet rs=null;
		if(source.length()==0)
			return match;

		String sql="";
		if(groupby.equalsIgnoreCase("geopolitical"))
			sql="select count(*) from sources s, sources_placenames sp, placenames_geopolitical pg, geopolitical g where "+
			" s.source = '"+source+"' and s.rsn = sp.sources_rsn and sp.placenames_rsn = pg.placenames_rsn and pg.geopolitical_rsn = g.rsn "+
			" and g.name = '"+matchitem+"'";
		else
			sql="select count(*) from sources s, sources_demographic sd, demographic d where "+
			" s.source = '"+source+"' and s.rsn = sd.sources_rsn and sd.demographic_rsn = d.rsn and d.name = '"+matchitem+"'";

		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				if(rs.getInt(1)>0)
					match=true;
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		return match;
	}

	private String getGroupTypeName(String groupby, String rsn, Session session){
		String name="";
		ResultSet rs=null;

		String sql="";
		if(groupby.equalsIgnoreCase("geopolitical"))
			sql="select type from geopolitical_types where rsn = "+rsn;
		else
			sql="select type from demographic_types where rsn = "+rsn;

		try{
			rs = DbUtil.runSql(sql, session);
			if(rs.next()){
				name=rs.getString(1);
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		return name;
	}

	private long getSourceCirculation(String source, Session session){
		long reach=0;
		ResultSet rs=null;
		if(source.length()==0)
			return reach;

		try{
			rs = DbUtil.runSql("select reach from sources s where s.source = '" + source + "'", session);
			if(rs.next())
				reach=rs.getLong(1);
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		return reach;
	}

	private long getSourceDemographicValue(String source, String demo, Session session){
		long value=0;
		ResultSet rs=null;
		if((source.length()==0) || (demo.length()==0))
			return value;

		try{
			rs = DbUtil.runSql("select sd.value from sources s, sources_demographic sd, demographic d where s.source = '" + source + "' and s.rsn = sd.sources_rsn and sd.demographic_rsn = d.rsn and d.name ='" + demo + "'", session);
			if(rs.next())
				value=rs.getLong(1);
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		return value;
	}

	private String getSourceList(String source,String groupby,String listtypersn, Session session){
		String list="";
		ResultSet rs=null;
		if(source.length()==0)
			return list;

		String sql="";
		if(groupby.equalsIgnoreCase("geopolitical"))
			sql="select distinct g.name from sources s, sources_placenames sp, placenames_geopolitical pg, geopolitical g where "+
			" s.source = '"+source+"' and s.rsn = sp.sources_rsn and sp.placenames_rsn = pg.placenames_rsn and pg.geopolitical_rsn = g.rsn "+
			" and g.type_rsn = "+listtypersn;
		else
			sql="select distinct g.name from sources s, sources_demographic sd, demographic d where "+
			" s.source = '"+source+"' and s.rsn = sd.sources_rsn and sd.demographic_rsn = d.rsn and d.type_rsn = "+listtypersn;

		try{
			rs = DbUtil.runSql(sql, session);
			while(rs.next()){
				if(list.length()>0)
					list=list+",";
				list=list+rs.getString(1);
			}
		} catch(Exception err){
			;}
		try{
			if(rs!=null)
				rs.close();
		} catch(SQLException err){
			;}
		return list;
	}
	
	public String getMap_detail() { return map_detail; }
	public String getMap_count_detail() { return map_count_detail; }
	
	private float calculate_bcpoli_advalue(long bcpoli_reach) {
		return bcpoli_reach*0.0052f;
	}

}

class ToneRenderer extends BarRenderer {
	static final long serialVersionUID = 42L;
	public ToneRenderer() {
	}
	public java.awt.Paint getItemPaint(final int row, final int column) {
		// returns color depending on y coordinate.
		CategoryDataset l_jfcDataset = getPlot().getDataset();
		String l_rowKey = (String)l_jfcDataset.getRowKey(row);
		String l_colKey = (String)l_jfcDataset.getColumnKey(column);
		double l_value  = l_jfcDataset.getValue(l_rowKey, l_colKey).doubleValue();
		if (l_value > 0) return Color.green;
		if (l_value < 0) return Color.red;
		return Color.gray;
	}
	public double[] calculateBarL0L1(double value) {
		double[] xx = super.calculateBarL0L1(value);
		if (value==0) {
			xx[0] = -0.2;
			xx[1] = 0.2;
		}
		return xx;
	}
}
class ToneRenderer3D extends BarRenderer3D {
	static final long serialVersionUID = 42L;
	public ToneRenderer3D() {
	}
	public java.awt.Paint getItemPaint(final int row, final int column) {
		// returns color depending on y coordinate.
		CategoryDataset l_jfcDataset = getPlot().getDataset();
		String l_rowKey = (String)l_jfcDataset.getRowKey(row);
		String l_colKey = (String)l_jfcDataset.getColumnKey(column);
		double l_value  = l_jfcDataset.getValue(l_rowKey, l_colKey).doubleValue();
		if (l_value > 0) return Color.green;
		if (l_value < 0) return Color.red;
		return Color.gray;
	}
	public double[] calculateBarL0L1(double value) {
		double[] xx = super.calculateBarL0L1(value);
		if (value==0) {
			xx[0] = -0.2;
			xx[1] = 0.2;
		}
		return xx;
	}
}
class StackedToneRenderer extends BarRenderer {
	static final long serialVersionUID = 42L;
	public StackedToneRenderer() {
	}
	public double[] calculateBarL0L1(double value) {
		double[] xx = super.calculateBarL0L1(value);
		if (value==0) {
			xx[0] = -0.2;
			xx[1] = 0.2;
		}
		return xx;
	}
}
class StackedToneRenderer3D extends BarRenderer3D {
	static final long serialVersionUID = 42L;
	public StackedToneRenderer3D() {
	}
	public double[] calculateBarL0L1(double value) {
		double[] xx = super.calculateBarL0L1(value);
		if (value==0) {
			xx[0] = -0.2;
			xx[1] = 0.2;
		}
		return xx;
	}
}
