
/*
 * MAI - Multi-document Adjudication Interface
 * 
 * Copyright Amber Stubbs (astubbs@cs.brandeis.edu)
 * Department of Computer Science, Brandeis University
 * 
 * MAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package mai;

import java.sql.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collections;
import java.util.Enumeration;

/**
 * AdjudDB is the class that handles all the calls to the 
 * SQLite database.  AdjudDB in MAI has the following tables:
 * 1) extents, with columns: location int(5), element_name, id
 * 2) links, with columns: id,fromid,from_name,toid,to_name,element_name
 * 3) one table for each tag in the DTD, where information about each
 * tag in every file that's loaded is stored
 * 
 * @author Amber Stubbs
 *
 */

class AdjudDB {

	private PreparedStatement extent_insert;
	private PreparedStatement link_insert;
	private PreparedStatement overlap_insert;
	private Connection conn;    
	private Hashtable<String,PreparedStatement> insertSents;
	private ArrayList<String> currentLinks; //used to keep track of what links are being displayed
	private HashCollection<String,String>currentHighlights; //used to keep track of highlights

	/**
	 * Creates all the tables, HashTables, PreparedStatements, and the connection
	 * to the database.
	 */
	AdjudDB(){
		try{
			currentLinks = new ArrayList<String>();
			currentHighlights = new HashCollection<String,String>();
			insertSents = new Hashtable<String,PreparedStatement>();

			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:adjud.db");
			Statement stat = conn.createStatement();
			stat.executeUpdate("drop table if exists extents;");
			stat.executeUpdate("create table extents (file_name, location int(5), element_name, id);");
			stat.executeUpdate("drop table if exists links;");
			stat.executeUpdate("create table links (file_name, id,fromid,from_name,toid,to_name,element_name);");
			stat.executeUpdate("drop table if exists extent_overlaps");
			stat.executeUpdate("create table extent_overlaps (gsid, element_name, file_name, fileid);");

			extent_insert = conn.prepareStatement("insert into extents values (?, ?, ?, ?);");
			link_insert = conn.prepareStatement("insert into links values (?, ?, ?, ?, ?, ?, ?);");
			overlap_insert = conn.prepareStatement("insert into extent_overlaps values (?, ?, ?, ?);");
		}catch(Exception e){
			System.out.println(e.toString());
		}
	}



	/**
	 * Adds a table to the DB for every link in the DTD
	 * There will be a problem if any of those tags/tables is named
	 * "extent", "link", or "extent_overlap"
	 * 
	 * @param dtd The DTD object that was loaded into MAI
	 */
	void addDTD(DTD dtd){
		ArrayList<Elem> elems = dtd.getElements();
		for (int i=0;i<elems.size();i++){
			try{
				addTableToDB(elems.get(i));
			}catch(Exception ex){ System.out.println(ex); }
		}
	}

	/**
	 * Creates the table and a PreparedStatement for the table;
	 * PreparedStatements go in the insertSents hashtable for use later.
	 * 
	 * @param elem the Elem object being turned into a table
	 * @throws Exception
	 */
	private void addTableToDB(Elem elem) throws Exception{

		String name = elem.getName();
		Statement stat = conn.createStatement();
		stat.executeUpdate("drop table if exists "+name+";");
		ArrayList<Attrib> atts = elem.getAttributes();
		String statement = ("create table "+name+" (file_name, ");
		String prep_insert = ("insert into "+name+" values (?, ");
		for(int i=0;i<atts.size();i++){
			if(i==atts.size()-1){
				statement = statement + atts.get(i).getName() +");";
				prep_insert = prep_insert + "?);";
			}
			else{
				statement = statement + atts.get(i).getName() +", ";
				prep_insert = prep_insert + "?, ";
			}
		}
		stat.executeUpdate(statement);
		PreparedStatement st = conn.prepareStatement(prep_insert);
		insertSents.put(name, st);
	}

	/**
	 * Sends the command that all the tag-specific tables in the 
	 * database have their PreparedStatements inserted into the table
	 * 
	 * @param dtd the DTD containing all the tag names used to 
	 * create the tables
	 * 
	 * @throws Exception
	 */
	private void batchAll(DTD dtd) throws Exception{
		ArrayList<Elem> elements = dtd.getElements();
		for (int i=0;i<elements.size();i++){
			String name = elements.get(i).getName();
			PreparedStatement ps = insertSents.get(name);
			conn.setAutoCommit(false);
			ps.executeBatch();
			conn.setAutoCommit(true);
		}
	}

	/**
	 * Inserts the PreparedStatements for a single table
	 * 
	 * @param e the Elem object describing the table being
	 * updated
	 * 
	 * @throws Exception
	 */
	void batchElement(Elem e) throws Exception{
		PreparedStatement ps = insertSents.get(e.getName());
		conn.setAutoCommit(false);
		ps.executeBatch();
		conn.setAutoCommit(true);

	}

	/**
	 * When a file is loaded into MAI the tags are turned into a HashCollection 
	 * and sent here to be loaded into the database tables.
	 * 
	 * @param fullName the name of the file being added
	 * @param dtd the DTD describing the tags in the file
	 * @param newTags the HashCollection containing all the tags being added
	 */
	void addTagsFromHash(String fullName, DTD dtd, 
			HashCollection<String,Hashtable<String,String>> newTags){
		//for each tag in the DTD, get the ArrayList of Hashtables associated with it
		ArrayList<Elem> elements = dtd.getElements();
		for (int i=0;i<elements.size();i++){
			String name = elements.get(i).getName();
			Elem elem = dtd.getElem(name);
			ArrayList<Hashtable<String,String>> tagList = newTags.getList(name);
			//for each set of tags in the ArrayList, add the tags to the DB
			//extent tags first
			if(tagList!=null){
				if (elem instanceof ElemExtent){
					for(int j=0;j<tagList.size();j++){
						//first, add the extent tags with the PreparedStatement for that table
						Hashtable<String,String> tag = tagList.get(j);
						usePreparedExtentStatements(fullName,elem,tag);
					}
				}
			}
		}

		try{
			batchExtents();
			batchAll(dtd);
		}catch(Exception e){
			System.out.println(e.toString());
			System.out.println("error adding extent tags");
		}
		for (int i=0;i<elements.size();i++){
			String name = elements.get(i).getName();
			Elem elem = dtd.getElem(name);
			ArrayList<Hashtable<String,String>> tagList = newTags.getList(name);
			if(tagList!=null){
				if (elem instanceof ElemLink && tagList != null){
					for(int j=0;j<tagList.size();j++){
						//next, add the links tags with the PreparedStatement for that table
						Hashtable<String,String> tag = tagList.get(j);
						usePreparedLinkStatements(fullName,elem,tag);
					}
				}
			}
		}
		try{
			batchLinks();
			batchAll(dtd);
		}catch(Exception e){
			System.out.println(e.toString());
			System.out.println("error adding link tags");
		}
	}

