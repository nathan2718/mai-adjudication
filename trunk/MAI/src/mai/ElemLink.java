
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
 * Extends Elem; used for describing link tags
 * 
 * @author Amber Stubbs
 *
 */

class ElemLink extends Elem{

ElemLink(){
}

ElemLink(String name, String pre){
    setName(name);
    AttID id = new AttID("id", pre, true);
    AttData from = new AttData("fromID", true);
    AttData fromText = new AttData("fromText",true);
    AttData to = new AttData("toID", true);
    AttData toText = new AttData("toText",true);
    addAttribute(id);
    addAttribute(from);
    addAttribute(fromText);
    addAttribute(to);
    addAttribute(toText);
}

public void setFrom(String f){
    fromID=f;
}

public String getFrom(){
    return fromID;
}

public void setFromText(String f){
    fromText=f;
}

public String getFromText(){
    return fromText;
}

public void setTo(String t){
    toID=t;
}

public String getTo(){
    return toID;
}

public void setToText(String t){
    toText=t;
}

public String getToText(){
    return toText;
}



public void printInfo(){
    System.out.println("\tname = " + getName());
    System.out.println("\tFrom = " + getFrom());
    System.out.println("\tTo = " + getTo());
    
}

private String fromID;
private String fromText;
private String toID;
private String toText;

}
