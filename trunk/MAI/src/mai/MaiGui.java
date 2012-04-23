
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

/*
compiling:
javac -Xlint:unchecked -cp sqlitejdbc-v056.jar *.java
java -cp .:sqlitejdbc-v056.jar MaiGui

java 1.5: javac -target 1.5 -Xlint:unchecked -cp sqlitejdbc-v056.jar *.java

making the .jar file (after compiling):
jar cvfm Mai.jar manifest.txt *.class
java -jar Mai.jar

 */

package mai;

import java.awt.*;
import java.awt.event.*;
import java.lang.Exception;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import javax.swing.table.TableColumn;
import javax.swing.table.DefaultTableModel;

import java.io.*;
import java.util.*;




/** 
 * MaiGui is the main class for MAI; it manages all the GUI attributes 
 * and manages how the annotation/adjudication information is loaded, interacted with,
 * and displayed.
 * <p>
 * All files loaded into MAI must be UTF-8 encoded, otherwise the character offsets
 * cannot be gauranteed to work.
 * 
 * 
 * @author Amber Stubbs 
 * @version 0.7 April 16, 2012
 */

public class MaiGui extends JPanel{

	/**
	 * 
	 */
	private static final long serialVersionUID = -6122390155866896831L;
	private Hashtable<String, Color> colorTable;
	private Color[] colors = {Color.magenta, new Color(153,102,0),
			new Color(255,204,51), new Color(0,172,188),new Color (234,160,0), 
			new Color(102,75,153),Color.lightGray};

	//private Hashtable<String,Object> highlighterTable;

	//keeps track of what parts of the text
	//have been visited by the user when
	//looking at link tags.
	private HashCollection<String,Integer> visitedLocs;

	private boolean textSelected;
	private boolean ctrlPressed;
	private int loc1;
	private boolean hasFile;

	private String linkFrom;
	private String linkName;
	private String linkTo;

	private int start;
	private int end;

	private int tableRow;
	private int tableCol;

	private JFileChooser fcDtd;
	private JFileChooser fcFile;
	private JFileChooser fcSave;

	private static JFrame frame;
	private JPanel annotatePane;
	private JTextPane displayAnnotation;
	private JScrollPane scrollPane;
	private JLabel mouseLabel;
	private JFrame linkFrame;

	private JPanel tagPanel;
	private ButtonGroup tagButtons;

	private JPanel infoPanel;

	private TableListener tablelistener;

	private AnnJTable tagTable;

	private JMenuBar mb;
	private JMenu fileMenu;
	private JMenu display;
	//private JMenu nc_tags;
	private JMenu helpMenu;
	private JPopupMenu popup1;
	private JPopupMenu popup2;

	private static AdjudicationTask adjudicationTask;
	private ArrayList<String> filenames;

	public MaiGui(){
		super(new BorderLayout());

		visitedLocs = new HashCollection<String,Integer>();

		/*global variable assignments*/
		hasFile = false;

		start=-1;
		end=-1;
		loc1 = -1;

		linkFrom="";
		linkName="";
		linkTo="";

		textSelected=false;
		ctrlPressed=false;

		tableRow = -1;
		tableCol = -1;

		filenames = new ArrayList<String>();
		filenames.add("goldStandard.xml");
		colorTable = new Hashtable<String,Color>();

		linkFrame = new JFrame();

		/*GUI parts*/
		fcDtd = new JFileChooser(".");
		fcDtd.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		fcFile = new JFileChooser(".");
		fcFile.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		fcSave = new JFileChooser(".");
		fcSave.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		popup1 = new JPopupMenu();
		popup2 = new JPopupMenu();

		tagTable = new AnnJTable();
		tablelistener = new TableListener();


		adjudicationTask = new AdjudicationTask();

		mouseLabel = new JLabel("Highlighted text: (-1,-1)");
		annotatePane = new JPanel(new BorderLayout());
		displayAnnotation = new JTextPane(new DefaultStyledDocument());
		displayAnnotation.setEditable(false);
		displayAnnotation.setContentType("text/plain; charset=UTF-8");
		displayAnnotation.addKeyListener(new ModKeyListener());
		displayAnnotation.addCaretListener(new AnnCaretListener());
		displayAnnotation.addMouseListener(new PopupListener());
		scrollPane = new JScrollPane(displayAnnotation);
		annotatePane.add(scrollPane,BorderLayout.CENTER);
		annotatePane.add(mouseLabel,BorderLayout.SOUTH);

		tagPanel = new JPanel(new GridLayout(0,1));
		tagButtons = new ButtonGroup();
		JLabel lab = new JLabel("DTD tags");
		tagPanel.add(lab);

		infoPanel = new JPanel(new GridLayout(0,1));

		mb = new JMenuBar();
		fileMenu = createFileMenu();
		display = createDisplayMenu();
		helpMenu = createHelpMenu();
		mb.add(fileMenu);
		mb.add(display);
		mb.add(helpMenu);

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,annotatePane,infoPanel);

		this.addKeyListener(new ModKeyListener());


