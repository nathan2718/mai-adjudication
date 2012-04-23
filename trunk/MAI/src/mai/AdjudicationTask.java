
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

import java.util.*;

/** 
 * AdjudicationTask serves as a go-between for MaiGui and the 
 * SQLite interface adjudDB, and also manages the ID 
 * assignments for the gold standard file.
 * <p>
 * The majority of methods in this file just provide error 
 * catching for the methods in AdjudDB.
 * 
 * @author Amber Stubbs 
 * @version 0.7 April 19, 2012
 */


class AdjudicationTask {

	private Hashtable<String,Elem> elements;
	private Hashtable<String,AttID> idTracker;

	private AdjudDB tagTable;
	private DTD dtd;
	private boolean hasDTD;

	/**
	 * Creates a new AdjudicationTask object and accompanying database
	 */
	AdjudicationTask(){
		tagTable = new AdjudDB();
		hasDTD = false;
	}

	/**
	 * resets the database
	 */
	void reset_db(){
		tagTable.close_db();
		tagTable = new AdjudDB();
	}

	/**
	 * Clears the idTracker hashtable
	 */
	void reset_IDTracker(){
		idTracker = createIDTracker();
	}

	/**
	 * Calls the DB to create all the necessary tables
	 */
	void addDTDtoDB(){
		tagTable.addDTD(dtd);
	}

	/**
	 * 
	 * @param fullName
	 * @param newTags
	 */
	void addTagsFromHash(String fullName, 
			HashCollection<String,Hashtable<String,String>> newTags){
		tagTable.addTagsFromHash(fullName, dtd, newTags);

	}
	/**
	 * called when a goldStandard file is added to the task
	 */
	void findAllOverlaps(){
		try{
			tagTable.findAllOverlaps();
		}
		catch(Exception e){
			System.out.println("help, error finding extent overlaps!");
			System.out.println(e.toString());
		}


	}

	/**
	 * Adds a tag (with information about attribute values in a Hashtable)
	 * to the database
	 * 
	 * @param fullName name of the file the tag is from
	 * @param e the type of Elem (tag) being added
	 * @param tag Hashtable with information about the tag
	 */
	void addTagFromHash(String fullName,Elem e, Hashtable<String,String> tag){
		if (e instanceof ElemExtent){
			tagTable.usePreparedExtentStatements(fullName, e,tag);
			try{
				tagTable.batchExtents();
			}catch(Exception ex){
				System.out.println("help, error in batch extents!");
				System.out.println(ex.toString());
			}
			try{
				tagTable.batchElement(e);
			}catch(Exception ex){
				System.out.println("help, error in batchelement extent!");
				System.out.println(ex.toString());
			}
			//also, check for overlaps and add them to the extent_overlaps table
			if(fullName.equals("goldStandard.xml")){
				try{
					tagTable.add_overlaps(fullName,e,tag);
				}catch(Exception exe){
					System.out.println("help, error in finding extent overlaps!");
					System.out.println(exe.toString());
				}
			}
		}
		else if (e instanceof ElemLink){
			tagTable.usePreparedLinkStatements(fullName, e, tag);
			try{
				tagTable.batchLinks();
			}catch(Exception ex){
				System.out.println("help, error in batchLinks link!");
				System.out.println(ex.toString());
			}
			try{
				tagTable.batchElement(e);
			}catch(Exception ex){
				System.out.println("help, error in batchElement link!");
				System.out.println(ex.toString());
			}
		}
		else{
			System.out.println("error!  element type not found");
		}
	}


	/**
	 * Creates the hastable of DTD Elements
	 * @return
	 */
	private Hashtable<String,Elem> createHash(){
		Hashtable<String,Elem> es=new Hashtable<String,Elem>();
		ArrayList<Elem>elems = dtd.getElements();
		for(int i=0;i<elems.size();i++){
			es.put(elems.get(i).getName(),elems.get(i));
		} 
		return(es);
	}

	/**
	 * The IDTracker hashtable keeps one ID for each element that
	 * has an ID, and increments the number so that no two 
	 * tags of the same type will have the same ID.  In MAI this 
	 * is used only for the Gold Standard file (other files can't be edited and 
	 * are assumed to already have only one tag per ID)
	 * 
	 * @return
	 */
	private Hashtable<String,AttID> createIDTracker(){
		Hashtable<String,AttID> ids = new Hashtable<String,AttID>();
		ArrayList<Elem>elems = dtd.getElements();
		for(int i=0;i<elems.size();i++){
			ArrayList<Attrib> attribs = elems.get(i).getAttributes();
			for(int j=0;j<attribs.size();j++){
				if (attribs.get(j) instanceof AttID){
					AttID oldid = (AttID)attribs.get(j);
					AttID id = new AttID(oldid.getName(),
							oldid.getPrefix(),true);
					id.setNumber(0);
					ids.put(elems.get(i).getName(),id);
				}
			}
		}        
		return ids;
	}

