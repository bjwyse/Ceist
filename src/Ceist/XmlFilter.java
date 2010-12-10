/*
    Ceist Question Generation (QG) System
    Copyright (C) 2010  Brendan Wyse <bjwyse@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package Ceist;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * A FileFilter for XML files, filtering by the extension .xml
 */
public class XmlFilter extends FileFilter {
    public boolean accept (File f )
    {
        if (f.isDirectory())
            return true;
        
        String extension = getExtension(f);
        
        if (extension != null)
        {
            if (extension.equals("xml"))
                return true;
            else
                return false;
        }
        
        return false;
    }
    
    /**
     * Gets the desc
     * @return
     */
    public String getDescription()
    {
        return "XML Files";
    }

     /*
     * Get the extension of a file.
     */  
    public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 &&  i < s.length() - 1) {
            ext = s.substring(i+1).toLowerCase();
        }
        return ext;
    }
}