	/**
	 * Uses the previously created PreparedStatements
	 * to enter extent tag information into the database
	 * 
	 * @param fullName name of the file being added
	 * @param elem the Elem object describing the tag being added
	 * @param tag the Hashtable containing the tag information
	 */
	void usePreparedExtentStatements(String fullName, Elem elem,
			Hashtable<String,String> tag){
		//get PreparedStatement from Hashtable
		PreparedStatement ps = insertSents.get(elem.getName());
		ArrayList<Attrib> atts = elem.getAttributes();
		try{ ps.setString(1,fullName);}
		catch(Exception e){
			System.out.println(e.toString());
			System.out.println("error adding name");
		}
		//add the tag information to the preparedStatement
		for(int i=0;i<atts.size();i++){
			try{
				ps.setString(i+2,tag.get(atts.get(i).getName()));
			}catch(Exception e){
				System.out.println(e.toString());
				System.out.println("error setting String for "+tag.get(atts.get(i).getName()));
			}
		}
		try{
			//add the set strings to the preparedstatement's batch
			ps.addBatch();
		}catch(Exception e){
			System.out.println(e.toString());
			System.out.println("error adding extent batch");
		}
		//now, add the tag information to the extent table
		String startString = tag.get("start");
		String endString = tag.get("end");
		int start = Integer.valueOf(startString);
		int end = Integer.valueOf(endString);
		//if the tag is associated with a span in the text, use this
		if(start>-1){
			for(int i=start;i<end;i++){
				try{
					add_extent(fullName,i,elem.getName(),tag.get("id"));
				}catch(Exception e){
					System.out.println(e.toString());
					System.out.println("error adding extent");
				}
			}
		}
		//otherwise (if it's a non-consuming tag), use this
		else{
			try{
				add_extent(fullName,-1,elem.getName(),tag.get("id"));
			}catch(Exception e){
				System.out.println(e.toString());
				System.out.println("error adding -1 extent");
			}
		}
	}

	/**
	 * Uses the previously created PreparedStatements
	 * to enter extent tag information into the database
	 * 
	 * @param fullName name of the file being added
	 * @param elem the Elem object describing the tag being added
	 * @param tag the Hashtable containing the tag information
	 */
	void usePreparedLinkStatements(String fullName, Elem elem,
			Hashtable<String,String> tag){
		//get PreparedStatement from Hashtable
		try{
			PreparedStatement ps = insertSents.get(elem.getName());
			ArrayList<Attrib> atts = elem.getAttributes();
			ps.setString(1,fullName);
			for(int i=0;i<atts.size();i++){
				String test = tag.get(atts.get(i).getName());
				if (test!=null){
					ps.setString(i+2,test);
				}
				else{
					ps.setString(i+2,"");
				}
			}
			try{
				ps.addBatch();
			}catch(Exception e){
				System.out.println(e.toString());
				System.out.println("error adding link batch");
			}
			//add the tag information to the link table
			String from_id = tag.get("fromID");
			String to_id = tag.get("toID");
			String from_type = getElementByFileAndID(fullName,from_id);
			String to_type = getElementByFileAndID(fullName,to_id);
			try{
				add_link(fullName,tag.get("id"),elem.getName(),
						from_id, from_type,to_id,to_type);
			}catch(Exception e){
				System.out.println(e.toString());
				System.out.println("error adding link to link table");
			}
		}
		catch(Exception e){
			System.out.println(e.toString());
			System.out.println("error adding link: "+
					"filename = "+fullName+ "\ntag id = "+tag.get("id"));
		}

	}

