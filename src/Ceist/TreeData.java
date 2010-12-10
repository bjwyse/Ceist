/*
    Ceist Question Generation (QG) System
    Copyright (C) 2010  Brendan Wyse <bjwyse@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/
package Ceist;

import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.TreeReaderFactory;

import edu.stanford.nlp.trees.tregex.TreeMatcher;

public class TreeData {
    private Treebank treebank;
    private int      treeLimit;
    private boolean  loaded;

    public TreeData (){
        TreeReaderFactory trf;
        trf = new TreeMatcher.TRegexTreeReaderFactory();
        treebank = new MemoryTreebank(trf, "UTF-8");

        treeLimit = 30000;
        loaded = false;
    }

    public int Count(){
        return treebank.size();
    }

    public void loadFromFiles (String folder, String files) {

        if (folder.equals(""))
            return;

        clear();
        
        for (String file : files.split(","))
        {
            try {
                treebank.loadPath(folder + "\\" + file.trim() + ".stp");
            }
            catch (Exception e) {
                System.out.println (e.toString());
            }

            if (treebank.size() > treeLimit )
            {
                System.out.println ("Over " + treeLimit + " trees!");
                break;
            }
        }

        loaded = true;

    }

    public void loadFromURL (String url){

    }

    public void clear (){
         treebank.clear();
         loaded = false;
    }

    public boolean isLoaded (){
        return loaded;
    }

    public Treebank getTreebank(){
        return treebank;
    }
}