		add(mb,BorderLayout.NORTH);
		add(tagPanel,BorderLayout.WEST);
		add(splitPane,BorderLayout.CENTER);
		splitPane.setDividerLocation(400);

	}

	// *******************************************************
	// This section of MAI code contains the classes used in MAI
	
	/**
	 * Handles the actions in the File menu; loading DTDs, starting new
	 * adjudication tasks, adding files/gold standards to the adjudication task.
	 *
	 */
	private class getFile implements ActionListener{
		public void actionPerformed(ActionEvent e){
			if (e.getActionCommand().equals("Load DTD")){
				int returnVal = fcDtd.showOpenDialog(MaiGui.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fcDtd.getSelectedFile();
					try{
						displayAnnotation.setStyledDocument(new DefaultStyledDocument());
						DTDLoader dtdl = new DTDLoader(file);
						adjudicationTask.reset_db();
						adjudicationTask.setDTD(dtdl.getDTD());
						makeRadioTags();
						//reset visitedLocs
						visitedLocs = new HashCollection<String,Integer>();

						updateMenus();
						hasFile=false;
						adjudicationTask.addDTDtoDB();
						resetInfoPanel();
					}catch(Exception o){
						System.out.println("Error loading DTD");
						System.out.println(o.toString());
					}
				}
			}//end if "Load DTD"
			else if (e.getActionCommand().equals("start adjud")){
				int returnVal = fcFile.showOpenDialog(MaiGui.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fcFile.getSelectedFile();
					String fullName = file.getName();

					try{
						frame.setTitle(fullName);
						hasFile = true;
						updateMenus();

						visitedLocs = new HashCollection<String,Integer>();

						adjudicationTask.reset_db();
						adjudicationTask.addDTDtoDB();
						adjudicationTask.reset_IDTracker();

						colorTable.clear();
						colorTable.put("goldStandard.xml",Color.yellow);
						colorTable.put("allOtherFiles",Color.cyan);
						colorTable.put("someOtherFiles",Color.pink);

						Highlighter high = displayAnnotation.getHighlighter();
						high.removeAllHighlights();

						
						filenames = new ArrayList<String>();
						filenames.add(fullName);
						filenames.add("goldStandard.xml");

						frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

						displayAnnotation.setStyledDocument(new DefaultStyledDocument());
						displayAnnotation.setContentType("text/plain; charset=UTF-8");

						resetInfoPanel();
						if (FileOperations.areTags(file)){
							XMLFileLoader xfl = new XMLFileLoader(file);
							StyledDocument d = displayAnnotation.getStyledDocument();
							Style def = StyleContext.getDefaultStyleContext().getStyle( 
									StyleContext.DEFAULT_STYLE );
							Style regular = d.addStyle( "regular", def );
							d.insertString(0, xfl.getTextChars(), regular);

							HashCollection<String,Hashtable<String,String>> newTags = xfl.getTagHash();
							if (newTags.size()>0){
								adjudicationTask.addTagsFromHash(fullName, newTags);
							}
						}
					}catch(Exception ex){
						hasFile=false;
						System.out.println("Error loading file");
						System.out.println(ex.toString());
					}
					if (tagButtons.getSelection()!=null){
						String command = tagButtons.getSelection().getActionCommand();
						assignTextColors(command);
					}
				}
				frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				displayAnnotation.setCaretPosition(0);
			}//end start adjud
			
			else if (e.getActionCommand().equals("add file")){
				int returnVal = fcFile.showOpenDialog(MaiGui.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fcFile.getSelectedFile();

					try{
						String fullName = file.getName();
						frame.setTitle(frame.getTitle() + ", "+fullName);
						
						frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
						//check to make sure the text is the same as the first file
						int textLen = displayAnnotation.getStyledDocument().getLength();
						String text= displayAnnotation.getStyledDocument().getText(0,textLen);
						XMLFileLoader xfl = new XMLFileLoader(file);

						String text2 = xfl.getTextChars();
						int textLen2 = text2.length();

						if(textLen != textLen2){
							throw new Exception("File length mismatch!");
						}
						else{

							if(text.equals(text2)==false){
								throw new Exception("error matching text!");
							}
						}

						updateMenus();
						//check to make sure name isn't already there
						while(filenames.contains(fullName)){
							fullName = "x"+fullName;
						}
						filenames.add(0,fullName);
						assignColors(fullName);

						//add the new file to the DB
						if (FileOperations.areTags(file)){
							HashCollection<String,Hashtable<String,String>> newTags = xfl.getTagHash();
							if (newTags.size()>0){
								adjudicationTask.addTagsFromHash(fullName, newTags);
							}
						}

						//update the display
						resetInfoPanel();
						if (tagButtons.getSelection()!=null){
							String command = tagButtons.getSelection().getActionCommand();
							assignTextColors(command);
						}

					}catch(Exception ex){
						System.out.println("Error loading file");
						System.out.println(ex.toString());
					}
				}
				frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				displayAnnotation.setCaretPosition(0);
			}//end addfile
			
			else if (e.getActionCommand().equals("add GS")){
				int returnVal = fcFile.showOpenDialog(MaiGui.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fcFile.getSelectedFile();

					try{
						String fullName = file.getName();
						frame.setTitle(frame.getTitle() + ", "+fullName);
						
						frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
						//check to make sure the text is the same as the first file
						int textLen = displayAnnotation.getStyledDocument().getLength();
						String text= displayAnnotation.getStyledDocument().getText(0,textLen);
						XMLFileLoader xfl = new XMLFileLoader(file);

						String text2 = xfl.getTextChars();
						int textLen2 = text2.length();

						if(textLen != textLen2){
							throw new Exception("File length mismatch!");
						}
						else{

							if(text.equals(text2)==false){
								throw new Exception("error matching text!");
							}
						}

						String tempHack = "goldStandard.xml";
						updateMenus();

						if (FileOperations.areTags(file)){
							HashCollection<String,Hashtable<String,String>> newTags = xfl.getTagHash();
							if (newTags.size()>0){
								adjudicationTask.addTagsFromHash(tempHack, newTags);
							}
						}

						//update the display
						resetInfoPanel();
						adjudicationTask.findAllOverlaps();
						if (tagButtons.getSelection()!=null){
							String command = tagButtons.getSelection().getActionCommand();
							assignTextColors(command);
						}

					}catch(Exception ex){
						System.out.println("Error loading file");
						System.out.println(ex.toString());
					}
				}
				frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				displayAnnotation.setCaretPosition(0);
			}//end addGS


			else if(e.getActionCommand().equals("Save XML")){
				fcSave.setSelectedFile(new File("goldStandard.xml"));
				int returnVal = fcSave.showSaveDialog(MaiGui.this);
				if(returnVal == JFileChooser.APPROVE_OPTION){
					File file = fcSave.getSelectedFile();
					String fullName = file.getName();
					try{
						FileOperations.saveAdjudXML(file,displayAnnotation,
								adjudicationTask);
						frame.setTitle(fullName);
					}catch(Exception e2){
						System.out.println(e2.toString());
					}
				}
			} 
		}//end actionPerformed
	}//end class getFile
	
	/**
	 * Listens for the command to increase/decrease the size of the font
	 */
	private class DisplayListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			String action = e.getActionCommand();
			if (action.equals("Font++")){
				Font font = displayAnnotation.getFont();
				Font font2 = new Font(font.getName(),font.getStyle(),font.getSize()+1);
				displayAnnotation.setFont(font2);
			}
			if (action.equals("Font--")){
				Font font = displayAnnotation.getFont();
				Font font2 = new Font(font.getName(),font.getStyle(),font.getSize()-1);
				displayAnnotation.setFont(font2);
			}
		}
	}
	
	/**
	 * Creates a highlighter object that can be added to the text display
	 *
	 */
	private class TextHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
		private TextHighlightPainter(Color color) {
			super(color);
		}
	}
	
	/**
	 * Listens for the request from the Help Menu
	 */
	private class AboutListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			showAboutDialog();
		}
	}
	/**
	 * Listens to the table to determine if a button has been clicked
	 *
	 */
	private class TableListener implements ListSelectionListener {
		public void valueChanged(ListSelectionEvent event) {
			if (event.getValueIsAdjusting()) {
				return;
			}
			//first, determine if we actually want to trigger an event
			if (!(tableRow == tagTable.getSelectedRow() && tableCol == tagTable.getSelectedColumn())){
				//if we do, figure out what event we're triggering
				if (tagTable.getSelectedColumn()==tagTable.getColumnCount()-1){
					ButtonEditor b = (ButtonEditor)tagTable.getCellEditor(tagTable.getSelectedRow(),tagTable.getSelectedColumn());
					if (b.getLabel().startsWith("add")){
						checkForAddition(tagTable.getSelectedColumn(),tagTable.getSelectedRow());
					}
					else if (b.getLabel().startsWith("copy")){
						//get data for new row and add directly to the GS
						String[]newdata = makeRow(tagTable.getSelectedColumn(),tagTable.getSelectedRow());
						DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
						int i = tableModel.getRowCount();
						tableModel.addRow(newdata);
						checkForAddition(tagTable.getSelectedColumn(),i);
					}
				}
			}
			tableRow = tagTable.getSelectedRow();
			tableCol = tagTable.getSelectedColumn();

		}
	}
	
	/**
	 * This is the class that's called whenever a  
     * radio button is pressed.
	 */
	private class RadioButtonListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			resetInfoPanel();
			assignTextColors(e.getActionCommand());
			Highlighter high = displayAnnotation.getHighlighter();
			high.removeAllHighlights();
			
		}
	}

	/**
	 * Listens to the table to determine when a tag is double-clicked, and 
	 * calls the function to highlight the related extents.  Also determines
	 * if the user right-clicked and should be given the option to remove
	 * a tag from the database.
	 *
	 */
	private class JTableListener extends MouseAdapter {
		public void mousePressed(MouseEvent e){
			maybeShowRemovePopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowRemovePopup(e);
		}

		public void mouseClicked(MouseEvent e) {
			if(e.getClickCount()==2){
				Highlighter high = displayAnnotation.getHighlighter();
				try{
					high.removeAllHighlights();
				}catch(Exception b){  
				}

				String title = tagButtons.getSelection().getActionCommand();
				Elem el = adjudicationTask.getElem(title);

				if(el instanceof ElemExtent){
					try{
						high.addHighlight(start,end,new TextHighlightPainter(Color.orange));
					}catch (Exception b) {
					}
				}//end if ElemExtent

				if(el instanceof ElemLink){
					//if a link is selected, the locations of the from and to anchors
					//need to be found and highlighted
					int selectedRow = tagTable.getSelectedRow();
					String fromSelect = (String)tagTable.getValueAt(selectedRow,2);
					String toSelect = (String)tagTable.getValueAt(selectedRow,4);

					String fromLoc = adjudicationTask.getLocByFileAndID("goldStandard.xml",fromSelect);
					String toLoc = adjudicationTask.getLocByFileAndID("goldStandard.xml",toSelect);
					if (fromLoc != null){

						String [] locs = fromLoc.split(",");
						int startSelect = Integer.parseInt(locs[0]);
						int endSelect = Integer.parseInt(locs[1]);
						try{
							high.addHighlight(startSelect,endSelect+1,new TextHighlightPainter(Color.orange));
						}catch(Exception ex){
							System.out.println(ex);
						}
					}
					if (toLoc != null){
						String [] locs = toLoc.split(",");
						int startSelect = Integer.parseInt(locs[0]);
						int endSelect = Integer.parseInt(locs[1]);
						try{
							high.addHighlight(startSelect,endSelect+1,new TextHighlightPainter(Color.orange));
						}catch(Exception ex){
							System.out.println(ex);
						}
					}
				}//end if ElemLink
			}
		}
		
		//if the user right-clicks on a link
		private void maybeShowRemovePopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				popup2 = removePopup();
				popup2.show(e.getComponent(),
						e.getX(), e.getY());
			}
		}
	}

	/**
	 * Listens for mouse events in the text area; if the 
	 * mouse situation meets popup requirements, 
	 * give the option to create a new tag.
	 *
	 */
	private class PopupListener extends MouseAdapter {
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) {
			if (e.isPopupTrigger() && textSelected){
				popup1 = populatePopup();
				popup1.show(e.getComponent(),
						e.getX(), e.getY());
			}
		}
	}

	/**
	 * If the user decides to remove the highlighted table rows, 
	 * this method is called.  It removes the tags from the table 
	 * as well as the database, and for extent tags it also 
	 * removes any link tags the extent is participating in.
	 *
	 */
	private class removeSelectedTableRows implements ActionListener{
		public void actionPerformed(ActionEvent actionEvent) {

			boolean check = showDeleteWarning();
			if (check){
				String action = tagButtons.getSelection().getActionCommand();
				Elem elem = adjudicationTask.getElem(action);
				int[] selectedRows = tagTable.getSelectedRows();

				DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
				//find the id column
				int cols = tableModel.getColumnCount();
				int idCol = -1;
				int sourceCol = -1;
				for(int i=0;i<cols;i++){
					String colname = tableModel.getColumnName(i);
					if(colname.equalsIgnoreCase("id")){
						idCol = i;
					}
					if(colname.equalsIgnoreCase("source")){
						sourceCol = i;
					}
				}
				//get the id for each selected row and remove id
				String id = "";
				String source = "";
				for (int i=selectedRows.length-1;i>=0;i--){
					int row = selectedRows[i];
					id = (String)tableModel.getValueAt(row,idCol);
					source = (String)tableModel.getValueAt(row,sourceCol);
					//don't want to delete tags that come from the files
					//being adjudicated
					if (source.equalsIgnoreCase("goldStandard.xml")){
						if(elem instanceof ElemExtent){
							adjudicationTask.removeExtentByFileAndID(source,action,id);
							int start = Integer.parseInt(((String)tableModel.getValueAt(row,2)));
							int end = Integer.parseInt(((String)tableModel.getValueAt(row,3)));
							assignTextColor(action,start,end);
							HashCollection<String,String> links = 
									adjudicationTask.getLinksByFileAndExtentID(source,action,id);
							//remove links that use the tag being removed
							ArrayList<String> keys = links.getKeyList();
							for (int k=0;k<keys.size();k++){
								String tag = keys.get(k);
								ArrayList<String> ids = links.get(tag);
								for(int j=0;j<ids.size();j++){
									String idl = ids.get(j);
									adjudicationTask.removeLinkByFileAndID(source,tag,idl);
								}
								//also, remove those locations from the visitedLocs 
								//HashCollection
								ArrayList<Integer> vlocs = visitedLocs.get(tag);
								for(int j=start;j<=end;j++){
									vlocs.remove(new Integer(j));
								}
							}


						}
						else{
							adjudicationTask.removeLinkByFileAndID(source,action,id);

						}
						tableModel.removeRow(selectedRows[i]);
					}
				}
				if(elem instanceof ElemExtent){
					assignTextColors(action);
				}
			}
		}
	}

	/**
	 * Keeps track of whether the CTRL key (or the equivalent Mac key)
	 * is being pressed in order to determine if the link creation window 
	 * should be displayed.
	 */
	private class ModKeyListener implements KeyListener{
		public void keyPressed(KeyEvent e) {
			int keyCode = e.getKeyCode();

			String p = System.getProperty("os.name");
			if(p.toLowerCase().contains("mac")){
				if (keyCode == 18 || keyCode == 157){
					ctrlPressed = true;
				}
			}
			else{
				if ( keyCode == 17){
					ctrlPressed = true;
				}
			}
		}

		public void keyReleased(KeyEvent e){
			String p = System.getProperty("os.name");
			int keyCode = e.getKeyCode();
			if(p.toLowerCase().contains("mac")){
				if (keyCode == 18 || keyCode == 157){
					ctrlPressed = false;
				}
			}
			else{
				if ( keyCode == 17){
					ctrlPressed = false;
				}
			}
		}

		public void keyTyped(KeyEvent e){
			//do nothing
		}
	}


	/**
	 * Keeps track of what extents in the text are highlighted by the user 
	 * in order to refresh the tag tables, and assigns highlights based 
	 * on the tag and extent selected.
	 *
	 */
	private class AnnCaretListener implements CaretListener{
		public void caretUpdate(CaretEvent e) {
			Highlighter high = displayAnnotation.getHighlighter();
			high.removeAllHighlights();

			tagTable.getColumnModel().getSelectionModel().removeListSelectionListener(tablelistener);
			tagTable.getSelectionModel().removeListSelectionListener(tablelistener);
			tableRow = -1;
			tableCol = -1;
			resetInfoPanel();

			int dot = e.getDot();
			int mark = e.getMark();

			if((ctrlPressed==true) && (loc1 == -1)){
				loc1 = dot;
			}
			else if(ctrlPressed==true && loc1 != -1){
				showLinkWindow(loc1,dot);
				ctrlPressed = false;
				loc1=-1;
			}

			if (dot!=mark){
				textSelected=true;
				if(dot<mark){
					start=dot;
					end=mark;
				}
				else{
					start=mark;
					end=dot;
				}
				mouseLabel.setText("Highlighted text: "+ Integer.toString(start) +","+Integer.toString(end)+")");

				findRelatedTags();

				try{
					high.addHighlight(start, end, DefaultHighlighter.DefaultPainter);
				}catch(BadLocationException b){
				}
			}
			else{
				textSelected=false;
				start=-1;
				end=-1;
				mouseLabel.setText("Highlighted text: (-1,-1)");
			}
		}
	}//end AnnCaretListener
	
	/**
	 * A quick and dirty way to change global variables based 
	 * on what's going on in the link creation window so that 
	 * a new link can be created more easily.
	 *
	 */
	private class jboxListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			JComboBox box = (JComboBox)e.getSource();
			String select = (String)box.getSelectedItem();
			if (e.getActionCommand() == "fromID"){
				linkFrom = select;
			}
			else if (e.getActionCommand() == "link"){
				linkName = select;
			}
			else if (e.getActionCommand() == "toID"){
				linkTo = select;
			}
		}
	}
	
	/**
	 * This is the class that's associated with the make link button
	 * in the popup window created in showLinkWindow()
	 *
	 */
	private class linkListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			clearTableSelections();
			//check to make sure that linkFrom, linkName, and linkTo
			//are all valid ids/link names
			linkFrom = linkFrom.split(" \\(")[0];
			String from_id = linkFrom.split(" - ")[1];
			String from_type = linkFrom.split(" - ")[0];
			
			linkTo = linkTo.split(" \\(")[0];
			String to_id = linkTo.split(" - ")[1];
			String to_type = linkTo.split(" - ")[0];
			
			String from_text = adjudicationTask.getTextByFileElemAndID("goldStandard.xml",from_type,from_id);
			String to_text = adjudicationTask.getTextByFileElemAndID("goldStandard.xml",to_type,to_id);

			//add link to appropriate table
			DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();

			String[] newdata = new String[tableModel.getColumnCount()];
			for(int i=0;i<tableModel.getColumnCount();i++){
				newdata[i]="";
			}
			//get the Elem that the table was based on, and go through
			//the attributes.  Put in the start and end bits
			Hashtable<String,Elem> elements = adjudicationTask.getElemHash();
			Elem elem = elements.get(linkName);

			//get ID number for link
			String newID = "";

			for(int k=0;k<tableModel.getColumnCount();k++){
				String colName = tableModel.getColumnName(k);
				if(colName.equals("id")){
					newID=adjudicationTask.getNextID(elem.getName(),"goldStandard.xml");
					newdata[k]=newID;
				}
				else if(colName.equals("fromID")){
					newdata[k]=from_id;
				}
				else if(colName.equals("toID")){
					newdata[k]=to_id;
				}
				else if(colName.equals("fromText")){
					newdata[k]=from_text;
				}
				else if(colName.equals("toText")){
					newdata[k]=to_text;
				}
				else if (colName.equals("source")){
					newdata[k] = "goldStandard.xml";
				}
			}
			newdata[tableModel.getColumnCount()-1] = "add/moddify";
			tableModel.addRow(newdata);
			tagTable.clearSelection();
			tagTable.setRowSelectionInterval(tableModel.getRowCount()-1,tableModel.getRowCount()-1);
			Rectangle rect =  tagTable.getCellRect(tableModel.getRowCount()-1, 0, true);
			tagTable.scrollRectToVisible(rect);
			
			//add the new tag to the database
			addRowToGoldStandard(0,tableModel.getRowCount()-1,elem);

			//reset variables
			linkFrom="";
			linkName="";
			linkTo="";
			linkFrame.setVisible(false);  
		}

	}


	
	//end of classes section
	//********************************************************

	/**
	 * Assigns colors to each file being adjudicated.
	 * 
	 * @param filename
	 */
	private void assignColors(String filename){
		//assigns 
		int col = colorTable.size();
		int cols = colors.length;
		if (col>=cols){
			col = col%cols;
		}
		colorTable.put(filename,colors[col]);
	}

	/**
	 * This is the class that's called when an extent tag is
     * selected from the popup menu
	 * 
	 */
	private class MakeTagListener implements ActionListener{
		public void actionPerformed(ActionEvent actionEvent) {
			clearTableSelections();
			String action = actionEvent.getActionCommand();
			//if the tag being added is non-consuming, make sure 
			//start and end are set to -1
			if(action.contains("NC-")){
				start=-1;
				end=-1;
				action = action.split("-")[1];
			}
			DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
			//clear out the rest of the table
			tableModel.getDataVector().removeAllElements();
			//create array for data for row*/
			String[] newdata = new String[tableModel.getColumnCount()];
			for(int i=0;i<tableModel.getColumnCount();i++){
				newdata[i]="";
			}
			//get the Elem that the table was based on, and go through
            //     the attributes.  Put in the start and end bits
			Hashtable<String,Elem> elements = adjudicationTask.getElemHash();
			Elem elem = elements.get(action);
			//get ID number. This also isn't as hard-coded as it looks: 
            //     the columns for the table are created from the Attributes array list
			String newID = "";
			ArrayList<Attrib> attributes = elem.getAttributes();
			for(int i=0;i<attributes.size();i++){
				if(attributes.get(i) instanceof AttID){
					newID=adjudicationTask.getNextID(elem.getName(),"goldStandard.xml");
					newdata[i+1]=newID;
				}
				if(attributes.get(i).hasDefaultValue()){
					newdata[i+1]=attributes.get(i).getDefaultValue();
				}
			}

			//put in start and end values
			if (elem instanceof ElemExtent){
				attributes = elem.getAttributes();
				for(int k=0;k<tableModel.getColumnCount();k++){
					String colName = tableModel.getColumnName(k);
					//this isn't as hard-coded as it looks, because
					//all extent elements have these attributes
					if (colName.equals("start")){
						newdata[k]=Integer.toString(start);
					}
					else if (colName.equals("end")){
						newdata[k]=Integer.toString(end);
					}
					else if (colName.equals("text") && start != -1){
						newdata[k] = getText(start,end);
					}
					else if (colName.equals("source")){
						newdata[k] = "goldStandard.xml";
					}
				}
				newdata[tableModel.getColumnCount()-1] = "add/moddify";

				tableModel.addRow(newdata);
				tagTable.clearSelection();
				tagTable.setRowSelectionInterval(tableModel.getRowCount()-1,tableModel.getRowCount()-1);
				Rectangle rect =  tagTable.getCellRect(tableModel.getRowCount()-1, 0, true);
				tagTable.scrollRectToVisible(rect);
				addRowToGoldStandard(0,tableModel.getRowCount()-1,elem);
				if(start!=-1){
					assignTextColor(action,start,end);
				}
			}
		}
	}

	/**
	 * fetches the string from the text panel based on character offsets
	 * 
	 * @param start the start of the span
	 * @param end the end of the span
	 * @return the text at the specificed location
	 */
	private String getText(int start, int end){
		DefaultStyledDocument styleDoc = (DefaultStyledDocument)displayAnnotation.getStyledDocument();
		String text = "";
		try{
			text = styleDoc.getText(start,end-start);
		}catch(Exception e){
			System.out.println(e.toString());
		}
		return text;
	}



	/**
     * Updates the colors in the text area when a new tag is chosen from the radio buttons
     * 
     * When a tag is selected, the colors are based on which/how many 
     * files include it in the annotation (if it's in the Gold Standard, the 
     * text is green, if it's in all the files but the Gold Standard the text 
     * is blue, and if it's some but not all the files the text is red).    
     * 
     * @param tagname The name of the selected tag
	 */
	private void assignTextColors(String tagname){
		//reset all text to black, then set new colors
		setColorAtLocation(Color.black,0,displayAnnotation.getStyledDocument().getLength(),false);

		//check to see if the tagname is a non-consuming tag
		if(tagname.startsWith("NC-")){
			//no colors will be set if an non-consuming tag is chosen; instead 
			//skip straight to filling in the table
			String command = tagname.substring(3);
			HashCollection<String,String> idHash = adjudicationTask.getTagsSpanByType(-1,
					-1,command);
			fillInTable(idHash,command);
		}
		else{
			Elem e = adjudicationTask.getElem(tagname);
			if (e instanceof ElemExtent){
				HashCollection<String,String>elems = adjudicationTask.getExtentAllLocs(tagname);
				ArrayList<String> locations = elems.getKeyList();
				for (int i=0;i<locations.size();i++) {
					String location = locations.get(i);
					ArrayList<String> files = elems.getList(location);
					if (files.contains("goldStandard.xml")){
						setColorAtLocation(Color.green,Integer.parseInt(location),1,false);
					}
					else if (files.size()>0){
						boolean allfiles = true;
						for(int f=0;f<filenames.size();f++){
							if(files.contains(filenames.get(f))==false
									&& filenames.get(f).equals("goldStandard.xml")==false){
								allfiles = false;
							}
						}
						if (allfiles==true){
							setColorAtLocation(Color.blue,Integer.parseInt(location),1,false);
						}
						else{
							setColorAtLocation(Color.red,Integer.parseInt(location),1,false);
						}
					}
				}
			}

			else{//if selected tag is a link
				//first, get all the places where there are extent tags in the 
				//gold standard
				Hashtable<String,String> allLocs = adjudicationTask.getAllExtentsByFile("goldStandard.xml");
				for (Enumeration<String> locs = allLocs.keys(); locs.hasMoreElements();){
					int loc = Integer.parseInt(locs.nextElement());
					setColorAtLocation(Color.lightGray,loc,1,false);
				}
				//then, figure out what extents are already in links and
				//highlight them appropriately
				HashCollection<String,String>elems = 
						adjudicationTask.findGoldStandardLinksByType(tagname);

				ArrayList<String> locations = elems.getKeyList();
				for (int i=0;i<locations.size();i++) {
					String location = locations.get(i);
					ArrayList<String> files = elems.getList(location);
					if (files.contains("goldStandard.xml")){
						setColorAtLocation(Color.green,Integer.parseInt(location),1,false);
					}
					else if (files.size()>0){
						boolean allfiles = true;
						for(int f=0;f<filenames.size();f++){
							if(files.contains(filenames.get(f))==false
									&& filenames.get(f).equals("goldStandard.xml")==false){
								allfiles = false;
							}
						}
						if (allfiles==true){
							setColorAtLocation(Color.blue,Integer.parseInt(location),1,false);
						}
						else{
							setColorAtLocation(Color.red,Integer.parseInt(location),1,false);
						}
					}
				}
				//finally, go over everything that's already been looked at
				colorVisitedLocs(tagname);

			}
		}
	}

	/**
	 * Goes over the text when a link is selected and colors the locations
	 * that the adjudicator has already examined.
	 * 
	 * @param tagname that tag that's been selected
	 */
	private void colorVisitedLocs(String tagname){
		ArrayList<Integer> visitlocs = visitedLocs.get(tagname);
		if(visitlocs !=null){
			for(int i=0;i<visitlocs.size();i++){
				setColorAtLocation(Color.magenta,visitlocs.get(i).intValue(),1,false);
			}
		}

	}


	/**
	 *  This method is for coloring/underlining text
     *  in the text window.  It detects overlaps, and
     *  should be called every time a tag is added
     *  or removed.
	 * 
	 * @param tagname the selected tagname
	 * @param beginColor the beginning of the text span being assigned a color
	 * @param endColor the end of the text span being assigned a color
	 */
	private void assignTextColor(String tagname, int beginColor, int endColor){
		// go through each part of the word being changed and 
        // find what tags are there, and what color it should be.
		Elem e = adjudicationTask.getElem(tagname);
		if (e instanceof ElemExtent){
			for(int i=0;i<endColor-beginColor;i++){
				ArrayList<String>files = 
						adjudicationTask.getFilesAtLocbyElement(tagname,beginColor+i);
				//if there is a gold standard tag in that location, the text will be gree
				if (files.contains("goldStandard.xml")){
					setColorAtLocation(Color.green,beginColor+i,1,false);
				}
				else if (files.size()>0){
					boolean allfiles = true;
					// check to see if there's a tag at that location from each
					// of the files being adjudicated
					for(int f=0;f<filenames.size();f++){
						if(files.contains(filenames.get(f))==false
								&& filenames.get(f).equals("goldStandard.xml")==false){
							allfiles = false;
						}
					}
					//if a tag exists in all files, the text is blue
					if (allfiles==true){
						setColorAtLocation(Color.blue,beginColor+i,1,false);
					}
					//otherwise it's red
					else{
						setColorAtLocation(Color.red,beginColor+i,1,false);
					}
				}
				else{
					setColorAtLocation(Color.black,beginColor+i,1,false);
				}
			}
		}
	}

	/**
	 * Used to set the text in a span to a determined color
	 * 
	 * @param color the color being assigned
	 * @param s the start of the span
	 * @param e the end of the span
	 * @param b whether or not the text will be underlined
	 */
	private void setColorAtLocation(Color color, int s, int e, boolean b){
		DefaultStyledDocument styleDoc = (DefaultStyledDocument)displayAnnotation.getStyledDocument();
		SimpleAttributeSet aset = new SimpleAttributeSet();
		StyleConstants.setForeground(aset, color);
		StyleConstants.setUnderline(aset, b);
		styleDoc.setCharacterAttributes(s,e,aset,false);
	}



	/**
	 * Creates the popup menu that allows users to create new 
	 * tags from the text window
	 * @return popup menu
	 */
	private JPopupMenu populatePopup(){
		JPopupMenu jp = new JPopupMenu();
		//create a menuitem for the selected RadioButton
		String name = tagButtons.getSelection().getActionCommand();
		JMenuItem menuItem = new JMenuItem(name);
		menuItem.addActionListener(new MakeTagListener());
		jp.add(menuItem);
		return jp;
	}

	/**
	 * Shows the warning when deleting a tag.  This will be 
	 * displayed even if the extent does not participate in any links.
	 * @return true or false
	 */
	private boolean showDeleteWarning(){
		String text = ("Deleting extent tag(s) will also delete \n" +
				"any links that use these extents.  Would you like to continue?");

		int message = JOptionPane.showConfirmDialog(frame, 
				text, "Warning!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (message==0){
			return true;
		}
		return false;
	}

	/**
	 * Fills in the table based on the extent selected in the text window
	 */
	private void findRelatedTags(){
		//first, get files and ids of elements in selected extents by type\
		if (tagButtons.getSelection()!=null){
			String command = tagButtons.getSelection().getActionCommand();
			Elem e = adjudicationTask.getElem(command);
			if (e instanceof ElemExtent){
				HashCollection<String,String> idHash = adjudicationTask.getTagsSpanByType(start,
						end,command);
				fillInTable(idHash,command);
			}
			else if(e instanceof ElemLink){
				//for location between start and end, if there is an extent tag 
				//there in the gold standard, change color to magenta and add 
				//to visitedLocs
				for(int i = start;i<=end;i++){
					if(adjudicationTask.tagExistsInFileAtLoc("goldStandard.xml",i)){
						setColorAtLocation(Color.magenta,i,1,false);
						visitedLocs.putEnt(command,new Integer(i));
					}
				}

				HashCollection<String,Hashtable<String,String>> idHash = 
						adjudicationTask.getLinkTagsSpanByType(start,end,command);
				fillInLinkTable(idHash,command);
			}
			else{
				//do nothing, it's a non-consuming tag
			}
		}
	}


	/**
	 * Removes the bottom table and creates a new one based on the 
	 * selected tag.
	 */
	private void resetInfoPanel(){
		infoPanel.removeAll();
		JComponent tableComp = makeTable();
		infoPanel.add(tableComp);
		infoPanel.updateUI();
	}

	/**
	 * Builds and displays the link creation window by getting the tags 
	 * at the two locations the user clicked while holding the CTRL key
	 * 
	 * @param loc the first location clicked
	 * @param loc2 the second location clicked
	 */
	private void showLinkWindow(int loc, int loc2){
		JPanel linkPane = new JPanel(new BorderLayout());
		JPanel boxPane = new JPanel(new GridLayout(3,2));
		linkFrame = new JFrame();

		//first, get all the tags in the gold standard at the first location
		//non-consuming tags are included, because those can also be linked to/from
		JComboBox fromList = new JComboBox();
		fromList.addActionListener(new jboxListener());
		fromList.setActionCommand("fromID"); 

		HashCollection<String,String> idHash =  
				adjudicationTask.getFileTagsSpanAndNC("goldStandard.xml",loc,loc+1);
		ArrayList<String> elements = idHash.getKeyList();
		if (elements.size()>0){
			if (elements.size()>1){
				fromList.addItem("");
			}
			for(int i=0; i<elements.size();i++){
				ArrayList<String> tags = idHash.get(elements.get(i));
				for(int j=0;j<tags.size();j++){
					//create the string for the table list
					String puttag = (elements.get(i) + 
							" - " + tags.get(j));
					//get the text for the words by id and element
					String text = adjudicationTask.getTextByFileElemAndID("goldStandard.xml",elements.get(i),tags.get(j));
					puttag = puttag + " ("+text+")";
					//add string to JComboBox
					fromList.addItem(puttag);
				}
			}
		}

		//the, create the combobox that contains the link types 
		//(in MAI, this is the tag that's been selected, not all the link tags)
		JComboBox linkList = new JComboBox();
		linkList.setActionCommand("link"); 
		linkList.addActionListener(new jboxListener());
		linkList.addItem(tagButtons.getSelection().getActionCommand());

		//then, fill in the tag and NC information for the second location
		JComboBox toList = new JComboBox();
		toList.setActionCommand("toID"); 
		toList.addActionListener(new jboxListener());

		idHash =  adjudicationTask.getFileTagsSpanAndNC("goldStandard.xml",loc2,loc2+1);
		elements = idHash.getKeyList();
		if (elements.size()>0){
			if (elements.size()>1){
				toList.addItem("");
			}
			for(int i=0; i<elements.size();i++){
				ArrayList<String> tags = idHash.get(elements.get(i));
				for(int j=0;j<tags.size();j++){
					String puttag = (elements.get(i) + 
							" - " + tags.get(j));
					//get the text for the words by id and element
					String text = adjudicationTask.getTextByFileElemAndID("goldStandard.xml",elements.get(i),tags.get(j));
					puttag = puttag + " ("+text+")";
					toList.addItem(puttag);
				}
			}
		}
		//pack everything into the window and make it visible
		JButton makeLink = new JButton("Create Link");
		makeLink.addActionListener(new linkListener());
		boxPane.add(new JLabel("Link from:"));
		boxPane.add(fromList);
		boxPane.add(new JLabel("Link type:"));
		boxPane.add(linkList);
		boxPane.add(new JLabel("Link to:"));
		boxPane.add(toList);
		linkPane.add(boxPane,BorderLayout.CENTER);
		linkPane.add(makeLink,BorderLayout.SOUTH);
		linkFrame.setBounds(90,70,400,300);
		linkFrame.add(linkPane);
		linkFrame.setVisible(true);

	}



	/**
	 * Creates the table and table model for the data that will be displayed 
	 * based on the selected RadioButton.
	 */
	private JComponent makeTable(){
		AnnTableModel model = new AnnTableModel();
		model.setGoldStandardName("goldStandard.xml");
		tagTable = new AnnJTable(model);
		JScrollPane scroll = new JScrollPane(tagTable);
		tagTable.addMouseListener(new JTableListener());
		//check to make sure that a tag has been selected
		if (tagButtons.getSelection()!=null){
			String tagCommand = tagButtons.getSelection().getActionCommand();
			if(tagCommand.startsWith("NC-")){
				tagCommand = tagCommand.substring(3);
			}
			Elem e = adjudicationTask.getElem(tagCommand);
			ArrayList<Attrib> attributes = e.getAttributes();
			//for some reason, it's necessary to add the columns first,
			//then go back and add the cell renderers.
			model.addColumn("source");
			for (int i=0;i<attributes.size();i++){
				model.addColumn(attributes.get(i).getName());
			}
			model.addColumn("action");
			for (int i=0;i<attributes.size();i++){
				Attrib a = attributes.get(i);
				TableColumn c = tagTable.getColumnModel().getColumn(i+1);
				if (a instanceof AttList){
					AttList att = (AttList)a;
					JComboBox options = makeComboBox(att);
					c.setCellEditor(new DefaultCellEditor(options));
				}
			}

			//add buttons to end of rows
			TableColumn c= tagTable.getColumnModel().getColumn(tagTable.getColumnCount()-1);
			c.setCellRenderer(new ButtonRenderer());
			c.setCellEditor(new ButtonEditor(new JCheckBox()));
		}
		//need to add the same listener to both, 
		//otherwise the table events won't trigger correctly
		tagTable.getSelectionModel().addListSelectionListener(tablelistener);
		tagTable.getColumnModel().getSelectionModel().addListSelectionListener(tablelistener);

		return(scroll);
	}

	/**
	 * Link tables are more complicated to fill in because they require that the 
	 * links from the files being adjudicated contain the IDs and text of the overlapping 
	 * tags from the goldStandard
	 * 
	 * @param idHash the hashtable containing IDs
	 * @param tagname the name of the tag whose information is being filled in
	 */
	private void fillInLinkTable(HashCollection<String,Hashtable<String,String>> idHash,
			String tagname){
		//first, clear out existing table and listener, otherwise the changes to the table
		//trigger conflicting events
		tagTable.getColumnModel().getSelectionModel().removeListSelectionListener(tablelistener);
		tagTable.getSelectionModel().removeListSelectionListener(tablelistener);
		tableRow = -1;
		tableCol = -1;
		DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
		tableModel.getDataVector().removeAllElements();

		//idHash is a HashCollection containing the filenames as keys 
		//and HaahTables with attribute info as data
		String[] newdata = new String[tableModel.getColumnCount()];
		ArrayList<String> keys = idHash.getKeyList();
		for(int i=0;i<keys.size();i++){
			String source = keys.get(i);
			ArrayList<Hashtable<String,String>>links = idHash.getList(source);
			if(links!=null){
				for(int j=0;j<links.size();j++){
					Hashtable<String,String> attributes = links.get(j);
					newdata[0] = source;
					for(int k=1;k<tableModel.getColumnCount();k++){
						String colName = tableModel.getColumnName(k);
						String value = attributes.get(colName);
						if(value!=null){
							newdata[k]=value;
						}
						else{
							newdata[k]="";
						}
					}
					//create the appropriate buttons
					if (source.equals("goldStandard.xml")){
						newdata[tableModel.getColumnCount()-1]="add/modify";
					}
					else{
						newdata[tableModel.getColumnCount()-1]="copy to GS";
					}
					tableModel.addRow(newdata);
				}
			}

		}
		//also, highlight the appropriate related extents
		HashCollection<String,String> currentHighlights = adjudicationTask.getCurrentHighlights();

		//keep the gold standard highlights separate for dealing with afterwards
		ArrayList<String>gsLocs = currentHighlights.get("goldStandard.xml");
		currentHighlights.remove("goldStandard.xml");
		Highlighter high = displayAnnotation.getHighlighter();
		high.removeAllHighlights();

		if(gsLocs!=null){
			for(int i=0;i<gsLocs.size();i++){
				String loc = gsLocs.get(i);
				//split the string into start and end ints
				int start = Integer.parseInt(loc.split("@#@")[0]);
				int end = Integer.parseInt(loc.split("@#@")[1]);

				//now, add those to the highlighter
				try{
					high.addHighlight(start,end,new TextHighlightPainter(colorTable.get("goldStandard.xml")));
				}catch(BadLocationException b){
					System.out.println(b);
				}

			}
		}

		ArrayList<String> files = currentHighlights.getKeyList();
		for (int i=0;i<files.size();i++){
			String file = files.get(i);
			ArrayList<String> highLocs = currentHighlights.get(file);
			for(int j=0;j<highLocs.size();j++){
				String loc = highLocs.get(j);
				//split the string into start and end ints
				int start = Integer.parseInt(loc.split("@#@")[0]);
				int end = Integer.parseInt(loc.split("@#@")[1]);

				//now, add those to the highlighter
				try{
					high.addHighlight(start,end,new TextHighlightPainter(colorTable.get(file)));
				}catch(BadLocationException b){
					System.out.println(b);
				}

			}
		}
		//add the listeners back to the table
		tagTable.getSelectionModel().addListSelectionListener(tablelistener);
		tagTable.getColumnModel().getSelectionModel().addListSelectionListener(tablelistener);
	}

	/**
	 * Fills in the table when an extent tag is selected from the 
	 * RadioButtons and a new span is highlighted in the text area.
	 * 
	 * @param idHash a HashCollection containing relevent IDs
	 * @param tagname the type of the tag information being filled in 
	 */
	private void fillInTable(HashCollection<String,String> idHash, String tagname){
		//first, clear out existing table and listener, otherwise the changes to the table
		//trigger conflicting events
		tagTable.getColumnModel().getSelectionModel().removeListSelectionListener(tablelistener);
		tagTable.getSelectionModel().removeListSelectionListener(tablelistener);
		tableRow = -1;
		tableCol = -1;
		DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
		tableModel.getDataVector().removeAllElements();

		//idHash is a HashCollection containing the filenames as keys and tag ids
		//as data
		String[] newdata = new String[tableModel.getColumnCount()];
		ArrayList<String> keys = idHash.getKeyList();
		//for each file source, add all tags in the idHash
		for(int i=0;i<keys.size();i++){
			String source = keys.get(i);
			ArrayList<String>ids=idHash.getList(source);
			if(ids!=null){
				for(int j=0;j<ids.size();j++){
					Hashtable<String,String> ht = 
							adjudicationTask.getTagsByFileAndID(tagname,ids.get(j),source);
					newdata[0]=source;

					for(int k=1;k<tableModel.getColumnCount();k++){
						String colName = tableModel.getColumnName(k);
						String value = ht.get(colName);
						if(value!=null){
							newdata[k]=value;
						}
						else{
							newdata[k]="";
						}
					}
					if (source.equals("goldStandard.xml")){
						newdata[tableModel.getColumnCount()-1]="add/modify";
					}
					else{
						newdata[tableModel.getColumnCount()-1]="copy to GS";
					}
					tableModel.addRow(newdata);
				}
			}

		}
		tagTable.getSelectionModel().addListSelectionListener(tablelistener);
		tagTable.getColumnModel().getSelectionModel().addListSelectionListener(tablelistener);
	}


	/**
	 * If a new file is being added to the gold standard, this method
	 * checks to make sure all the necessary information is there.
	 * 
	 * @param col the column where the "add" button was pressed (will be 0 
	 * if this is being called from somewhere outside the tag display table)
	 * @param buttonRow the row being checked for inclusion to the gold standard
	 */
	private void checkForAddition(int col, int buttonRow){
		//get array of tag attributes
		String tagCommand = tagButtons.getSelection().getActionCommand();
		//if we're dealing with an NC tag, the checks are the same as a regular tag
		//but the triggering command is different and needs to be fixed.
		if(tagCommand.startsWith("NC-")){
			tagCommand = tagCommand.substring(3);
		}
		Elem e = adjudicationTask.getElem(tagCommand);
		if(e instanceof ElemExtent){
			//check for start
			boolean hasStart = false;
			boolean hasEnd = false;

			//TO DO: make this checking more robust: check that 
			//start and end are integers, that start comes before
			//end and that both are within the bounds of the text
			for (int i=0;i<tagTable.getColumnCount();i++){
				String header = tagTable.getColumnName(i);
				if (header.equals("start")){
					String val = (String)tagTable.getValueAt(buttonRow,i);
					if(val!=""){
						hasStart=true;
					}
				}
				else if (header.equals("end")){
					String val = (String)tagTable.getValueAt(buttonRow,i);
					if(val!=""){
						hasEnd=true;
					}
				}
			}

			if (hasStart && hasEnd){
				addRowToGoldStandard(col,buttonRow,e);
			}
			else{
				JOptionPane.showMessageDialog(tagTable,"parameters missing");
			}
		}
		else{//if it's a link
			boolean hasFromID = false;
			boolean hasToID = false;

			for (int i=0;i<tagTable.getColumnCount();i++){
				String header = tagTable.getColumnName(i);
				if (header.equals("fromID")){
					String val = (String)tagTable.getValueAt(buttonRow,i);
					if(val!=""){
						hasFromID=true;
					}
				}
				else if (header.equals("toID")){
					String val = (String)tagTable.getValueAt(buttonRow,i);
					if(val!=""){
						hasToID=true;
					}
				}
			}
			if(hasFromID && hasToID){
				addRowToGoldStandard(col,buttonRow,e);
			}else{
				JOptionPane.showMessageDialog(tagTable,"parameters missing");
			}
		}
	}
	
	/**
	 * Adds a new row to the gold standard, or resubmits an existing row 
	 * if attributes have been changed.
	 * 
	 * @param col the column of the button that triggered this method being called
	 * (0 if the table was not used)
	 * @param buttonRow the row being added/modified
	 * @param e the type of tag being added
	 */
	private void addRowToGoldStandard(int col, int buttonRow, Elem e){
		boolean hasID = false;
		String id = "";
		int idLoc = -1;

		for (int i=0;i<tagTable.getColumnCount();i++){
			String header = tagTable.getColumnName(i);
			if (header.equals("id")){
				idLoc = i;
				id = (String)tagTable.getValueAt(buttonRow,i);
				if (id!=""){ 
					hasID = true;
				}
			}
		}
		if (hasID){
			//remove previous tag from DB
			//this might not be necessary if the start and end aren't being
			//changed
			if (e instanceof ElemExtent){
				adjudicationTask.removeExtentByFileAndID("goldStandard.xml",
						e.getName(),id);
			}
			else{
				adjudicationTask.removeLinkByFileAndID("goldStandard.xml",
						e.getName(),id);
			}
		}
		else{
			//if no id exists in the row, get the next one
			id = adjudicationTask.getNextID(e.getName(),"goldStandard.xml");
			tagTable.setValueAt(id,buttonRow,idLoc);
		}
		//create hashtable of element attributes
		Hashtable<String,String> tag = new Hashtable<String,String>();

		if (e instanceof ElemExtent){
			int startT = -1;
			int endT = -1;

			for (int i=0;i<tagTable.getColumnCount();i++){
				String header = tagTable.getColumnName(i);
				tag.put(header,(String)tagTable.getValueAt(buttonRow,i));
				if (header.equals("start")){
					startT = Integer.parseInt((String)tagTable.getValueAt(buttonRow,i));
				}
				if (header.equals("end")){
					endT = Integer.parseInt((String)tagTable.getValueAt(buttonRow,i));
				}
			}
			//add the column to the DB
			adjudicationTask.addTagFromHash("goldStandard.xml",e,tag);
			//color the new location appropriately
			assignTextColor(e.getName(),startT,endT);
		}
		else{//if it's a link
			for (int i=0;i<tagTable.getColumnCount();i++){
				String header = tagTable.getColumnName(i);
				tag.put(header,(String)tagTable.getValueAt(buttonRow,i));
			}
			//add the column to the DB
			adjudicationTask.addTagFromHash("goldStandard.xml",e,tag);
			String command = tagButtons.getSelection().getActionCommand();
			assignTextColors(command);
		}
	}


	/**
	 * remove all highlights from rows
	 */
	private void clearTableSelections(){
		DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
		int rows = tableModel.getRowCount();
		if(rows>0)
			tagTable.removeRowSelectionInterval(0,rows-1);
	}


	/**
	 * Creates a new row for the Gold Standard, usually from a button click from the table.
	 * 
	 * @param col the column where the clicked button exists
	 * @param row the row being copied to the Gold Standard
	 * @return an array of the correct size with the some of the information filled in
	 */
	private String[] makeRow(int col, int row){
		DefaultTableModel tableModel = (DefaultTableModel)tagTable.getModel();
		String[] newdata = new String[tableModel.getColumnCount()];
		for (int i=0;i<tableModel.getColumnCount()-1;i++){
			if(i==0){
				newdata[i]="goldStandard.xml";
			}
			else if (tagTable.getColumnName(i).equals("id")){
				newdata[i]="";
			}
			else{
				newdata[i] = (String)tagTable.getValueAt(row,i);
			}
		}
		newdata[tableModel.getColumnCount()-1]="add/modify";
		return newdata;
	}
	
	/**
	 * The pop-up that appears when a tag table is right-clicked to give the 
	 * user the option to delete tags from the gold standard
	 * 
	 * @return popup menu
	 */
	private JPopupMenu removePopup(){
		JPopupMenu jp = new JPopupMenu();
		String title = tagButtons.getSelection().getActionCommand();
		if(title.startsWith("NC-")){
			title = title.substring(3);
		}
		String action = "Remove selected " + title + " rows";
		JMenuItem menuItem = new JMenuItem(action);
		menuItem.setActionCommand(title);
		menuItem.addActionListener(new removeSelectedTableRows());
		jp.add(menuItem);
		return jp;
	}

	/**
	 * Provides information about MAI
	 */
	private void showAboutDialog(){
		JOptionPane about = new JOptionPane();
		about.setLocation(100,100);
		about.setAlignmentX(Component.CENTER_ALIGNMENT);
		about.setAlignmentY(Component.CENTER_ALIGNMENT);
		String text = ("MAI \nMulti-document Adjudication Interface \nVersion 0.6 \n\n" +
				"Copyright Amber Stubbs\nastubbs@cs.brandeis.edu \n Lab for " +
				"Linguistics and Computation, Brandeis University 2010-2012." + 
				"\n\nThis distribution of MAI (the software and the source code) \n" +
				" is covered under the GNU General Public License version 3.\n" +
				"http://www.gnu.org/licenses/");
		JOptionPane.showMessageDialog(frame, text);
	}


	/**
	 * Makes a comboBox from List-type attribute
	 * @param att The Attlist being turned into a combobox
	 * @return jcombobox
	 */
	private JComboBox makeComboBox(AttList att){
		JComboBox options = new JComboBox();
		options.addItem("");
		for(int j=0;j<att.getList().size();j++){
			options.addItem(att.getList().get(j));
		}
		return options;
	}

	/**
	 * Creates the radiobutton options on the left side of the display.
	 */
	private void makeRadioTags(){
		tagPanel.removeAll();
		ArrayList<Elem> elements = adjudicationTask.getElements();
		tagButtons = new ButtonGroup();
		//first, add the regular tags
		for (int i = 0;i<elements.size();i++){
			Elem e = elements.get(i);
			JRadioButton button = new JRadioButton(e.getName());
			button.setActionCommand(e.getName());
			button.addActionListener(new RadioButtonListener());
			tagButtons.add(button);
			tagPanel.add(button);
		}
		//then, add the NC elements
		ArrayList<Elem> ncElements = adjudicationTask.getNCElements();
		for (int i = 0;i<ncElements.size();i++){
			Elem e = ncElements.get(i);
			JRadioButton button = new JRadioButton("NC-"+e.getName());
			button.setActionCommand("NC-"+e.getName());
			button.addActionListener(new RadioButtonListener());
			tagButtons.add(button);
			tagPanel.add(button);
		}
	}


	/**
	 * Create the file menu with associated listeners and 
	 * commands.
	 */
	private JMenu createFileMenu() {
		JMenu menu = new JMenu("File");
		JMenuItem loadDTD = new JMenuItem("Load DTD");
		loadDTD.setActionCommand("Load DTD");
		loadDTD.addActionListener(new getFile());
		menu.add(loadDTD);

		JMenuItem startAdjud = new JMenuItem("Start new adjudication");
		startAdjud.setActionCommand("start adjud");
		startAdjud.addActionListener(new getFile());
		if(adjudicationTask.hasDTD()==false){
			startAdjud.setEnabled(false);
		}
		else{
			startAdjud.setEnabled(true);
		}
		menu.add(startAdjud);

		JMenuItem addFile = new JMenuItem("Add file to adjudication");
		addFile.setActionCommand("add file");
		addFile.addActionListener(new getFile());
		if(hasFile==false){
			addFile.setEnabled(false);
		}
		else{
			addFile.setEnabled(true);
		}

		menu.add(addFile);

		JMenuItem addGS = new JMenuItem("Add gold standard file");
		addGS.setActionCommand("add GS");
		addGS.addActionListener(new getFile());
		if(hasFile==false){
			addGS.setEnabled(false);
		}
		else{
			addGS.setEnabled(true);
		}
		menu.add(addGS);

		menu.addSeparator();
		JMenuItem saveFileXML = new JMenuItem("Save Gold Standard As XML");
		saveFileXML.setActionCommand("Save XML");
		saveFileXML.addActionListener(new getFile());
		if(hasFile==false){
			saveFileXML.setEnabled(false);
		}
		else{
			saveFileXML.setEnabled(true);
		}

		menu.add(saveFileXML);
		return menu;
	}

	/**
	 * Creates the menu for changing the font size
	 */
	private JMenu createDisplayMenu(){
		JMenu menu = new JMenu("Display");

		JMenuItem increaseFont = new JMenuItem("Font Size ++");
		increaseFont.setActionCommand("Font++");
		increaseFont.addActionListener(new DisplayListener());
		menu.add(increaseFont);

		JMenuItem decreaseFont = new JMenuItem("Font Size --");
		decreaseFont.setActionCommand("Font--");
		decreaseFont.addActionListener(new DisplayListener());
		menu.add(decreaseFont);

		return menu;

	}

	/**
	 * Creates the menu describing MAI
	 */
	private JMenu createHelpMenu(){
		JMenu menu = new JMenu("Help");
		JMenuItem about = new JMenuItem("About MAI");
		about.addActionListener(new AboutListener());
		menu.add(about);
		return menu;
	}

	/**
	 * Updates the menus when new files are added
	 */
	private void updateMenus(){
		mb.remove(display);
		mb.remove(fileMenu);
		mb.remove(helpMenu);
		fileMenu = createFileMenu();
		mb.add(fileMenu);
		mb.add(display);
		mb.add(helpMenu);
		mb.updateUI();

	}

	/**
	 * Create the GUI
	 */
	private static void createAndShowGUI() {
		JFrame.setDefaultLookAndFeelDecorated(true);

		//Create and set up the window.
		frame = new JFrame("MAI");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		//Create and set up the content pane.
		JComponent newContentPane = new MaiGui();
		newContentPane.setOpaque(true); //content panes must be opaque
		frame.setContentPane(newContentPane);

		//Display the window.
		frame.pack();
		frame.setSize(900,700);
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(
				new Runnable() {
					public void run() {
						createAndShowGUI();
					}
				});
	}

}