	//returns all the tag information based on file name and id
	/**
	 * Returns a hashtable of tag information based on the id,  
	 * tag name, and filename of the tag
	 * 
	 * @param tagname the name of the tag in the DTD
	 * @param id the ID being searched for
	 * @param filename the name of the file the tag is in
	 * @param atts the list of attributes whose values will be put 
	 * in the Hashtable.
	 * 
	 * @return a Hashtable containing the attribute names and their 
	 * values for the tag being searched for.
	 * 
	 * @throws Exception
	 */
	Hashtable<String,String>getTagsByFileAndID(String tagname,
			String id, String filename,ArrayList<Attrib> atts) throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from "+ tagname + " where id = '" +
				id + "' and file_name = '"+ filename+"';");
		ResultSet rs = stat.executeQuery(query);
		Hashtable<String,String> ht = new Hashtable<String,String>();
		while(rs.next()){
			//for each attribute in the list, get the value and put both in the
			//hashtable ht
			for(int i=0;i<atts.size();i++){
				ht.put(atts.get(i).getName(),rs.getString(atts.get(i).getName()));
			}
		}
		rs.close();
		return ht;
	}

	/**
	 * Gets the names of files that have a tag of a particular type
	 * at a specified location.
	 * 
	 * @param elem the name of the tag being searched for
	 * @param loc the location being searched for
	 * @return ArrayList of file names that have that tag type at that location
	 * 
	 * @throws Exception
	 */
	ArrayList<String>getFilesAtLocbyElement(String elem, int loc)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from extents where location = " +
				loc + " and element_name ='" + elem +"';");
		ResultSet rs = stat.executeQuery(query);
		ArrayList<String> files = new ArrayList<String>();
		while(rs.next()){
			files.add(rs.getString("file_name"));
		}
		rs.close();
		return files;
	}

	/**
	 * Returns an ArrayList of extent tags that exist in a particular file
	 * and are of the type specified by the Elem object.
	 * ArrayList contains the tags in String form that are used for writing out
	 * the XML files.
	 * 
	 * @param file the name of the file the tag is in
	 * @param elem Elem object defining the type of tag being searched for
	 * @returnArrayList of extent tags that exist in a particular file
	 * and are of the type specified by the Elem object.
	 * 
	 * @throws Exception
	 */
	ArrayList<String> getExtentTagsByFileAndType(String file, Elem elem)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from "+elem.getName()+ " where file_name = '" +
				file + "' order by start;");
		ResultSet rs = stat.executeQuery(query);
		ArrayList<String> tags = makeTagStringsForOutput(rs, elem);
		rs.close();
		return tags;
	}

	/**
	 * Returns an ArrayList of link tags that exist in a particular file
	 * and are of the type specified by the Elem object.
	 * ArrayList contains the tags in String form that are used for writing out
	 * the XML files.
	 * 
	 * @param file the name of the file the tag is in
	 * @param elem Elem object defining the type of tag being searched for
	 * @returnArrayList of extent tags that exist in a particular file
	 * and are of the type specified by the Elem object.
	 * 
	 * @throws Exception
	 */
	ArrayList<String> getLinkTagsByFileAndType(String file, Elem elem)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from "+elem.getName()+ " where file_name = '" +
				file + "' order by id;");
		ResultSet rs = stat.executeQuery(query);
		ArrayList<String> tags = makeTagStringsForOutput(rs, elem);
		rs.close();
		return tags;
	}

	/**
	 * Creates strings containing the tag information being searched for
	 * 
	 * 
	 * @param rs the ResultSet of a different method (getLinkTagsByFileAndType or
	 * (getExtentTagsByFileAndType)
	 * 
	 * @param elem the Elem object describing the tags being retrieved
	 * 
	 * @return an ArrayList of Strings containing the tag information
	 */
	private ArrayList<String>makeTagStringsForOutput(ResultSet rs, Elem elem){
		ArrayList<String> tags = new ArrayList<String>();
		ArrayList<Attrib> atts= elem.getAttributes();
		try{
			while(rs.next()){
				String tag = ("<"+elem.getName()+ " ");
				for(int i=0;i<atts.size();i++){
					String attName = atts.get(i).getName();
					String attText = rs.getString(attName);
					//need to get rid of all the information that's
					//not valid in XML
					attText=attText.replace("\n"," ");
					attText=attText.replace("<","&lt;");
					attText=attText.replace(">","&gt;");
					attText=attText.replace("&","&amp;");
					attText=attText.replace("\"","'");
					tag = tag+attName+"=\""+attText+"\" ";
				}
				tag = tag + "/>\n";
				tags.add(tag);
			}
			rs.close();
		}catch(Exception e){
			System.out.println(e);
		}
		return tags;
	}
	
	/**
	 * Retrieves a Hashtable of all the locations in a file where tags exist.
	 * Used when assigning colors to the text when a link is selected
	 * 
	 * @param filename the name of the file the tags are coming from
	 * @return Hashtable with the locations of tags as keys
	 * @throws Exception
	 */
	Hashtable<String,String>getAllExtentsByFile(String filename) 
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select location from extents where file_name = '"+ filename+"';");
		ResultSet rs = stat.executeQuery(query);
		Hashtable<String,String> allLocs = new Hashtable<String,String>();
		while(rs.next()){
			allLocs.put(rs.getString("location"),"");
		}
		rs.close();
		return allLocs;
	}
	
	/**
	 * Returns a HashCollection of locations (as keys) and the file names that have 
	 * a particular type of tag at each location.
	 * 
	 * @param tagname
	 * @return
	 * @throws Exception
	 */
	HashCollection<String,String>getExtentAllLocs(String tagname)
			throws Exception{
		HashCollection<String,String>elems = new HashCollection<String,String>();
		Statement stat = conn.createStatement();
		String query = ("select location, file_name from extents where " +
				"element_name = '" + tagname + "';");
		ResultSet rs = stat.executeQuery(query);
		while(rs.next()){
			elems.putEnt(rs.getString("location"),rs.getString("file_name"));
		}
		rs.close();
		return elems;
	}

	/**
	 * Gets the type of a tag based on its file and ID.  Assumes
	 * that no file will have an ID that is used more than once, 
	 * even for different tags
	 * 
	 * @param file
	 * @param id
	 * @return String containing the element type
	 * @throws Exception
	 */
	String getElementByFileAndID(String file,String id)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from extents where id = '" 
				+ id + "'" + " and file_name =  '" + file + "';");
		ResultSet rs = stat.executeQuery(query);
		String elemName =  rs.getString("element_name");
		rs.close();
		return elemName;
	}

	/**
	 * Used to add a single tag's worth of information to the preparedStatement
	 * for the extent table.  Does not add to the database.
	 * 
	 * @param file_name name of the file the tag is from
	 * @param location the location of the tag in the text
	 * @param element the type of the tag being added
	 * @param id the ID of the tag being added
	 * @throws Exception
	 */
	void add_extent(String file_name, int location, String element_name, String id)
			throws Exception{
		extent_insert.setString(1, file_name);
		extent_insert.setInt(2, location);
		extent_insert.setString(3, element_name);
		extent_insert.setString(4, id);
		extent_insert.addBatch();
	}

	/**
	 * Adds all the inserts that have been batched into the PreparedStatement
	 * for the extent table in the extent table to the database
	 * 
	 * @throws Exception
	 */
	void batchExtents() throws Exception{
		conn.setAutoCommit(false);
		extent_insert.executeBatch();
		conn.setAutoCommit(true);
	}

	/**
	 * Used to add a single tag's worth of information to the preparedStatement
	 * for the extent table and to the database.
	 * 
	 * @param file_name name of the file the tag is from
	 * @param location the location of the tag in the text
	 * @param element the type of the tag being added
	 * @param id the ID of the tag being added
	 * @throws Exception
	 */
	void insert_extent(String file_name, int location, String element, String id)
			throws Exception{
		extent_insert.setString(1, file_name);
		extent_insert.setInt(2, location);
		extent_insert.setString(3, element);
		extent_insert.setString(4, id);
		extent_insert.addBatch();
		conn.setAutoCommit(false);
		extent_insert.executeBatch();
		conn.setAutoCommit(true);
	}

	/**
	 * Adds all the inserts that have been batched into the PreparedStatement
	 * for the link table in the extent table to the database
	 * 
	 * @throws Exception
	 */
	void batchLinks() throws Exception{
		conn.setAutoCommit(false);
		link_insert.executeBatch();
		conn.setAutoCommit(true);
	}

	/**
	 * Used to add a single tag's worth of information to the preparedStatement
	 * for the link table.  Does not add to the database.
	 * 
	 * @param file_name name of the file the added tag is from
	 * @param newID ID of the tag being added
	 * @param linkName type of the tag being added
	 * @param linkFrom the id of the 'from' anchor for the link
	 * @param from_name the type of the 'from' anchor for the link
	 * @param linkTo the id of the 'to' anchor for the link
	 * @param to_name the type of the 'to' anchor for the link
	 * @throws Exception
	 */
	void add_link(String file_name, String newID, String linkName, String linkFrom, 
			String from_name, String linkTo, String to_name) throws Exception{
		link_insert.setString(1, file_name);
		link_insert.setString(2, newID);
		link_insert.setString(3, linkFrom);
		link_insert.setString(4, from_name);
		link_insert.setString(5, linkTo);
		link_insert.setString(6, to_name);
		link_insert.setString(7, linkName);
		link_insert.addBatch();
	}

	/**
	 * Used to add a single tag's worth of information to the preparedStatement
	 * for the link table and to the database.
	 * 
	 * @param file_name name of the file the added tag is from
	 * @param newID ID of the tag being added
	 * @param linkName type of the tag being added
	 * @param linkFrom the id of the 'from' anchor for the link
	 * @param from_name the type of the 'from' anchor for the link
	 * @param linkTo the id of the 'to' anchor for the link
	 * @param to_name the type of the 'to' anchor for the link
	 * @throws Exception
	 */
	void insert_link(String file_name, String newID, String linkName, String linkFrom, 
			String from_name, String linkTo, String to_name) throws Exception{
		link_insert.setString(1, file_name);
		link_insert.setString(2, newID);
		link_insert.setString(3, linkFrom);
		link_insert.setString(4, from_name);
		link_insert.setString(5, linkTo);
		link_insert.setString(6, to_name);
		link_insert.setString(7, linkName);
		link_insert.addBatch();
		conn.setAutoCommit(false);
		link_insert.executeBatch();
		conn.setAutoCommit(true);
	}

	/**
	 * Closes the connection to the DB
	 */
	void close_db(){
		try{
			conn.close();
		}catch(Exception e){
			System.out.println(e.toString());
		}
	}

	/**
	 * Checks to see if the given id exists for the filename.
	 * 
	 * @param id the ID being searched for
	 * @param fileName the name of the file being searched i
	 * @return true if the id exists, false if not
	 * 
	 * @throws Exception
	 */
	boolean idExists(String id, String fileName)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select count(id) from extents where " +
				"id = '" + id + "' and file_name ='"+fileName+"';");
		ResultSet rs = stat.executeQuery(query);
		int num = rs.getInt(1);
		rs.close();
		if (num>0){
			return true;
		}
		String query2 = ("select count(id) from links where " +
				"id = '" + id + 
				"' and file_name ='"+fileName+"';");
		ResultSet rs2 = stat.executeQuery(query2);
		int num2 = rs2.getInt(1);
		rs2.close();
		if (num2>0){
			return true;
		}

		return false;
	}

	/**
	 * Checks to see if the file has a tag at the given location
	 * 
	 * @param file the name of the file being searched
	 * @param loc the location being searched
	 * @return true or false, depending on if there's a tag there
	 * 
	 * @throws Exception
	 */
	boolean tagExistsInFileAtLoc(String file, int loc)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select count(id) from extents where " +
				"location = " + loc + " and file_name ='"+file+"';");
		ResultSet rs = stat.executeQuery(query);
		int num = rs.getInt(1);
		rs.close();
		if(num>0){
			return true;
		}
		return false;
	}
	
	/**
	 * removes a link tag based on file, element name and id.  Is currently
	 * only called for the goldStandard file.
	 * 
	 * @param fullName name of the file the tag is in
	 * @param element_name type of the tag being removed
	 * @param id ID of the tag being removed
	 * 
	 * @throws Exception
	 */
	void removeLinkTags(String fullName, String element_name, String id)
			throws Exception{
		print_other(element_name);
		//remove the tag from the links table
		Statement stat = conn.createStatement();
		String delete = ("delete from links where id = '" 
				+id + "' and element_name = '" 
				+ element_name + "' and file_name = '"+fullName+"';");
		stat.executeUpdate(delete);  
		//also need to remove it from the table associated with its element name
		stat = conn.createStatement();
		delete = ("delete from " + element_name +" where id = '" 
				+id + "' and file_name = '"+fullName+"';");
		stat.executeUpdate(delete);
	}

	/**
	 * removes an extent tag based on file, element name and id.  Is currently
	 * only called for the goldStandard file.
	 * 
	 * @param fullName name of the file the tag is in
	 * @param element_name type of the tag being removed
	 * @param id ID of the tag being removed
	 * 
	 * @throws Exception
	 */
	void removeExtentTags(String fullName, String element_name, String id)
			throws Exception{
		//remove the tag from the extents table
		Statement stat = conn.createStatement();
		String delete = ("delete from extents where id = '" 
				+id + "' and element_name = '" 
				+ element_name + "' and file_name = '"+fullName+"';");
		stat.executeUpdate(delete); 
		
		//also need to remove it from the element_name table
		stat = conn.createStatement();
		delete = ("delete from " + element_name +" where id = '" 
				+id + "' and file_name = '"+fullName+"';");

		//finally, remove it from the overlap_extents
		if(fullName.equals("goldStandard.xml")){
			stat = conn.createStatement();
			delete = ("delete from extent_overlaps where gsid = '" 
					+id + "' and element_name = '"+element_name+"';");
			stat.executeUpdate(delete);
		}
	}


	/**
	 * Checks to see if the provided tag overlaps with tags of the same type
	 * from other files.
	 * 
	 * @param fullname the name of the file being checked for overlaps,
	 * currently only used for the goldStandard file
	 * @param e the element describing the tag being added
	 * @param tag the Hashtable of tag information
	 * 
	 * @throws Exception
	 */
	void add_overlaps(String fullname, Elem e, Hashtable<String,String> tag)
			throws Exception{
		Statement stat = conn.createStatement();
		int start = Integer.parseInt(tag.get("start"));
		int end = Integer.parseInt(tag.get("end"));
		String gsid = tag.get("id");

		String query = ("select distinct(id), file_name from extents where "+
				"element_name = '" + e.getName() +"' and location >= " 
				+ start + " and location <=" + end 
				+ " and file_name !='goldStandard.xml';");
		ResultSet rs = stat.executeQuery(query);
		while (rs.next()){
			String filename = rs.getString("file_name");
			String id = rs.getString("id");
			overlap_insert.setString(1,gsid);
			overlap_insert.setString(2,e.getName());
			overlap_insert.setString(3,filename);
			overlap_insert.setString(4,id);
			overlap_insert.addBatch();
		}
		rs.close();
		conn.setAutoCommit(false);
		overlap_insert.executeBatch();
		conn.setAutoCommit(true);

	}

	/**
	 * Finds all the overlaps with the goldStandard and other files.
	 * Called when a new goldStandard file is loaded into MAI
	 * 
	 * @throws Exception
	 */
	void findAllOverlaps() 
			throws Exception{
		//first, clear out the table
		Statement stat = conn.createStatement();
		String delete = ("delete from extent_overlaps;");
		stat.executeUpdate(delete); 
		//then, find the ids and types of the GS links
		String findGSIDs = ("select distinct(id), element_name from extents where file_name = 'goldStandard.xml';");
		ResultSet rs = stat.executeQuery(findGSIDs);
		while(rs.next()){
			String e_name = rs.getString("element_name");
			String gsid = rs.getString("id");
			int start = (getStartOrEnd(rs.getString("id"), "goldStandard.xml", 
					e_name,"start"));
			int end = (getStartOrEnd(rs.getString("id"), "goldStandard.xml", 
					e_name,"end"));
			Statement stat2 = conn.createStatement();
			//then, find the tags from other files that overlap with the one in the GS
			String query = ("select distinct(id), file_name from extents where "+
					"element_name = '" + e_name +"' and location >= " 
					+ start + " and location <=" + end 
					+ " and file_name !='goldStandard.xml';");
			ResultSet rs2 = stat2.executeQuery(query);
			while (rs2.next()){
				String filename = rs2.getString("file_name");
				String id = rs2.getString("id");
				overlap_insert.setString(1,gsid);
				overlap_insert.setString(2,e_name);
				overlap_insert.setString(3,filename);
				overlap_insert.setString(4,id);
				overlap_insert.addBatch();
			}
			rs2.close();

		}
		rs.close();
		//add the tags to the table
		conn.setAutoCommit(false);
		overlap_insert.executeBatch();
		conn.setAutoCommit(true);

	}

	/**
	 * gets the start and end locations of a tag by the file and id,
	 * concatenated into a string: start,end
	 * 
	 * @param file the file the tag is in
	 * @param id the id of the tag
	 * @return
	 * @//add the tags to the tablethrows Exception
	 */
	String getLocByFileAndID(String file,String id)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select * from extents where id = '" + 
				id + "' and file_name = '" + file + "';");
		ResultSet rs = stat.executeQuery(query);
		ArrayList<Integer>locs = new ArrayList<Integer>();
		while (rs.next()){
			locs.add(Integer.parseInt(rs.getString("location")));
		}
		//sort the ArrayList
		Collections.sort(locs);
		rs.close();
		//return the first and last elements in the list as a string
		return locs.get(0)+","+(locs.get(locs.size()-1));

	}

	/**
	 * Returns the start or the end of the string, whichever is asked for
	 * 
	 * @param id the id of the tag
	 * @param filename the name of the file the tag is in
	 * @param elemname the type of the tag
	 * @param attribute the attribute being searched for, here only used for "start" or "end"
	 * 
	 * @return
	 */
	private int getStartOrEnd(String id, String filename, String elemname, String attribute){
		try{
			Statement stat = conn.createStatement();
			String query = ("select * from " + elemname + " where file_name = '" +
					filename + "' and id = '" + id + "';");
			ResultSet rs = stat.executeQuery(query);
			String att = rs.getString(attribute);
			rs.close();
			return(Integer.parseInt(att));
		}catch(Exception e){
			System.out.println(e.toString());
			return -2;
		}
	}

	/**
	 * Gets all instances of the requested link tag that exist in the gold standard.
	 * This method is called when a link tag is selected from the radio buttons in MAI.
	 * This is a complicated procedure, because it has to determine what link tags from the 
	 * files being adjudicated have anchors that overlap with extents in the gold standard, and 
	 * return a list of the locations in the gold standard that overlap with the link anchors 
	 * from the other files.
	 * 
	 * It also fills in the information in the currentLinks hash, which keeps track of what links 
	 * from other files have overlaps so that when extents are selected the process of filling in the
	 * adjudication table is sped up.
	 * 
	 * TODO: refactor to make more efficient, use multiple database connections
	 * 
	 * @param tagname the name of the tag being evaluated
	 * 
	 * @return a hashcollection with all the locations and file names where
	 * link anchors overlap with the gold standard
	 * @throws Exception
	 */
	HashCollection<String,String> getGSLinksByType(String tagname)
			throws Exception{

		//keep track of relevant links, reset each time a 
		//new link tag is selected
		currentLinks.clear(); 

		HashCollection<String,String>links = new HashCollection<String,String>();

		Statement stat = conn.createStatement();
		String query = ("select * from links where element_name = '" +
				tagname + "';");
		ResultSet rs = stat.executeQuery(query);

		Hashtable<String,String>inGS = new Hashtable<String,String>();
		Hashtable<String,String>inOther = new Hashtable<String,String>();

		while(rs.next()){
			//this needs to be re-written
			if(rs.getString("file_name").equals("goldStandard.xml")){
				String newid = (rs.getString("file_name")+"@#@"+rs.getString("fromid")
						+"@#@"+rs.getString("from_name"));
				inGS.put(newid,"");
				newid = (rs.getString("file_name")+"@#@"+rs.getString("toid")
						+"@#@"+rs.getString("to_name"));
				inGS.put(newid,"");
				currentLinks.add(rs.getString("file_name")+"@#@"+rs.getString("id"));
			}
			else{
				//if the link isn't in the GS, we only want to highlight
				//these extents if they both have overlaps in the GS
				String newid = (rs.getString("file_name")+"@#@"+rs.getString("fromid")
						+"@#@"+rs.getString("from_name")+"@#@"+
						rs.getString("file_name")+"@#@"+rs.getString("toid")
						+"@#@"+rs.getString("to_name")+"@#@"+rs.getString("id"));
				inOther.put(newid,"");
			}

		}
		rs.close();


		for(Enumeration<String> ids = inOther.keys() ; ids.hasMoreElements() ;) {
			//if the ids being examined don't come from a GS link,
			//we need to get the corresponding GS id (based on overlaps)
			//both IDs must have overlaps for either to be included 
			//in the hashtable
			String id = (String)ids.nextElement();
			boolean hasToOverlap = false;
			boolean hasFromOverlap = false;
			ArrayList<String> overlaps = new ArrayList<String>();
			String filename = id.split("@#@")[0];
			String filetagid = id.split("@#@")[1];

			query = ("select gsid from extent_overlaps where element_name ='" +
					id.split("@#@")[2]+"' and file_name ='" + filename + 
					"'and fileid='" + filetagid +"';"); 

			rs=stat.executeQuery(query);
			while (rs.next()){
				String newid = (filename+"@#@"+rs.getString("gsid")
						+"@#@"+id.split("@#@")[2]);
				overlaps.add(newid);
				hasToOverlap = true;
			}
			rs.close();

			filetagid = id.split("@#@")[4];
			query = ("select gsid from extent_overlaps where element_name ='" +
					id.split("@#@")[5]+"' and file_name ='" + filename + 
					"'and fileid='" + filetagid +"';"); 

			rs=stat.executeQuery(query);
			while (rs.next()){
				String newid = (filename+"@#@"+rs.getString("gsid")
						+"@#@"+id.split("@#@")[5]);
				overlaps.add(newid);
				hasFromOverlap = true;
			}
			rs.close();

			if (hasToOverlap && hasFromOverlap){
				for(int i=0;i<overlaps.size();i++){
					inGS.put(overlaps.get(i),"");
					currentLinks.add(filename+"@#@"+id.split("@#@")[6]);
				}
			}

		}

		//now that we have all the overlapping GS ids, we can 
		for (Enumeration<String> ids = inGS.keys() ; ids.hasMoreElements() ;) {
			String id = (String)ids.nextElement();
			String filename = id.split("@#@")[0];
			query = ("select location from extents where file_name = '" +
					"goldStandard.xml" + "' and element_name = '" + id.split("@#@")[2] + 
					"' and id = '" + id.split("@#@")[1] + "';");
			rs = stat.executeQuery(query);
			while (rs.next()){
				links.putEnt(rs.getString("location"),filename);
			}
			rs.close();

		}

		return links;

	}

	/**
	 * Returns a HashCollection of link IDs from a file where the given extent id is an anchor
	 * 
	 * @param file the name of the file being searched
	 * @param element_name the type of element being searched for in the anchor
	 * @param id the id of the anchor being searched for
	 * @return A HashCollection where the keys are the type of element and the 
	 * values are link IDs
	 * @throws Exception
	 */
	HashCollection<String,String> getLinksByFileAndExtentID(String file, String element_name, String id)
			throws Exception{
		HashCollection<String,String>links = new HashCollection<String,String>();
		//first get the links where the extent being searched for is the 
		//'from' anchor
		Statement stat = conn.createStatement();
		String query = ("select id,element_name from links where fromid = '" +
				id + "' and from_name  ='" + element_name + "' and file_name = '"+
				file + "';");
		ResultSet rs = stat.executeQuery(query);
		while(rs.next()){
			links.putEnt(rs.getString("element_name"),rs.getString("id"));
		}
		rs.close();
		//then get the ones where the extent is the 'to' anchor
		String query2 = ("select id,element_name from links where toid = '" +
				id + "' and to_name  ='" + element_name + "' and file_name = '"+
				file + "';");
		ResultSet rs2 = stat.executeQuery(query2);
		while(rs2.next()){
			links.putEnt(rs2.getString("element_name"),rs2.getString("id"));
		}
		rs2.close();
		return links;
	}

	/**
	 * Finds the tags of a particular type that exist in an extent and 
	 * returns the filenames as keys and the IDs as values.  Used to fill
	 * in the table in MAI when an extent is selected.
	 * 
	 * @param begin the starting offset of the selected extent
	 * @param end the ending offset of the selected extent
	 * @param tagName the type of tag being searched for
	 * @return HashCollection with file names as keys and IDs as values
	 * 
	 * @throws Exception
	 */
	HashCollection<String,String> getTagsInSpanByType(int begin, int end, String tagName)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = "";
		if(begin!=end){
			query = ("select distinct(id), file_name from extents " +
					"where element_name = '"+tagName+"' and location >= " 
					+ begin + " and location <=" + end + ";");
		}
		else{
			query = ("select distinct(id), file_name from extents where location = " 
					+ begin + " and element_name = '"+tagName+"';");
		}

		ResultSet rs = stat.executeQuery(query);
		HashCollection<String,String> tags = new HashCollection<String,String>();
		while(rs.next()){
			tags.putEnt(rs.getString("file_name"),rs.getString("id"));
		}
		rs.close();
		return tags;
	}

	/**
	 * This function is called when a user selects an extent in the text window
	 * and returns the tag information that will be used to fill in the table.
	 * While links are collected from all the files entered into the adjudication,
	 * the text and extent IDs that are shown in the links are ones from the GoldStandard.
	 * This helps ensure that only links with both anchors in (or at least overlapping with
	 * the goldStandard are displayed, but it does make the function much more complicated.
	 *  
	 * <p>
	 * This method also tracks the locations of the extents that should be 
	 * highlighted in the text to reflect where the annotations and gold standard 
	 * placed the other extents associated with the selected text.  This information 
	 * is kept in the currentHighlights hash.
	 * <p>
	 * TODO: refactor, use more nested DB queries, remove assumption about
	 * link ends only having one tag per document
	 * <p>
	 * @param begin the beginning offset of the selected extent
	 * @param end the ending offset of the selected extent
	 * @param tagname the type of tag being searched for
	 * @param atts the attributes of the tag being searched for
	 * @return a HashCollection of tag information, keyed by filename
	 * @throws Exception
	 */
	HashCollection<String,Hashtable<String,String>> getLinkTagsInSpanByType
	(int begin, int end, String tagname,ArrayList<Attrib> atts) throws Exception{
		//based on the new selection, the highlights in the text window will
		//be changed; keep track of those here
		currentHighlights.clear();

		HashCollection<String,Hashtable<String,String>> gsLinkExtents = 
				new HashCollection<String,Hashtable<String,String>>();

		HashCollection<String,Hashtable<String,String>> gsTempLinkExtents = 
				new HashCollection<String,Hashtable<String,String>>();

		Statement stat = conn.createStatement();

		for(int i=0;i<currentLinks.size();i++){
			String link = currentLinks.get(i);
			String filename = link.split("@#@")[0];
			String linkid = link.split("@#@")[1];

			//first, grab the info for each of the links that are being considered
			String query = ("select * from "+ tagname + " where id = '" +
					linkid + "' and file_name = '"+ filename+"';");
			ResultSet rs = stat.executeQuery(query);
			Hashtable<String,String> linkelems = new Hashtable<String,String>();
			while (rs.next()){
				for(int j=0;j<atts.size();j++){
					linkelems.put(atts.get(j).getName(),rs.getString(atts.get(j).getName()));
				}
				//use TempLinkExtents so that only links with overlaps will be
				//passed back
				gsTempLinkExtents.putEnt(filename,linkelems);
			}
			rs.close();
		}


		//next, go through each link, check to see if either end overlaps with
		//the selected text, then
		//find the GS replacements for the From
		ArrayList<String> filenames = gsTempLinkExtents.getKeyList();
		for(int i = 0;i<filenames.size();i++){
			String filename = filenames.get(i);
			ArrayList<Hashtable<String,String>> links = gsTempLinkExtents.get(filename);
			for(int j=0;j<links.size();j++){
				boolean overlap = false;
				Hashtable<String,String> link = links.get(j);
				String fromid = link.get("fromID");

				//need to get type of extent to ensure compatibility
				String query = ("select * from extents where file_name = '" 
						+ filename + "' and id = '" + fromid + "';");
				ResultSet rs = stat.executeQuery(query);
				String fromElemName = rs.getString("element_name");
				rs.close();

				//check to see if fromID overlaps with selected text
				query = ("select start, end from " + fromElemName + 
						" where id = '" + fromid + "' and file_name = '" +
						filename + "';");
				rs = stat.executeQuery(query);
				int fromStart = Integer.parseInt(rs.getString("start"));
				int fromEnd = Integer.parseInt(rs.getString("end"));
				rs.close();

				boolean fromOverlap = false;
				if((fromStart >= begin && fromStart<=end) || 
						(fromEnd >= begin && fromEnd <=end) ||
						(fromStart<=begin && fromEnd>=end)){
					overlap=true;
					fromOverlap = true;
				}

				//do the same for the toID
				//need to get type of extent to ensure compatibility
				String toid = link.get("toID");
				query = ("select * from extents where file_name = '" 
						+ filename + "' and id = '" + toid + "';");
				rs = stat.executeQuery(query);
				String toElemName = rs.getString("element_name");
				rs.close();

				//check to see if toID overlaps with selected text
				query = ("select start, end from " + toElemName + 
						" where id = '" + toid + "' and file_name = '" +
						filename + "';");
				rs = stat.executeQuery(query);
				int toStart = Integer.parseInt(rs.getString("start"));
				int toEnd = Integer.parseInt(rs.getString("end"));
				rs.close();
				boolean toOverlap = false;
				if((toStart >= begin && toStart<=end) || 
						(toEnd >= begin && toEnd <=end) ||
						(toStart<=begin && toEnd>=end)){
					overlap=true;
					toOverlap = true;
				}

				//if there's an overlap, proceed with replacing the ids and text
				if(overlap){
					//add overlaps to currentHighlights
					if (!fromOverlap){
						currentHighlights.putEnt(filename,fromStart+"@#@"+fromEnd);
					}
					if (!toOverlap){
						currentHighlights.putEnt(filename,toStart+"@#@"+toEnd);
					}

					//first, swap out fromID and fromText
					query = ("select distinct(id) from extents " +
							"where element_name = '"+fromElemName+"' and file_name = '" +
							"goldStandard.xml" + "' and location >= " 
							+ fromStart + " and location <=" + fromEnd + ";");

					rs = stat.executeQuery(query);
					//NOTE: assumes there will be a one-to-one overlap
					//may need to be fixed in later versions
					String newFromID = rs.getString("id");
					rs.close();
					links.get(j).put("fromID",newFromID);

					String newFromText = "";
					try{
						newFromText = getTextByFileElemAndID("goldStandard.xml",fromElemName,newFromID);
					}catch(Exception e){
					}

					links.get(j).put("fromText",newFromText);

					//now, do the same for toID and toText

					//get location of toID extent
					query = ("select distinct(id) from extents " +
							"where element_name = '"+toElemName+"' and file_name = '" +
							"goldStandard.xml" + "' and location >= " 
							+ toStart + " and location <=" + toEnd + ";");

					rs = stat.executeQuery(query);
					//NOTE: assumes there will be a one-to-one overlap
					//may need to be fixed in later versions
					String newToID = rs.getString("id");
					rs.close();
					links.get(j).put("toID",newToID);
					String newToText = "";
					try{
						newToText = getTextByFileElemAndID("goldStandard.xml",toElemName,newToID);
					}catch(Exception e){
					}
					links.get(j).put("toText", newToText);

					//add new link info to HashCollection being sent back to MAI
					gsLinkExtents.putEnt(filename,links.get(j));

				}

			}

		}
		return gsLinkExtents;
	}

	/**
	 * Retrieves the text of an extent tag by using the id, type, and file
	 * of the tag
	 * 
	 * @param file name of the file the tag is in
	 * @param elem type of the tag being searched for
	 * @param id ID of the tag
	 * @return the text of the tag
	 * @throws Exception
	 */
	String getTextByFileElemAndID(String file, String elem, String id)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = ("select text from " + elem + 
				" where file_name = '"+ file +"' and id = '"
				+ id + "';");
		ResultSet rs = stat.executeQuery(query);
		String text = rs.getString("text");
		rs.close();
		return text;

	}

	/**
	 * Returns IDs of all the tags located over an extent, as well as 
	 * all non-consuming tags from that file.
	 * 
	 * @param file the file being searched
	 * @param begin the beginning of the selected extent
	 * @param end the end of the selected extent
	 * @return a HashCollection of tag information keyed by element_name
	 * @throws Exception
	 */
	HashCollection<String,String> getFileTagsInSpanAndNC(String file,int begin, int end)
			throws Exception{
		Statement stat = conn.createStatement();
		String query = "";
		if(begin!=end){
			query = ("select distinct(id), element_name from extents where location >= " 
					+ begin + " and location <=" + end + " and file_name ='" + file + "';");
		}
		else{
			query = ("select distinct(id), element_name from extents where location = " 
					+ begin + " and file_name = '" + file+ "';");
		}

		ResultSet rs = stat.executeQuery(query);
		HashCollection<String,String> tags = new HashCollection<String,String>();
		while(rs.next()){
			tags.putEnt(rs.getString("element_name"),rs.getString("id"));
		}
		rs.close();

		//now get the non-consuming tags
		query = ("select distinct(id), element_name from extents where location = -1;");
		rs = stat.executeQuery(query);
		while(rs.next()){
			tags.putEnt(rs.getString("element_name"),rs.getString("id"));
		}
		rs.close();

		return tags;
	}

	/**
	 * Returns the HashCollection currentHighlights for use 
	 * in highlighting the appropriate extents in MAI's text area.
	 * 
	 * @return
	 */
	public HashCollection<String,String>getCurrentHighlights(){
		return currentHighlights;
	}

	//Below are a series of methods for printing the information in the
	//database.  They aren't terribly useful because it's easier and
	//more accureate to check the DB through the command line if necessary,
	//but they're nice to have around.

	/**
	 * Prints the extent table
	 */
	public void print_extents(){
		System.out.println("Extents in DB:");
		try{
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from extents;");
			while (rs.next()) {
				System.out.println("file name = " + rs.getString("file_name"));
				System.out.println("location = " + rs.getString("location"));
				System.out.println("element = " + rs.getString("element_name"));
				System.out.println("id = " + rs.getString("id"));
			}
			rs.close();
		}catch(Exception e){
			System.out.println(e.toString());
		}

	}

	/**
	 * Prints the unique ids in the extent table
	 */
	public void print_unique_extents(){
		System.out.println("Extents in DB:");
		try{
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select distinct(id), file_name, element_name from extents;");
			while (rs.next()) {
				System.out.println("file name = " + rs.getString("file_name"));
				System.out.println("element_name = " + rs.getString("element_name"));
				System.out.println("id = " + rs.getString("id"));
			}
			rs.close();
		}catch(Exception e){
			System.out.println(e.toString());
		}

	}

	/**
	 * Prints the links in the DB
	 */
	public void print_links(){
		System.out.println("Links in DB:");
		//links (id,fromid,from_name,toid,to_name,element_name);");
		try{
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from links;");
			while (rs.next()) {
				System.out.println("file name = " + rs.getString("file_name"));
				System.out.println("id = " + rs.getString("id"));
				System.out.println("from = " + rs.getString("fromid"));
				System.out.println("from_name = " + rs.getString("from_name"));
				System.out.println("to = " + rs.getString("toid"));
				System.out.println("to_name = " + rs.getString("to_name"));
				System.out.println("element_name = " + rs.getString("element_name"));
			}
			rs.close();
		}catch(Exception e){
			System.out.println(e.toString());
		}

	}

	/**
	 * Prints the extent_overlaps table
	 */
	public void print_overlaps(){
		System.out.println("\nExtent overlaps:");
		try{
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from extent_overlaps;");
			while (rs.next()) {
				System.out.println("gsid = " + rs.getString("gsid"));
				System.out.println("element_name = " + rs.getString("element_name"));
				System.out.println("file_name = " + rs.getString("file_name"));
				System.out.println("fileid = " + rs.getString("fileid"));
			}
			rs.close();
		}
		catch(Exception e){
			System.out.println(e.toString());
		}
	}

	/**
	 * Prints basic information about the table with the 
	 * provided tag name.
	 * 
	 * @param extent_name the name of the tag/table to be printed.
	 */
	private void print_other(String extent_name){
		System.out.println("\nTag info:");
		try{
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from "+ extent_name + ";");
			while (rs.next()) {
				System.out.println("id = " + rs.getString("id"));
				System.out.println("file_name = " + rs.getString("file_name"));
			}
			rs.close();
		}
		catch(Exception e){
			System.out.println(e.toString());
		}
	}

}
