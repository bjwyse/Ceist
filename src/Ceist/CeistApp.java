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

import java.util.prefs.Preferences;
import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;

public class CeistApp extends SingleFrameApplication {

    public Preferences prefs;
    
    /**
     * At startup create and show the main frame of the application.
     */
    @Override protected void startup() {
        prefs = Preferences.userNodeForPackage(this.getClass());
        show(new CeistView(this));
    }

    /**
     * This method is to initialize the specified window by injecting resources.
     * Windows shown in our application come fully initialized from the GUI
     * builder, so this additional configuration is not needed.
     */
    @Override protected void configureWindow(java.awt.Window root) {
    }

    /**
     * A convenient static getter for the application instance.
     * @return the instance of RulePadApp
     */
    public static CeistApp getApplication() {
        return Application.getInstance(CeistApp.class);
    }

    /**
     * Main method launching the application.
     */
    public static void main(String[] args) {
        launch(CeistApp.class, args);
    }
}
