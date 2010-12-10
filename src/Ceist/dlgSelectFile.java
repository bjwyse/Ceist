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

import java.io.File;

public class dlgSelectFile extends javax.swing.JDialog {

    private CeistApp mainApp;
    
    /** Creates new form dlgSelectFile */
    public dlgSelectFile(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        
        mainApp = CeistApp.getApplication();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileChooser = new javax.swing.JFileChooser();

        setModal(true);
        setName("Form"); // NOI18N
        setResizable(false);
        addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            public void windowGainedFocus(java.awt.event.WindowEvent evt) {
                formWindowGainedFocus(evt);
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt) {
            }
        });

        fileChooser.setName("fileChooser"); // NOI18N
        fileChooser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileChooserActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(fileChooser, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 446, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(fileChooser, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 268, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowGainedFocus(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowGainedFocus

        String rulesFile = mainApp.prefs.get ("RULES_FILE", "");
       
        File file = new File (rulesFile);
        fileChooser.setCurrentDirectory(file);
    }//GEN-LAST:event_formWindowGainedFocus

    private void fileChooserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileChooserActionPerformed

        String command = evt.getActionCommand();
        
        if (command.equals("ApproveSelection"))
        {
            File rulesFile = fileChooser.getSelectedFile();
            mainApp.prefs.put("RULES_FILE", rulesFile.getAbsolutePath());
        }
        setVisible(false);
        this.setDefaultCloseOperation(HIDE_ON_CLOSE);
    }//GEN-LAST:event_fileChooserActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                dlgSelectFile dialog = new dlgSelectFile(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JFileChooser fileChooser;
    // End of variables declaration//GEN-END:variables

}
