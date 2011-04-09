package br.rio.puc.inf.lac.mobilis.cms.provider;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlSerializer;

import br.rio.puc.inf.lac.mobilis.cms.ContextInformationObject;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Xml;

/**
 * Meeting Context Provider Based on Google Calendar
 * 
 * @author victor
 */
public class MeetingContextProvider extends ContextProvider implements Runnable {
	
	private static final String TAG = "MeetingContextProvider";

	private class CalendarEvents
	{
		public String name;
		public String id;
		public String author;
		public String place;
		public String guests;
		public String when;
		public String reminder;
		
		CalendarEvents()
		{
			name = new String();
			id = new String();
			author = new String();
			place = new String();
			guests = new String();
			when = new String();
			reminder = new String();
		}
	}
	
	/* Google AUTH String */
	private String userAuth;
	
	/* Thread Handler */
	private Handler handler;
	
	/* Refresh Interval */
	private int interval;
	
	/**
	 * Constructor
	 * 
	 * @param context
	 */
	public MeetingContextProvider(Context context) {
		super(context, "MeetingContextProvider");
		handler = new Handler();
		
		interval = 900 * 1000; /* refresh interval set to 15 minutes */
		
		addInformationProvided("meeting.feed");
		
		addConfigurationSuported("setAuth");
		addConfigurationSuported("refreshInterval");

		Log.d(TAG, "Provedor de Reuniao Inicializado! (v2.17)");
	}

	@Override
	public int setConfiguration(String configName, String configValue) {
		if (configName.equals("setAuth")) {
			Log.d(TAG, "Setting userAuth to " + configValue);
			this.userAuth = configValue;
		}
		
		if (configName.equals("refreshInterval")) {
			Log.d(TAG, "Setting refresh interval to " + configValue);
			this.interval = Integer.parseInt(configValue) * 1000;
		}
		
		return 0;
	}

	@Override
	public void updateInformation(String information) {
		updateInformations();
	}

	protected void sendUpdatedInformation(ContextInformationObject obj) {
		updateInformation(obj);
	}

	@Override
	public void updateInformations() {
		if (!this.userAuth.equals(null))
			new MeetingGetter();
		
		handler.postDelayed(this, interval);
	}
	
	@Override
	public void run() {		
		updateInformations();
	}

	/**
	 * Implements a class to get all meeting informations.
	 * 
	 * @author victor
	 */
	private class MeetingGetter {
		/**
		 * {@inheritDoc}
		 */
		
		MeetingGetter() {
				Log.d(TAG, "Instantiating a new MeetingGetter...");
				run();
		}
		
		public void run(){
			CalendarEvents events[] = null;;
			try {
				events = getCalendarEvents (userAuth);

				XmlSerializer serializer = Xml.newSerializer();
				StringWriter writer = new StringWriter();
				serializer.setOutput(writer);
				
				serializer.startDocument("UTF-8", null);
				serializer.startTag(null, "root");
				
				for (CalendarEvents e : events) {
					Log.d(TAG, "Feeding id " + e.id);
					
					serializer.startTag(null, "entry");
					
					serializer.attribute(null, "id", e.id);
					serializer.attribute(null, "name", e.name);
					serializer.attribute(null, "author", e.author);
					serializer.attribute(null, "guests", e.guests);
					serializer.attribute(null, "place", e.place);
					serializer.attribute(null, "reminder", e.reminder);
					serializer.attribute(null, "when", e.when);
					
					serializer.endTag(null, "entry");
				}
				
				serializer.endTag(null, "root");
				serializer.endDocument();
				
				ContextInformationObject contextInformation = new ContextInformationObject("meeting");
				contextInformation.addContextInformation("feed", writer.toString());
				sendUpdatedInformation(contextInformation);
			}
			catch (ClientProtocolException cpe) {
				Log.e(TAG,"ClientProtocolException: "+cpe);
			}
			catch (IOException ioe) {
				Log.e(TAG,"IOException: "+ioe);
			}
		}
		
