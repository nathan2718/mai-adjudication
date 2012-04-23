
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

/**
 * FileOperations handles turning the MAI databases into XML, UTF-8 encoded files.
 * 
 * @author Amber Stubbs
 */

import java.io.*;
import java.util.*;
import javax.swing.*;
import java.io.File;

class FileOperations {

	/**
	 * Checks to see if there are tags in a file
	 * @param f the file being checked 
	 * @return true if the file has tags (in the MAE/MAI format), false if not
	 * 
	 * @throws Exception
	 */
	public static boolean areTags(File f) throws Exception{
		Scanner scan = new Scanner(f,"UTF-8");
		while (scan.hasNextLine()){
			String line = scan.nextLine();
			if(line.equals("<TAGS>")==true){
				scan.close();
				return true;
			}
		}
		scan.close();
		return false;
	}


	/**
	 * Writes the current goldStandard to a file
	 * 
	 * @param f the file being written
	 * @param pane the pane containing the text being adjudicated
	 * @param adjudicationTask the interface with the database
	 */
	public static void saveAdjudXML(File f, JTextPane pane, 
			AdjudicationTask adjudicationTask){

		String paneText = pane.getText();
		ArrayList<Elem> elements = adjudicationTask.getElements();
		String dtdName = adjudicationTask.getDTDName();
		try{
			//first, create the OutputStreamWriter and write the header information
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(f),"UTF-8");
			String t = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n";
			t = t + "<"+dtdName+">\n";
			t = t + "<TEXT><![CDATA[";
			fw.write(t,0,t.length());
			//then, write what's in the text
			fw.write(paneText,0,paneText.length());
			t = "]]></TEXT>\n";
			fw.write(t,0,t.length());
			String s = "<TAGS>\n";
			fw.write(s,0,s.length());
			//now to put in the tags
			for(int i=0;i<elements.size();i++){
				if(elements.get(i) instanceof ElemExtent){
					//get tags of this type from the GoldStandard
					ArrayList<String>tags = adjudicationTask.getExtentTagsByFileAndType("goldStandard.xml",elements.get(i));
					arrayWrite(tags,fw);
				}
				else{
					ArrayList<String>tags = adjudicationTask.getLinkTagsByFileAndType("goldStandard.xml",elements.get(i));
					arrayWrite(tags,fw);
				}
			}

			s = "</TAGS>\n</"+dtdName+">";
			fw.write(s,0,s.length());
			fw.close();
		}catch(Exception ex){
			System.out.println(ex.toString());
		}
	}
	
	/**
	 * writes the contents of an array to the file
	 * 
	 * @param tags
	 * @param fw
	 * @throws Exception
	 */
	private static void arrayWrite(ArrayList<String> tags,OutputStreamWriter fw )
			throws Exception{
		for (int i=0;i<tags.size();i++){
			String tag = tags.get(i);
			fw.write(tag,0,tag.length());
		}
	}

}