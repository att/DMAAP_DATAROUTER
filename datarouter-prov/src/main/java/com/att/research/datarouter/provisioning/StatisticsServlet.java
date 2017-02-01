/*
 *                        AT&T - PROPRIETARY
 *          THIS FILE CONTAINS PROPRIETARY INFORMATION OF
 *        AT&T AND IS NOT TO BE DISCLOSED OR USED EXCEPT IN
 *             ACCORDANCE WITH APPLICABLE AGREEMENTS.
 *
 *          Copyright (c) 2013 AT&T Knowledge Ventures
 *              Unpublished and Not for Publication
 *                     All Rights Reserved
 */

package com.att.research.datarouter.provisioning;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.LOGJSONObject;
import com.att.research.datarouter.provisioning.beans.EventLogRecord;
import com.att.research.datarouter.provisioning.utils.DB;

/**
 * This Servlet handles requests to the &lt;Statistics API&gt; and  &lt;Statistics consilidated resultset&gt;,
 * @author Manish Singh 
 * @version $Id: StatisticsServlet.java,v 1.11 2016/08/10 17:27:02 Manish Exp $
 */
@SuppressWarnings("serial")

public class StatisticsServlet extends BaseServlet {

	private static final long TWENTYFOUR_HOURS = (24 * 60 * 60 * 1000L);
	private static final String fmt1 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	private static final String fmt2 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

	
	/**
	 * DELETE a logging URL -- not supported.
	 */
	@Override
	public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String message = "DELETE not allowed for the logURL.";
		EventLogRecord elr = new EventLogRecord(req);
		elr.setMessage(message);
		elr.setResult(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		eventlogger.info(elr);
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
	}
	/**
	 * GET a Statistics URL -- retrieve Statistics data for a feed or subscription.
	 * See the <b>Statistics API</b> document for details on how this 	method should be invoked.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		Map<String, String> map = buildMapFromRequest(req);
		if (map.get("err") != null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments: "+map.get("err"));
			return;
		}
		// check Accept: header??
		
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType(LOGLIST_CONTENT_TYPE);
		ServletOutputStream out = resp.getOutputStream();
		
		
		String outputType = "json";
		String feedids = null;
		
		if(req.getParameter("feedid") ==null && req.getParameter("groupid") ==null)
		{
			out.print("Invalid request, Feedid or Group ID is required.");
		}
		
	    if(req.getParameter("feedid")!=null && req.getParameter("groupid") == null) {
			map.put("feedids", req.getParameter("feedid").replace("|", ",").toString());
		}

		if(req.getParameter("groupid") != null && req.getParameter("feedid") ==null) {
			  // String groupid1 = null;
			StringBuffer groupid1 = new  StringBuffer();  
			   
				 try {
					 System.out.println("feeedidsssssssss");
					 groupid1 = this.getFeedIdsByGroupId(Integer.parseInt(req.getParameter("groupid")));
					  System.out.println("feeedids"+req.getParameter("groupid"));
					  
						map.put("feedids", groupid1.toString());
						System.out.println("groupid1" +groupid1.toString());
					  
					  					   
				  } catch (NumberFormatException e) {
					 e.printStackTrace();
				  } catch (SQLException e) {
				    e.printStackTrace();
				 }
			}
		if(req.getParameter("groupid") != null && req.getParameter("feedid") !=null) {
			   StringBuffer groupid1 = new  StringBuffer();
			     
				   
				 try {
					 System.out.println("both r not null");
					 groupid1 = this.getFeedIdsByGroupId(Integer.parseInt(req.getParameter("groupid")));
					  System.out.println("feeedids"+req.getParameter("groupid"));
					  groupid1.append(",");
					   groupid1.append(req.getParameter("feedid").replace("|", ",").toString());
					   
						map.put("feedids", groupid1.toString());
						
					
						System.out.println("groupid1" +groupid1.toString());
					  
					  					   
				  } catch (NumberFormatException e) {
					 e.printStackTrace();
				  } catch (SQLException e) {
				    e.printStackTrace();
				 }
			}
		
		
				
		if(req.getParameter("subid")!=null && req.getParameter("feedid") !=null) {
			 StringBuffer subidstr = new  StringBuffer();
//			 subidstr.append(" and e.DELIVERY_SUBID in(subid)");
//			  subidstr.append(req.getParameter("subid").replace("|", ",").toString());
			 subidstr.append("and e.DELIVERY_SUBID in(");
			
			 subidstr.append(req.getParameter("subid").replace("|", ",").toString());
			 subidstr.append(")");
			 map.put("subid", subidstr.toString());
		}
		if(req.getParameter("subid")!=null && req.getParameter("groupid") !=null) {
			 StringBuffer subidstr = new  StringBuffer();
//			 subidstr.append(" and e.DELIVERY_SUBID in(subid)");
//			  subidstr.append(req.getParameter("subid").replace("|", ",").toString());
			 subidstr.append("and e.DELIVERY_SUBID in(");
			
			 subidstr.append(req.getParameter("subid").replace("|", ",").toString());
			 subidstr.append(")");
			 map.put("subid", subidstr.toString());
		}
		if(req.getParameter("type")!=null) {
			map.put("eventType", req.getParameter("type").replace("|", ",").toString());
		}
			if(req.getParameter("output_type")!=null) {
			map.put("output_type", req.getParameter("output_type").toString());
		}
		if(req.getParameter("start_time")!=null) {
			map.put("start_time", req.getParameter("start_time").toString());
		}
		if(req.getParameter("end_time")!=null) {
			map.put("end_time", req.getParameter("end_time").toString());
		}
		
		if(req.getParameter("time")!=null) {
			map.put("start_time", req.getParameter("time").toString());
			map.put("end_time", null);
			}
		
		
				
		if(req.getParameter("output_type") !=null)
		{
			outputType = req.getParameter("output_type");
		}
		
	
		try {
			
			String filterQuery = this.queryGeneretor(map);
			eventlogger.debug("SQL Query for Statistics resultset. "+filterQuery);
			
			ResultSet rs=this.getRecordsForSQL(filterQuery);
			
			if(outputType.equals("csv")) {
				resp.setContentType("application/octet-stream");
				Date date = new Date() ;
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-YYYY HH:mm:ss") ;
				resp.setHeader("Content-Disposition", "attachment; filename=\"result:"+dateFormat.format(date)+".csv\"");
				eventlogger.info("Generating CSV file from Statistics resultset");
				
				rsToCSV(rs, out);
			}
			else {
				eventlogger.info("Generating JSON for Statistics resultset");
				this.rsToJson(rs, out);	
			}
		} 
		catch (IOException e) {
			eventlogger.error("IOException - Generating JSON/CSV:"+e);
			e.printStackTrace();
		 } 
		catch (JSONException e) {
			eventlogger.error("JSONException - executing SQL query:"+e);
			e.printStackTrace();
		} catch (SQLException e) {
			eventlogger.error("SQLException - executing SQL query:"+e);
			e.printStackTrace();
		} catch (ParseException e) {
			eventlogger.error("ParseException - executing SQL query:"+e);
			e.printStackTrace();
		}
	}
	
	
	/**
	 * rsToJson - Converting RS to JSON object
	 * @exception IOException, SQLException
	 * @param out ServletOutputStream, rs as ResultSet
	 */
	public void rsToCSV(ResultSet rs, ServletOutputStream out) throws IOException, SQLException {
		String header = "FEEDNAME,FEEDID,FILES_PUBLISHED,PUBLISH_LENGTH, FILES_DELIVERED, DELIVERED_LENGTH, SUBSCRIBER_URL, SUBID, PUBLISH_TIME,DELIVERY_TIME, AverageDelay\n";

		// String header = "FEEDNAME,FEEDID,TYPE,REMOTE_ADDR,DELIVERY_SUBID,REQURI,TOTAL CONTENT LENGTH,NO OF FILE,AVERAGE DELAY\n";
		 
         out.write(header.getBytes());
         			            
         while(rs.next()) {
         	StringBuffer line = new StringBuffer();
	            line.append(rs.getString("FEEDNAME"));
	            line.append(",");
	            line.append(rs.getString("FEEDID"));
	            line.append(",");
	            line.append(rs.getString("FILES_PUBLISHED"));
	            line.append(",");
	            line.append(rs.getString("PUBLISH_LENGTH"));
	            line.append(",");
	            line.append(rs.getString("FILES_DELIVERED"));
	            line.append(",");
	            line.append(rs.getString("DELIVERED_LENGTH"));
	            line.append(",");
	            line.append(rs.getString("SUBSCRIBER_URL"));
	            line.append(",");
	            line.append(rs.getString("SUBID"));
	            line.append(",");
	            line.append(rs.getString("PUBLISH_TIME"));
	            line.append(",");
	            line.append(rs.getString("DELIVERY_TIME"));
	            line.append(",");
	            line.append(rs.getString("AverageDelay"));
	            line.append(",");
	        
	            line.append("\n");
	            out.write(line.toString().getBytes());
	            out.flush();
         }
	}
	