	/**
	 * Finds the next available ID for an element and returns it.
	 * 
	 * @param element tag type
	 * @param fileName name of the file the ID is for
	 * @return the next ID for that element
	 */
	String getNextID(String element,String fileName){
		AttID id = idTracker.get(element);
		String nextid = id.getID();
		id.incrementNumber();
		//check to see if nextid is already in db
		//this will catch cases where two tags have
		//the same prefix
		try{
			while(tagTable.idExists(nextid,fileName)){
				nextid = id.getID();
				id.incrementNumber();
			}
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return nextid;

	}


	HashCollection<String,String> getExtentAllLocs(String tagname){
		try{
			return(tagTable.getExtentAllLocs(tagname));
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return (new HashCollection<String,String>());

	}

	ArrayList<String> getFilesAtLocbyElement(String elem, int loc){
		try{
			ArrayList<String>files = tagTable.getFilesAtLocbyElement(elem,loc);
			return files;
		}catch(Exception e){
			System.out.println(e.toString());
			return null;
		}
	}

	ArrayList<String> getExtentTagsByFileAndType(String file, Elem elem){
		try{
			ArrayList<String>tags = tagTable.getExtentTagsByFileAndType(file,elem);
			return tags;
		}catch(Exception e){
			System.out.println(e.toString());
			return null;
		}
	}

	ArrayList<String> getLinkTagsByFileAndType(String file, Elem elem){
		try{
			ArrayList<String>tags = tagTable.getLinkTagsByFileAndType(file,elem);
			return tags;
		}catch(Exception e){
			System.out.println(e.toString());
			return null;
		}
	}

	String getTextByFileElemAndID(String file, String elem, String id){
		String text = "";
		try{
			text = tagTable.getTextByFileElemAndID(file,elem,id);
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return text;
	}

	Hashtable<String,String> getAllExtentsByFile(String file){
		Hashtable<String,String> allExtents = new Hashtable<String,String>();
		try{
			allExtents = tagTable.getAllExtentsByFile(file);
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return allExtents;
	}

	boolean tagExistsInFileAtLoc(String file, int loc){
		try{
			return tagTable.tagExistsInFileAtLoc(file,loc);
		}catch(Exception e){
			System.out.println("help!");
			System.out.println(e.toString());
			return false;
		}
	}


	Hashtable<String,String> getTagsByFileAndID(String tagname,
			String id, String filename){
		try{
			ArrayList<Attrib> atts = dtd.getElem(tagname).getAttributes();
			return tagTable.getTagsByFileAndID(tagname,id,filename,atts);
		}catch(Exception e){
			System.out.println(e.toString());
			return null;
		}
	}

	String getLocByFileAndID(String file, String id){  
		try{
			String loc = tagTable.getLocByFileAndID(file,id);
			return loc;
		}catch(Exception e){
			System.out.println(e.toString());
			return null;
		}
	}

	HashCollection<String,String>findGoldStandardLinksByType(String tagname){
		try{
			HashCollection<String,String> gslinks = tagTable.getGSLinksByType(tagname);
			return gslinks;
		}catch(Exception e){
			System.out.println(e);
		}
		return new HashCollection<String,String>();

	}

	void removeExtentByFileAndID(String fullName,String e_name,String id){
		try{
			tagTable.removeExtentTags(fullName,e_name,id);
		}catch(Exception e){
			System.out.println(e.toString());
		}
	}

	void removeLinkByFileAndID(String fullName,String e_name,String id){
		try{
			tagTable.removeLinkTags(fullName,e_name,id);
		}catch(Exception e){
			System.out.println(e.toString());
		}
	}


	HashCollection<String,String> getLinksByFileAndExtentID(String file,String e_name,String id){
		try{
			return(tagTable.getLinksByFileAndExtentID(file,e_name,id));
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return (new HashCollection<String,String>());
	}

	/**
	 * Sets the DTD for the Adjudication Task so 
	 * information about the files being adjudicated 
	 * are easily available.
	 * 
	 * @param d the object describing the task's DTD
	 */
	public void setDTD(DTD d){
		dtd=d;
		elements = createHash();
		idTracker = createIDTracker();
		hasDTD=true;
	}

	/**
	 * Returns all the Elem objects in the DTD
	 * 
	 * @return
	 */
	public ArrayList<Elem> getElements(){
		return dtd.getElements();
	}



	HashCollection<String,String> getTagsSpanByType(int begin, int end, 
			String tag){
		try{
			return (tagTable.getTagsInSpanByType(begin,end,tag));
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return null;
	}

	HashCollection<String,Hashtable<String,String>> getLinkTagsSpanByType
	   (int begin, int end, String tagname){
		try{
			ArrayList<Attrib> atts = dtd.getElem(tagname).getAttributes();
			return (tagTable.getLinkTagsInSpanByType(begin,end,tagname,atts));
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return null;

	}


	HashCollection<String,String> getFileTagsSpanAndNC(String file, int begin, int end){
		try{
			return (tagTable.getFileTagsInSpanAndNC(file, begin,end));
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return null;
	}


	public HashCollection<String,String>getCurrentHighlights(){
		return tagTable.getCurrentHighlights();
	}

	public ArrayList<String> getExtentElements(){
		ArrayList<String> extents = new ArrayList<String>();
		ArrayList<Elem> elems = dtd.getElements();
		for(int i=0;i<elems.size();i++){
			Elem e = elems.get(i);
			if(e instanceof ElemExtent){
				extents.add(e.getName());
			}
		}
		return extents;
	}
	
	/**
	 * Returns only non-consuming elements in the DTD
	 * 
	 * @return
	 */
	public ArrayList<Elem> getNCElements(){
		return dtd.getNCElements();
	}

	public ArrayList<String> getLinkElements(){
		ArrayList<String> links = new ArrayList<String>();
		ArrayList<Elem> elems = dtd.getElements();
		for(int i=0;i<elems.size();i++){
			Elem e = elems.get(i);
			if(e instanceof ElemLink){
				links.add(e.getName());
			}
		}
		return links;
	}

	public Hashtable<String,Elem> getElemHash(){
		return elements;
	}

	Elem getElem(String name){
		return elements.get(name);
	}

	boolean hasDTD(){
		return hasDTD;
	}

	public String getDTDName(){
		return dtd.getName();
	}

}