		/**
		 * Connects to Google Calendar and retrieves an event
		 * 
		 * @param auth - authorization token
		 * @return CalendarEvent
		 * 
		 * @author luizfelipe
		 * @throws IOException 
		 * @throws ClientProtocolException 
		 */
		 private CalendarEvents[] getCalendarEvents(String auth) throws ClientProtocolException, IOException {
			ArrayList<CalendarEvents> calendarEvents = new ArrayList<CalendarEvents>();
			
			HttpParams params = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(params, 10 * 1000);
			HttpConnectionParams.setSoTimeout(params, 10 * 1000);
			HttpConnectionParams.setTcpNoDelay(params, true);
			HttpConnectionParams.setStaleCheckingEnabled(params, true);
			
			HttpClient httpclient = new DefaultHttpClient(params);
			
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
			Calendar cal = Calendar.getInstance(TimeZone.getDefault());

			String startDate = dateFormat.format(cal.getTime()) + "T" + timeFormat.format(cal.getTime());
			
			cal.add(Calendar.DATE, 1);

			String endDate = dateFormat.format(cal.getTime()) + "T03:00:00";
			
			String queryString = "http://www.google.com/calendar/feeds/default/private/full"
			  + "?start-min=" + startDate + "&start-max=" + endDate + "&orderby=starttime&sortorder=ascending";
			
			Log.d("cms-client", "Quering google for: " + queryString);
			HttpGet httpget = new HttpGet(queryString);
			
			/* Sets required authorization header */
			httpget.addHeader("Authorization", "GoogleLogin auth=" + URLEncoder.encode(auth));
			
			HttpResponse response = httpclient.execute(httpget);
			Log.d("cms-client", "getCalendarEvent - Status: " + response.getStatusLine().getStatusCode());
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = null;
			try {
			  db = dbf.newDocumentBuilder();
			}
			catch (ParserConfigurationException e) {
			  e.printStackTrace();
			}
			InputSource is = new InputSource();
			is.setByteStream(response.getEntity().getContent());
			
			try {
			  Document doc = db.parse(is);
			  /* Obtem a lista de eventos */
			  NodeList events = doc.getElementsByTagName("entry");
			
			  for (int i = 0; i < events.getLength(); i++) {
				CalendarEvents calevent = new CalendarEvents();
			
			    /* Obtem o evento */
			    Element event = (Element) events.item(i);

			    NodeList idList = event.getElementsByTagName("id");
		    	Element id = (Element) idList.item(0);
		    	NodeList fstId = id.getChildNodes();
		    	calevent.id = fstId.item(0).getNodeValue();
		    	Log.d(TAG, "id("+i+"): " + calevent.id);

			    NodeList titleList = event.getElementsByTagName("title");
		    	Element title = (Element) titleList.item(0);
		    	NodeList fstTitle = title.getChildNodes();
		    	calevent.name = fstTitle.item(0).getNodeValue();
		    	Log.d(TAG, "name("+i+"): " + calevent.name);
			
			    NodeList authorList = event.getElementsByTagName("author");
		    	Element author = (Element) authorList.item(0);
		    	NodeList fstAuthor = author.getChildNodes();
		    	
		    	for (int j = 0; j < fstAuthor.getLength(); j++){
		    		Log.d(TAG, "author("+i+","+fstAuthor.item(j).getNodeName()+"): " + fstAuthor.item(j).getFirstChild().getNodeValue());
		    		if (fstAuthor.item(j).getNodeName().equals("name"))
		    			calevent.author = fstAuthor.item(j).getFirstChild().getNodeValue();
		    	}

			    String guests = new String();
			    NodeList whoList = event.getElementsByTagName("gd:who");
			    for (int j = 0; j < whoList.getLength(); j++)
			    {
			    	Element who = (Element) whoList.item(j);
			    	Log.d(TAG, "who("+i+","+j+"): " + who.getAttribute("email"));
			    	guests += who.getAttribute("email");
			    	if(i!=(whoList.getLength()-1))
			    		guests+=";";
			    	
			    }
			    calevent.guests = guests;

			    NodeList placeList = event.getElementsByTagName("gd:where");
			    if (placeList.getLength() > 0) {
			    	Element place = (Element) placeList.item(0);
			    	calevent.place = place.getAttribute("valueString");
			    	Log.d(TAG, "place("+i+"): " + calevent.place);
			    }
			    
			    NodeList whenList = event.getElementsByTagName("gd:when");
			    if (whenList.getLength() > 0) {
			    	Element when = (Element) whenList.item(0);
			    	calevent.when = when.getAttribute("startTime");
			    	Log.d(TAG, "when("+i+"): " + calevent.when);
			    }
			    
			    NodeList reminderList = event.getElementsByTagName("gd:reminder");
			    if (reminderList.getLength() > 0) {
			    	for (int j = 0; j < whoList.getLength(); j++) {
			    		Element reminder = (Element) reminderList.item(j);
			    		Log.d(TAG, "method("+i+","+reminder.getAttribute("method")+"): " + reminder.getAttribute("minutes"));
			    		if (reminder.getAttribute("method").equals("alert"))
			    			calevent.reminder = reminder.getAttribute("minutes");
			    	} 
			    }
			    calendarEvents.add(calevent);
			   }
			 }
			 catch (Exception e) {
			   e.printStackTrace();
			   Log.e(TAG, e.getMessage());
			 }
			
			 httpclient.getConnectionManager().shutdown();
			 return calendarEvents.toArray(new CalendarEvents[0]);
		}
	}
}