	/**
	 * rsToJson - Converting RS to JSON object
	 * @exception IOException, SQLException
	 * @param out ServletOutputStream, rs as ResultSet
	 */
	public void rsToJson(ResultSet rs, ServletOutputStream out) throws IOException, SQLException {
		
		String fields[] = {"FEEDNAME","FEEDID","FILES_PUBLISHED","PUBLISH_LENGTH", "FILES_DELIVERED", "DELIVERED_LENGTH", "SUBSCRIBER_URL", "SUBID", "PUBLISH_TIME","DELIVERY_TIME", "AverageDelay"};
		StringBuffer line = new StringBuffer();
       	
		 line.append("[\n");
		
		 while(rs.next()) {
			 LOGJSONObject j2 = new LOGJSONObject();
			 for (String key : fields) {
				Object v = rs.getString(key);
				if (v != null)
					j2.put(key.toLowerCase(), v);
				else
					j2.put(key.toLowerCase(), "");
			}
			line =  line.append(j2.toString());;
			line.append(",\n");
		 }
		 line.append("]");
		out.print(line.toString());
	}
	
	/**
	 * getFeedIdsByGroupId - Getting FEEDID's by GROUP ID.
	 * @exception SQL Query SQLException.
	 * @param groupIds
	 */
	public StringBuffer getFeedIdsByGroupId(int groupIds) throws SQLException{ 
		 
		DB db = null; 
		Connection conn = null; 
		PreparedStatement prepareStatement = null; 
		ResultSet resultSet=null; 
		String sqlGoupid = null; 
		StringBuffer feedIds = new StringBuffer(); 
	 
		try { 
			db = new DB(); 
			conn = db.getConnection(); 
			sqlGoupid= " SELECT FEEDID from FEEDS  WHERE GROUPID = ?"; 
			prepareStatement =conn.prepareStatement(sqlGoupid); 
			prepareStatement.setInt(1, groupIds); 
			resultSet=prepareStatement.executeQuery(); 
			while(resultSet.next()){ 		
				feedIds.append(resultSet.getInt("FEEDID"));
				feedIds.append(",");
			} 
			feedIds.deleteCharAt(feedIds.length()-1);
		System.out.println("feedIds"+feedIds.toString());
			
		} catch (SQLException e) { 
			e.printStackTrace(); 
		} finally { 
			try { 
					if(resultSet != null) { 
						resultSet.close(); 
						resultSet = null; 
					} 
	 
					if(prepareStatement != null) { 
						prepareStatement.close(); 
						prepareStatement = null; 
					} 
	 
					if(conn != null){ 
						db.release(conn); 
					} 
				} catch(Exception e) { 
					e.printStackTrace(); 
				} 
		} 
		return feedIds; 
	}

	
	/**
	 * queryGeneretor - Generating sql query
	 * @exception SQL Query parse exception.
	 * @param Map as key value pare of all user input fields
	 */
	public String queryGeneretor(Map<String, String> map) throws ParseException{
		 
		String sql = null;
		String eventType = null;
		String feedids = null;
		String start_time = null;
		String end_time = null;
		String subid=" ";
		if(map.get("eventType") != null){
			eventType=(String) map.get("eventType");
		}
		if(map.get("feedids") != null){
			feedids=(String) map.get("feedids");
		}
		if(map.get("start_time") != null){
			start_time=(String) map.get("start_time");
		}
		if(map.get("end_time") != null){
			end_time=(String) map.get("end_time");
		}
		if("all".equalsIgnoreCase(eventType)){
			eventType="PUB','DEL, EXP, PBF";
		}
		if(map.get("subid") != null){
			subid=(String) map.get("subid");
		}
		
		eventlogger.info("Generating sql query to get Statistics resultset. ");
		
		if(end_time==null && start_time==null ){

				
				sql="SELECT (SELECT NAME FROM FEEDS AS f WHERE f.FEEDID in("+feedids+") and f.FEEDID=e.FEEDID) AS FEEDNAME, e.FEEDID as FEEDID, (SELECT COUNT(*) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+") and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS FILES_PUBLISHED,(SELECT SUM(content_length) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+")  and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS PUBLISH_LENGTH, COUNT(e.EVENT_TIME) as FILES_DELIVERED,  sum(m.content_length) as DELIVERED_LENGTH,SUBSTRING_INDEX(e.REQURI,'/',+3) as SUBSCRIBER_URL, e.DELIVERY_SUBID as SUBID, e.EVENT_TIME AS PUBLISH_TIME, m.EVENT_TIME AS DELIVERY_TIME,  AVG(e.EVENT_TIME - m.EVENT_TIME)/1000 as AverageDelay FROM LOG_RECORDS e JOIN LOG_RECORDS m ON m.PUBLISH_ID = e.PUBLISH_ID AND e.FEEDID IN ("+feedids+") "+subid+" AND m.STATUS=204 AND e.RESULT=204  group by SUBID";
				
			return sql;
		}else if(start_time!=null && end_time==null ){

			long inputTimeInMilli=60000*Long.parseLong(start_time);
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
			long currentTimeInMilli=cal.getTimeInMillis();
			long compareTime=currentTimeInMilli-inputTimeInMilli;
			
			  sql="SELECT (SELECT NAME FROM FEEDS AS f WHERE f.FEEDID in("+feedids+") and f.FEEDID=e.FEEDID) AS FEEDNAME, e.FEEDID as FEEDID, (SELECT COUNT(*) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+") and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS FILES_PUBLISHED,(SELECT SUM(content_length) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+")  and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS PUBLISH_LENGTH, COUNT(e.EVENT_TIME) as FILES_DELIVERED,  sum(m.content_length) as DELIVERED_LENGTH,SUBSTRING_INDEX(e.REQURI,'/',+3) as SUBSCRIBER_URL, e.DELIVERY_SUBID as SUBID, e.EVENT_TIME AS PUBLISH_TIME, m.EVENT_TIME AS DELIVERY_TIME,  AVG(e.EVENT_TIME - m.EVENT_TIME)/1000 as AverageDelay FROM LOG_RECORDS e JOIN LOG_RECORDS m ON m.PUBLISH_ID = e.PUBLISH_ID AND e.FEEDID IN ("+feedids+") "+subid+" AND m.STATUS=204 AND e.RESULT=204 and e.event_time>="+compareTime+" group by SUBID";
			 
    		 return sql;
		
		}else{
			SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			Date startDate=inFormat.parse(start_time);
			Date endDate=inFormat.parse(end_time);

			long startInMillis=startDate.getTime();
			long endInMillis=endDate.getTime();
			
			 {
				
				sql="SELECT (SELECT NAME FROM FEEDS AS f WHERE f.FEEDID in("+feedids+") and f.FEEDID=e.FEEDID) AS FEEDNAME, e.FEEDID as FEEDID, (SELECT COUNT(*) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+") and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS FILES_PUBLISHED,(SELECT SUM(content_length) FROM LOG_RECORDS AS c WHERE c.FEEDID in("+feedids+")  and c.FEEDID=e.FEEDID AND c.TYPE='PUB') AS PUBLISH_LENGTH, COUNT(e.EVENT_TIME) as FILES_DELIVERED,  sum(m.content_length) as DELIVERED_LENGTH,SUBSTRING_INDEX(e.REQURI,'/',+3) as SUBSCRIBER_URL, e.DELIVERY_SUBID as SUBID, e.EVENT_TIME AS PUBLISH_TIME, m.EVENT_TIME AS DELIVERY_TIME,  AVG(e.EVENT_TIME - m.EVENT_TIME)/1000 as AverageDelay FROM LOG_RECORDS e JOIN LOG_RECORDS m ON m.PUBLISH_ID = e.PUBLISH_ID AND e.FEEDID IN ("+feedids+") "+subid+" AND m.STATUS=204 AND e.RESULT=204 and e.event_time between "+startInMillis+" and "+endInMillis+" group by SUBID";
				
			}
			return sql;
		}
	}
	
	
	/**
	 * PUT a Statistics URL -- not supported.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String message = "PUT not allowed for the StatisticsURL.";
		EventLogRecord elr = new EventLogRecord(req);
		elr.setMessage(message);
		elr.setResult(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		eventlogger.info(elr);
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
	}
	/**
	 * POST a Statistics URL -- not supported.
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String message = "POST not allowed for the StatisticsURL.";
		EventLogRecord elr = new EventLogRecord(req);
		elr.setMessage(message);
		elr.setResult(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		eventlogger.info(elr);
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
	}

	private Map<String, String> buildMapFromRequest(HttpServletRequest req) {
		Map<String, String> map = new HashMap<String, String>();
		String s = req.getParameter("type");
		if (s != null) {
			if (s.equals("pub") || s.equals("del") || s.equals("exp")) {
				map.put("type", s);
			} else {
				map.put("err", "bad type");
				return map;
			}
		} else
		map.put("type", "all");
		map.put("publishSQL", "");
		map.put("statusSQL", "");
		map.put("resultSQL", "");
		map.put("reasonSQL", "");

		s = req.getParameter("publishId");
		if (s != null) {
			if (s.indexOf("'") >= 0) {
				map.put("err", "bad publishId");
				return map;
			}
			map.put("publishSQL", " AND PUBLISH_ID = '"+s+"'");
		}

		s = req.getParameter("statusCode");
		if (s != null) {
			String sql = null;
			if (s.equals("success")) {
				sql = " AND STATUS >= 200 AND STATUS < 300";
			} else if (s.equals("redirect")) {
				sql = " AND STATUS >= 300 AND STATUS < 400";
			} else if (s.equals("failure")) {
				sql = " AND STATUS >= 400";
			} else {
				try {
					Integer n = Integer.parseInt(s);
					if ((n >= 100 && n < 600) || (n == -1))
						sql = " AND STATUS = " + n;
				} catch (NumberFormatException e) {
				}
			}
			if (sql == null) {
				map.put("err", "bad statusCode");
				return map;
			}
			map.put("statusSQL", sql);
			map.put("resultSQL", sql.replaceAll("STATUS", "RESULT"));
		}

		s = req.getParameter("expiryReason");
		if (s != null) {
			map.put("type", "exp");
			if (s.equals("notRetryable")) {
				map.put("reasonSQL", " AND REASON = 'notRetryable'");
			} else if (s.equals("retriesExhausted")) {
				map.put("reasonSQL", " AND REASON = 'retriesExhausted'");
			} else if (s.equals("diskFull")) {
				map.put("reasonSQL", " AND REASON = 'diskFull'");
			} else if (s.equals("other")) {
				map.put("reasonSQL", " AND REASON = 'other'");
			} else {
				map.put("err", "bad expiryReason");
				return map;
			}
		}

		long stime = getTimeFromParam(req.getParameter("start"));
		if (stime < 0) {
			map.put("err", "bad start");
			return map;
		}
		long etime = getTimeFromParam(req.getParameter("end"));
		if (etime < 0) {
			map.put("err", "bad end");
			return map;
		}
		if (stime == 0 && etime == 0) {
			etime = System.currentTimeMillis();
			stime = etime - TWENTYFOUR_HOURS;
		} else if (stime == 0) {
			stime = etime - TWENTYFOUR_HOURS;
		} else if (etime == 0) {
			etime = stime + TWENTYFOUR_HOURS;
		}
		map.put("timeSQL", String.format(" AND EVENT_TIME >= %d AND EVENT_TIME <= %d", stime, etime));
		return map;
	}
	private long getTimeFromParam(final String s) {
		if (s == null)
			return 0;
		try {
			// First, look for an RFC 3339 date
			String fmt = (s.indexOf('.') > 0) ? fmt2 : fmt1;
			SimpleDateFormat sdf = new SimpleDateFormat(fmt);
			Date d = sdf.parse(s);
			return d.getTime();
		} catch (ParseException e) {
		}
		try {
			// Also allow a long (in ms); useful for testing
			long n = Long.parseLong(s);
			return n;
		} catch (NumberFormatException e) {
		}
		intlogger.info("Error parsing time="+s);
		return -1;
	}

	
	private ResultSet getRecordsForSQL(String sql) {
		intlogger.debug(sql);
		long start = System.currentTimeMillis();
		DB db = new DB();
		Connection conn = null;
		ResultSet rs=null;
		
		try {
			conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			PreparedStatement pst=conn.prepareStatement(sql);
			rs=pst.executeQuery();
			//this.rsToJson(rs)
			//rs.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				db.release(conn);
		}
		
		intlogger.debug("Time: " + (System.currentTimeMillis()-start) + " ms");
		
		return rs;
	}
}
