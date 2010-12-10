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

import QG.AnswerTemplate;
import QG.MatchPattern;
import QG.QGRule;
import QG.QuestionTemplate;

import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;
import javax.swing.JFrame;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import edu.stanford.nlp.trees.Tree;

import java.io.File;
import java.io.StringReader;

public class CeistView extends FrameView {

    private CeistApp mainApp;
        
    List<Tree> matchedTrees = new ArrayList<Tree>();
    List<Tree> diffTrees = new ArrayList<Tree>();

    private DataSetManager dataSet;
    
    private String treeFolder;
    private String testFiles;
    private String devFiles;

    public CeistView(SingleFrameApplication app) {
        super(app);

        dataSet = new DataSetManager();
        mainApp = CeistApp.getApplication();

        initComponents();
    
        getRootPane().setDefaultButton(this.btnFindMatches);
        
        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        
        busyIconTimer = new Timer
                (busyAnimationRate, new ActionListener() 
            {
                public void actionPerformed(ActionEvent e) 
                {
                    busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                    statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
                }
            });
        
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {

            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String) (evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer) (evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });

        loadData();
   }

    @Action
    public void loadData (){
        treeFolder   = mainApp.prefs.get ("TREE_FOLDER", "");
        testFiles    = mainApp.prefs.get ("TEST_SET", "");
        devFiles     = mainApp.prefs.get ("DEVELOPMENT_SET", "");

        loadDevData();
        loadTestData();
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            aboutBox = new CeistAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        CeistApp.getApplication().show(aboutBox);
    }

    public void showProgressBox(int max) {
        if (progressBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            progressBox = new dlgProgress(mainFrame, false);
            progressBox.setLocationRelativeTo(mainFrame);
            progressBox.mainBar.setMaximum(max);
        }
        CeistApp.getApplication().show(progressBox);
    }
    
    @Action
    public void setTreeFolder() {
        if (setTreeFolderBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            setTreeFolderBox = new dlgSetTreeFolder(mainFrame);
            setTreeFolderBox.setLocationRelativeTo(mainFrame);
        }
        CeistApp.getApplication().show(setTreeFolderBox);
    }

    @Action
    public void setRulesFile() {
        if (selectFileBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            selectFileBox = new dlgSelectFile(mainFrame, true);
            selectFileBox.setLocationRelativeTo(mainFrame);
            selectFileBox.fileChooser.addChoosableFileFilter(new XmlFilter());
            selectFileBox.setTitle("Select Rules file");
        }
        CeistApp.getApplication().show(selectFileBox);
    }
    
    public boolean yesNoBox (String message)
    {
        if (yesNoBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            yesNoBox = new dlgYesNo(mainFrame);
            yesNoBox.setLocationRelativeTo(mainFrame);
        }
        CeistApp.getApplication().show(yesNoBox);
        
        return true;
    }

    @Action
    public void specifyData() {
        if (specifyDataBox == null) {
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            specifyDataBox = new dlgSpecifyData(mainFrame);
            specifyDataBox.setLocationRelativeTo(mainFrame);
        }
        CeistApp.getApplication().show(specifyDataBox);
    }
    
    @Action
    public void showTreeView(){
        if (treeViewBox == null){
            JFrame mainFrame = CeistApp.getApplication().getMainFrame();
            treeViewBox = new dlgTreeView(mainFrame, false);
            treeViewBox.setLocationRelativeTo (mainFrame);
    
            String ptbTreeString = "(ROOT (S (NP (DT This)) (VP (VBZ is) (NP (DT a) (NN test))) (. .)))";
            try{
                Tree tree = (new PennTreeReader(new StringReader(ptbTreeString), new LabeledScoredTreeFactory(new StringLabelFactory()))).readTree();
                treeViewBox.mainPanel.setTree(tree);
            }
            catch (Exception e)
            {
                System.out.println (e.toString());
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        panelMainTabbed = new javax.swing.JTabbedPane();
        panelSearch = new javax.swing.JPanel();
        btnFindMatches = new javax.swing.JButton();
        lblSearchStatus = new javax.swing.JLabel();
        btnUseTestData = new javax.swing.JToggleButton();
        lblTestStatus = new javax.swing.JLabel();
        btnUseDevelopmentData = new javax.swing.JToggleButton();
        lblDevelopmentStatus = new javax.swing.JLabel();
        chkShowTagged = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        tableMatches = tableMatches = new javax.swing.JTable()
        {
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };
        ;
        chkShowPreview = new javax.swing.JCheckBox();
        panelRules = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        lstRules = new javax.swing.JList();
        tabTemplates = new javax.swing.JTabbedPane();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        tableMatchPatterns = new javax.swing.JTable();
        jPanel8 = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        tableQuestionTemplates = new javax.swing.JTable();
        jPanel9 = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        tableAnswerTemplates = new javax.swing.JTable();
        txtRuleName = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        btnSaveRule = new javax.swing.JButton();
        patternsToolbar = new javax.swing.JToolBar();
        btnAdd = new javax.swing.JButton();
        chkRuleLocked = new javax.swing.JCheckBox();
        btnScratchpadToRule = new javax.swing.JButton();
        btnRuleToScratchpad = new javax.swing.JButton();
        panelGroups = new javax.swing.JPanel();
        jButton2 = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        txtQuestionTemplate = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        txtAnswerTemplate = new javax.swing.JTextField();
        txtCurrentPattern = new javax.swing.JTextField();
        txtCurrentRuleName = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        textInput = new javax.swing.JTextArea();
        jLabel2 = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        setRulesFile = new javax.swing.JMenuItem();
        reloadRulesFile = new javax.swing.JMenuItem();
        setTreeFolderMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        dataMenu = new javax.swing.JMenu();
        specifyDataMenuItem = new javax.swing.JMenuItem();
        reloadData = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        popupRulesList = new javax.swing.JPopupMenu();
        mnuDeleteRule = new javax.swing.JMenuItem();
        mnuApplyPrimary = new javax.swing.JMenuItem();
        mnuCloneRule = new javax.swing.JMenuItem();
        mnuNewRule = new javax.swing.JMenuItem();
        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        popupMatchPatterns = new javax.swing.JPopupMenu();
        mnuDeleteRule1 = new javax.swing.JMenuItem();
        mnuApplyPrimary1 = new javax.swing.JMenuItem();
        mnuCloneRule1 = new javax.swing.JMenuItem();
        mnuNewMatchPattern = new javax.swing.JMenuItem();

        mainPanel.setName("mainPanel"); // NOI18N

        panelMainTabbed.setName("panelMainTabbed"); // NOI18N

        panelSearch.setName("panelSearch"); // NOI18N
        panelSearch.setOpaque(false);

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(Ceist.CeistApp.class).getContext().getResourceMap(CeistView.class);
        btnFindMatches.setText(resourceMap.getString("btnFindMatches.text")); // NOI18N
        btnFindMatches.setName("btnFindMatches"); // NOI18N
        btnFindMatches.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFindMatchesActionPerformed(evt);
            }
        });

        lblSearchStatus.setText(resourceMap.getString("lblSearchStatus.text")); // NOI18N
        lblSearchStatus.setName("lblSearchStatus"); // NOI18N

        btnUseTestData.setText(resourceMap.getString("btnUseTestData.text")); // NOI18N
        btnUseTestData.setName("btnUseTestData"); // NOI18N

        lblTestStatus.setText(resourceMap.getString("lblTestStatus.text")); // NOI18N
        lblTestStatus.setName("lblTestStatus"); // NOI18N

        btnUseDevelopmentData.setSelected(true);
        btnUseDevelopmentData.setText(resourceMap.getString("btnUseDevelopmentData.text")); // NOI18N
        btnUseDevelopmentData.setName("btnUseDevelopmentData"); // NOI18N

        lblDevelopmentStatus.setText(resourceMap.getString("lblDevelopmentStatus.text")); // NOI18N
        lblDevelopmentStatus.setName("lblDevelopmentStatus"); // NOI18N

        chkShowTagged.setText(resourceMap.getString("chkShowTagged.text")); // NOI18N
        chkShowTagged.setName("chkShowTagged"); // NOI18N
        chkShowTagged.setOpaque(false);

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        tableMatches.setModel(new DefaultTableModel());
        tableMatches.setBackground(resourceMap.getColor("tableMatches.background")); // NOI18N
        tableMatches.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Full Sentence", "Question", "Answer"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        tableMatches.setGridColor(resourceMap.getColor("tableMatches.gridColor")); // NOI18N
        tableMatches.setName("tableMatches"); // NOI18N
        tableMatches.setSelectionBackground(resourceMap.getColor("tableMatches.selectionBackground")); // NOI18N
        tableMatches.setSelectionForeground(resourceMap.getColor("tableMatches.selectionForeground")); // NOI18N
        tableMatches.setVerifyInputWhenFocusTarget(false);
        tableMatches.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tableMatchesMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(tableMatches);

        chkShowPreview.setText(resourceMap.getString("chkShowPreview.text")); // NOI18N
        chkShowPreview.setName("chkShowPreview"); // NOI18N
        chkShowPreview.setOpaque(false);

        org.jdesktop.layout.GroupLayout panelSearchLayout = new org.jdesktop.layout.GroupLayout(panelSearch);
        panelSearch.setLayout(panelSearchLayout);
        panelSearchLayout.setHorizontalGroup(
            panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(panelSearchLayout.createSequentialGroup()
                .addContainerGap()
                .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblSearchStatus)
                    .add(btnFindMatches))
                .add(46, 46, 46)
                .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(btnUseTestData)
                    .add(lblTestStatus))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(btnUseDevelopmentData)
                    .add(panelSearchLayout.createSequentialGroup()
                        .add(lblDevelopmentStatus)
                        .add(68, 68, 68)
                        .add(chkShowTagged)
                        .add(10, 10, 10)
                        .add(chkShowPreview)))
                .add(354, 354, 354))
            .add(panelSearchLayout.createSequentialGroup()
                .add(16, 16, 16)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 965, Short.MAX_VALUE)
                .addContainerGap())
        );

        panelSearchLayout.linkSize(new java.awt.Component[] {btnUseDevelopmentData, btnUseTestData}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        panelSearchLayout.setVerticalGroup(
            panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(panelSearchLayout.createSequentialGroup()
                .addContainerGap()
                .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnFindMatches)
                    .add(btnUseDevelopmentData)
                    .add(btnUseTestData))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(lblSearchStatus, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 14, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(lblTestStatus)
                        .add(lblDevelopmentStatus))
                    .add(panelSearchLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(chkShowTagged)
                        .add(chkShowPreview)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 302, Short.MAX_VALUE)
                .addContainerGap())
        );

        panelMainTabbed.addTab(resourceMap.getString("panelSearch.TabConstraints.tabTitle"), panelSearch); // NOI18N

        panelRules.setName("panelRules"); // NOI18N
        panelRules.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                panelRulesComponentShown(evt);
            }
        });

        jScrollPane3.setName("jScrollPane3"); // NOI18N

        lstRules.setModel(new DefaultListModel());
        lstRules.setComponentPopupMenu(popupRulesList);
        lstRules.setName("lstRules"); // NOI18N
        lstRules.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                lstRulesValueChanged(evt);
            }
        });
        lstRules.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lstRulesMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(lstRules);

        tabTemplates.setComponentPopupMenu(popupMatchPatterns);
        tabTemplates.setName("tabTemplates"); // NOI18N

        jPanel7.setName("jPanel7"); // NOI18N

        jScrollPane4.setName("jScrollPane4"); // NOI18N

        tableMatchPatterns.setModel(new DefaultTableModel());
        tableMatchPatterns.setBackground(resourceMap.getColor("tableMatchPatterns.background")); // NOI18N
        tableMatchPatterns.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Lvl", "ID", "Type", "Pattern"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        tableMatchPatterns.setName("tableMatchPatterns"); // NOI18N
        tableMatchPatterns.getTableHeader().setReorderingAllowed(false);
        jScrollPane4.setViewportView(tableMatchPatterns);
        tableMatchPatterns.getColumnModel().getColumn(0).setResizable(false);
        tableMatchPatterns.getColumnModel().getColumn(0).setPreferredWidth(30);
        tableMatchPatterns.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("tableMatchPatterns.columnModel.title0")); // NOI18N
        tableMatchPatterns.getColumnModel().getColumn(1).setResizable(false);
        tableMatchPatterns.getColumnModel().getColumn(1).setPreferredWidth(30);
        tableMatchPatterns.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("tableMatchPatterns.columnModel.title1")); // NOI18N
        tableMatchPatterns.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("tableMatchPatterns.columnModel.title2")); // NOI18N
        tableMatchPatterns.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("tableMatchPatterns.columnModel.title3")); // NOI18N

        org.jdesktop.layout.GroupLayout jPanel7Layout = new org.jdesktop.layout.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 664, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addContainerGap())
        );

        tabTemplates.addTab(resourceMap.getString("jPanel7.TabConstraints.tabTitle"), jPanel7); // NOI18N

        jPanel8.setName("jPanel8"); // NOI18N

        jScrollPane5.setName("jScrollPane5"); // NOI18N

        tableQuestionTemplates.setModel(new DefaultTableModel());
        tableQuestionTemplates.setBackground(resourceMap.getColor("tableQuestionTemplates.background")); // NOI18N
        tableQuestionTemplates.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tableQuestionTemplates.setName("tableQuestionTemplates"); // NOI18N
        jScrollPane5.setViewportView(tableQuestionTemplates);

        org.jdesktop.layout.GroupLayout jPanel8Layout = new org.jdesktop.layout.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 664, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addContainerGap())
        );

        tabTemplates.addTab(resourceMap.getString("jPanel8.TabConstraints.tabTitle"), jPanel8); // NOI18N

        jPanel9.setName("jPanel9"); // NOI18N

        jScrollPane6.setName("jScrollPane6"); // NOI18N

        tableAnswerTemplates.setModel(new DefaultTableModel());
        tableAnswerTemplates.setBackground(resourceMap.getColor("tableAnswerTemplates.background")); // NOI18N
        tableAnswerTemplates.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tableAnswerTemplates.setName("tableAnswerTemplates"); // NOI18N
        jScrollPane6.setViewportView(tableAnswerTemplates);

        org.jdesktop.layout.GroupLayout jPanel9Layout = new org.jdesktop.layout.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 664, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addContainerGap())
        );

        tabTemplates.addTab(resourceMap.getString("jPanel9.TabConstraints.tabTitle"), jPanel9); // NOI18N

        txtRuleName.setText(resourceMap.getString("txtRuleName.text")); // NOI18N
        txtRuleName.setName("txtRuleName"); // NOI18N

        jLabel9.setText(resourceMap.getString("jLabel9.text")); // NOI18N
        jLabel9.setName("jLabel9"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(Ceist.CeistApp.class).getContext().getActionMap(CeistView.class, this);
        btnSaveRule.setAction(actionMap.get("updateSelectedRule")); // NOI18N
        btnSaveRule.setText(resourceMap.getString("btnSaveRule.text")); // NOI18N
        btnSaveRule.setName("btnSaveRule"); // NOI18N

        patternsToolbar.setFloatable(false);
        patternsToolbar.setOrientation(1);
        patternsToolbar.setRollover(true);
        patternsToolbar.setName("patternsToolbar"); // NOI18N

        btnAdd.setAction(actionMap.get("addTemplateRow")); // NOI18N
        btnAdd.setIcon(resourceMap.getIcon("btnAdd.icon")); // NOI18N
        btnAdd.setText(resourceMap.getString("btnAdd.text")); // NOI18N
        btnAdd.setToolTipText(resourceMap.getString("btnAdd.toolTipText")); // NOI18N
        btnAdd.setFocusable(false);
        btnAdd.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAdd.setName("btnAdd"); // NOI18N
        btnAdd.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        patternsToolbar.add(btnAdd);

        chkRuleLocked.setText(resourceMap.getString("chkRuleLocked.text")); // NOI18N
        chkRuleLocked.setName("chkRuleLocked"); // NOI18N
        chkRuleLocked.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkRuleLockedActionPerformed(evt);
            }
        });

        btnScratchpadToRule.setAction(actionMap.get("ScratchpadToRule")); // NOI18N
        btnScratchpadToRule.setIcon(resourceMap.getIcon("btnScratchpadToRule.icon")); // NOI18N
        btnScratchpadToRule.setText(resourceMap.getString("btnScratchpadToRule.text")); // NOI18N
        btnScratchpadToRule.setName("btnScratchpadToRule"); // NOI18N

        btnRuleToScratchpad.setAction(actionMap.get("applySelectedRuleAsPrimary")); // NOI18N
        btnRuleToScratchpad.setIcon(resourceMap.getIcon("btnRuleToScratchpad.icon")); // NOI18N
        btnRuleToScratchpad.setText(resourceMap.getString("btnRuleToScratchpad.text")); // NOI18N
        btnRuleToScratchpad.setToolTipText(resourceMap.getString("btnRuleToScratchpad.toolTipText")); // NOI18N
        btnRuleToScratchpad.setName("btnRuleToScratchpad"); // NOI18N

        org.jdesktop.layout.GroupLayout panelRulesLayout = new org.jdesktop.layout.GroupLayout(panelRules);
        panelRules.setLayout(panelRulesLayout);
        panelRulesLayout.setHorizontalGroup(
            panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(panelRulesLayout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 251, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(4, 4, 4)
                .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel9)
                    .add(patternsToolbar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(panelRulesLayout.createSequentialGroup()
                        .add(btnRuleToScratchpad)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(btnScratchpadToRule)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(txtRuleName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 295, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(btnSaveRule)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(chkRuleLocked))
                    .add(tabTemplates, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 689, Short.MAX_VALUE)))
        );
        panelRulesLayout.setVerticalGroup(
            panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, panelRulesLayout.createSequentialGroup()
                .addContainerGap()
                .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, panelRulesLayout.createSequentialGroup()
                        .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(btnScratchpadToRule)
                            .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(jLabel9)
                                .add(txtRuleName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(btnSaveRule)
                                .add(chkRuleLocked))
                            .add(btnRuleToScratchpad))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(panelRulesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(tabTemplates, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)
                            .add(patternsToolbar, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)))
                    .add(jScrollPane3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 361, Short.MAX_VALUE))
                .addContainerGap())
        );

        panelMainTabbed.addTab(resourceMap.getString("panelRules.TabConstraints.tabTitle"), panelRules); // NOI18N

        panelGroups.setName("panelGroups"); // NOI18N

        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setName("jButton2"); // NOI18N

        org.jdesktop.layout.GroupLayout panelGroupsLayout = new org.jdesktop.layout.GroupLayout(panelGroups);
        panelGroups.setLayout(panelGroupsLayout);
        panelGroupsLayout.setHorizontalGroup(
            panelGroupsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(panelGroupsLayout.createSequentialGroup()
                .addContainerGap()
                .add(jButton2)
                .addContainerGap(908, Short.MAX_VALUE))
        );
        panelGroupsLayout.setVerticalGroup(
            panelGroupsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(panelGroupsLayout.createSequentialGroup()
                .addContainerGap()
                .add(jButton2)
                .addContainerGap(349, Short.MAX_VALUE))
        );

        panelMainTabbed.addTab(resourceMap.getString("panelGroups.TabConstraints.tabTitle"), panelGroups); // NOI18N

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel1.setName("jPanel1"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel10.setText(resourceMap.getString("jLabel10.text")); // NOI18N
        jLabel10.setName("jLabel10"); // NOI18N

        txtQuestionTemplate.setText(resourceMap.getString("txtQuestionTemplate.text")); // NOI18N
        txtQuestionTemplate.setName("txtQuestionTemplate"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        txtAnswerTemplate.setText(resourceMap.getString("txtAnswerTemplate.text")); // NOI18N
        txtAnswerTemplate.setName("txtAnswerTemplate"); // NOI18N

        txtCurrentPattern.setText(resourceMap.getString("txtCurrentPattern.text")); // NOI18N
        txtCurrentPattern.setName("txtCurrentPattern"); // NOI18N

        txtCurrentRuleName.setText(resourceMap.getString("txtCurrentRuleName.text")); // NOI18N
        txtCurrentRuleName.setName("txtCurrentRuleName"); // NOI18N

        jScrollPane2.setName("jScrollPane2"); // NOI18N

        textInput.setColumns(20);
        textInput.setRows(5);
        textInput.setName("textInput"); // NOI18N
        jScrollPane2.setViewportView(textInput);

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                        .add(jLabel1)
                        .add(jLabel4))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                        .add(jLabel10)
                        .add(3, 3, 3)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(txtQuestionTemplate, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 206, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(10, 10, 10)
                        .add(jLabel5)
                        .add(4, 4, 4)
                        .add(txtAnswerTemplate))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, txtCurrentPattern)
                    .add(txtCurrentRuleName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 459, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel2)
                    .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 419, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(14, Short.MAX_VALUE)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel10)
                            .add(txtCurrentRuleName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel1)
                            .add(txtCurrentPattern, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(jLabel5)
                                .add(txtAnswerTemplate, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(txtQuestionTemplate, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(jLabel4))))
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout mainPanelLayout = new org.jdesktop.layout.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(mainPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, panelMainTabbed, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 996, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(panelMainTabbed, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 411, Short.MAX_VALUE))
        );

        menuBar.setFont(resourceMap.getFont("menuBar.font")); // NOI18N
        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setFont(resourceMap.getFont("fileMenu.font")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        setRulesFile.setAction(actionMap.get("setRulesFile")); // NOI18N
        setRulesFile.setText(resourceMap.getString("setRulesFile.text")); // NOI18N
        setRulesFile.setName("setRulesFile"); // NOI18N
        fileMenu.add(setRulesFile);

        reloadRulesFile.setAction(actionMap.get("loadRules")); // NOI18N
        reloadRulesFile.setIcon(resourceMap.getIcon("reloadRulesFile.icon")); // NOI18N
        reloadRulesFile.setText(resourceMap.getString("reloadRulesFile.text")); // NOI18N
        reloadRulesFile.setName("reloadRulesFile"); // NOI18N
        fileMenu.add(reloadRulesFile);

        setTreeFolderMenuItem.setAction(actionMap.get("setTreeFolder")); // NOI18N
        setTreeFolderMenuItem.setText(resourceMap.getString("setTreeFolderMenuItem.text")); // NOI18N
        setTreeFolderMenuItem.setName("setTreeFolderMenuItem"); // NOI18N
        fileMenu.add(setTreeFolderMenuItem);

        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        dataMenu.setText(resourceMap.getString("dataMenu.text")); // NOI18N
        dataMenu.setName("dataMenu"); // NOI18N

        specifyDataMenuItem.setAction(actionMap.get("specifyData")); // NOI18N
        specifyDataMenuItem.setText(resourceMap.getString("specifyDataMenuItem.text")); // NOI18N
        specifyDataMenuItem.setName("specifyDataMenuItem"); // NOI18N
        dataMenu.add(specifyDataMenuItem);

        reloadData.setAction(actionMap.get("loadData")); // NOI18N
        reloadData.setIcon(resourceMap.getIcon("reloadData.icon")); // NOI18N
        reloadData.setText(resourceMap.getString("reloadData.text")); // NOI18N
        reloadData.setName("reloadData"); // NOI18N
        dataMenu.add(reloadData);

        menuBar.add(dataMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setName("statusPanel"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        org.jdesktop.layout.GroupLayout statusPanelLayout = new org.jdesktop.layout.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(statusPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(statusPanelLayout.createSequentialGroup()
                        .add(statusMessageLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 15, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(40, 40, 40))
                    .add(statusPanelLayout.createSequentialGroup()
                        .add(statusAnimationLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 841, Short.MAX_VALUE)
                .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(statusPanelLayout.createSequentialGroup()
                .add(statusPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(statusMessageLabel)
                    .add(statusAnimationLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        popupRulesList.setName("popupRulesList"); // NOI18N

        mnuDeleteRule.setAction(actionMap.get("deleteSelectedRule")); // NOI18N
        mnuDeleteRule.setText(resourceMap.getString("mnuDeleteRule.text")); // NOI18N
        mnuDeleteRule.setName("mnuDeleteRule"); // NOI18N
        popupRulesList.add(mnuDeleteRule);

        mnuApplyPrimary.setAction(actionMap.get("applySelectedRuleAsPrimary")); // NOI18N
        mnuApplyPrimary.setName("mnuApplyPrimary"); // NOI18N
        popupRulesList.add(mnuApplyPrimary);

        mnuCloneRule.setAction(actionMap.get("cloneRule")); // NOI18N
        mnuCloneRule.setText(resourceMap.getString("mnuCloneRule.text")); // NOI18N
        mnuCloneRule.setName("mnuCloneRule"); // NOI18N
        popupRulesList.add(mnuCloneRule);

        mnuNewRule.setAction(actionMap.get("newRule")); // NOI18N
        mnuNewRule.setActionCommand(resourceMap.getString("mnuNewRule.actionCommand")); // NOI18N
        mnuNewRule.setName("mnuNewRule"); // NOI18N
        popupRulesList.add(mnuNewRule);

        popupMatchPatterns.setName("popupMatchPatterns"); // NOI18N

        mnuDeleteRule1.setAction(actionMap.get("deleteSelectedRule")); // NOI18N
        mnuDeleteRule1.setText(resourceMap.getString("mnuDeleteRule1.text")); // NOI18N
        mnuDeleteRule1.setName("mnuDeleteRule1"); // NOI18N
        popupMatchPatterns.add(mnuDeleteRule1);

        mnuApplyPrimary1.setAction(actionMap.get("applySelectedRuleAsPrimary")); // NOI18N
        mnuApplyPrimary1.setName("mnuApplyPrimary1"); // NOI18N
        popupMatchPatterns.add(mnuApplyPrimary1);

        mnuCloneRule1.setAction(actionMap.get("cloneRule")); // NOI18N
        mnuCloneRule1.setText(resourceMap.getString("mnuCloneRule1.text")); // NOI18N
        mnuCloneRule1.setName("mnuCloneRule1"); // NOI18N
        popupMatchPatterns.add(mnuCloneRule1);

        mnuNewMatchPattern.setActionCommand(resourceMap.getString("mnuNewMatchPattern.actionCommand")); // NOI18N
        mnuNewMatchPattern.setName("mnuNewMatchPattern"); // NOI18N
        popupMatchPatterns.add(mnuNewMatchPattern);

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * A row has been clicked in the matches table.
     * The row will be displayed as a tree diagram.
     *
     */


    private void panelRulesComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_panelRulesComponentShown
      loadRules();
    }//GEN-LAST:event_panelRulesComponentShown

    @Action
    public void loadRules(){
        String rulesFile = mainApp.prefs.get ("RULES_FILE", "");
    
        this.qgRules = QGRule.fromXML(rulesFile);
    
        ((DefaultListModel)lstRules.getModel()).clear();
            
        for (QGRule rule : qgRules){
            if (!rule.getName().equals(""))
                ((DefaultListModel)lstRules.getModel()).addElement(rule.getName());
            else
                ((DefaultListModel)lstRules.getModel()).addElement("<Unnamed Rule>");
        }
 
        if (lstRules.getModel().getSize() > 0)
            lstRules.setSelectedIndex(0);
    }

    private void lstRulesValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_lstRulesValueChanged
        if (!evt.getValueIsAdjusting())
            ruleToTable();
    }//GEN-LAST:event_lstRulesValueChanged

    private void chkRuleLockedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkRuleLockedActionPerformed
        int rowSelected = lstRules.getSelectedIndex();//GEN-LAST:event_chkRuleLockedActionPerformed
        qgRules.get(rowSelected).setLocked(chkRuleLocked.isSelected());
        saveRules();
    }

    private void lstRulesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lstRulesMouseClicked
        if ( evt.getClickCount() == 2 ) {
            applySelectedRuleAsPrimary ();
            runSearch();
        }
    }//GEN-LAST:event_lstRulesMouseClicked

    private void tableMatchesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tableMatchesMouseClicked

        // Find the row which was clicked
        int selectedRow = this.tableMatches.getSelectedRow();

        if (selectedRow < 0)
            return;

        // Initialises dialog
        if (treeViewBox == null)
            showTreeView();

        // Matchedtrees is populated at search time with all the
        // trees, in the order that they are added to the table
        treeViewBox.mainPanel.setTree(matchedTrees.get(selectedRow));
        treeViewBox.setVisible(true);
    }//GEN-LAST:event_tableMatchesMouseClicked

    private void btnFindMatchesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnFindMatchesActionPerformed

        if (this.btnUseDevelopmentData.isSelected() && !dataSet.devData.isLoaded())
            loadDevData();

        if (this.btnUseTestData.isSelected() && !dataSet.testData.isLoaded())
            loadTestData();

        if (!btnUseDevelopmentData.isSelected() && !btnUseTestData.isSelected())
            lblSearchStatus.setText(String.format ("No data selected.") );
        else
            runSearch();
    }//GEN-LAST:event_btnFindMatchesActionPerformed

    private void loadDevData (){
        dataSet.devData.loadFromFiles(treeFolder, devFiles);
        this.lblDevelopmentStatus.setText(dataSet.devData.Count() + " trees loaded OK.");
    }
    
    private void loadTestData (){
        dataSet.testData.loadFromFiles(treeFolder, testFiles);
        this.lblTestStatus.setText(dataSet.testData.Count() + " trees loaded OK.");
    }

    /**
     * Loads the currently selected rule into the tables.
     */
    private void ruleToTable()
    {
        int rowSelected = lstRules.getSelectedIndex();
    
        if (rowSelected < 0) {
            System.err.println ("No row selected");
            return;
        }
    
        QGRule selectedRule = qgRules.get(rowSelected);
        ((DefaultTableModel)tableMatchPatterns.getModel()).setRowCount(0);
        ((DefaultTableModel)tableQuestionTemplates.getModel()).setRowCount(0);
        ((DefaultTableModel)tableAnswerTemplates.getModel()).setRowCount(0);
    
        txtRuleName.setText(selectedRule.getName());
        chkRuleLocked.setSelected(selectedRule.getLocked());
    
        for (MatchPattern mp : selectedRule.getMatchPatterns())
            ((DefaultTableModel)tableMatchPatterns.getModel()).addRow(mp.toTableRow());
    
        for (QuestionTemplate qt : selectedRule.getQuestionTemplates())
            ((DefaultTableModel)tableQuestionTemplates.getModel()).addRow(qt.toTableRow());
    
        for (AnswerTemplate at : selectedRule.getAnswerTemplates())
            ((DefaultTableModel)tableAnswerTemplates.getModel()).addRow(at.toTableRow());
    }

    /**
     * Reads the table data into the selected rule.
     */
    private void tableToRule()
    {
        int rowSelected = lstRules.getSelectedIndex();
        
        if (rowSelected < 0) {
            System.err.println ("No row selected");
            return;
        }
        
        QGRule selectedRule = qgRules.get(rowSelected);
        
        selectedRule.setName(txtRuleName.getText());
        
        // Clear the current MatchPatterns and read in the table rows
        selectedRule.getMatchPatterns().clear();
        
        int rowCount = ((DefaultTableModel)tableMatchPatterns.getModel()).getRowCount();
        
        for (int row = 0; row < rowCount; row++ ) {
            // TODO: This uses the column index for each item. If it could use the header text for the column
            //  then the order would not matter and less maintenance required if new columns are inserted
            String level    = (String)((DefaultTableModel)tableMatchPatterns.getModel()).getValueAt(row, 0);
            String id       = (String)((DefaultTableModel)tableMatchPatterns.getModel()).getValueAt(row, 1);
            String type     = (String)((DefaultTableModel)tableMatchPatterns.getModel()).getValueAt(row, 2);
            String pattern  = (String)((DefaultTableModel)tableMatchPatterns.getModel()).getValueAt(row, 3);
            
            MatchPattern mp = new MatchPattern(Integer.parseInt(id));
            mp.setLevel(Integer.parseInt(level));
            mp.setType( QGRule.matchTypesFromString(type));
            mp.setPattern(pattern);
            
            selectedRule.addMatchPattern(mp);
        }

        // Clear the current QuestionTemplates and read in the table rows
        selectedRule.getQuestionTemplates().clear();
        
        rowCount = ((DefaultTableModel)tableQuestionTemplates.getModel()).getRowCount();
        
        for (int row = 0; row < rowCount; row++ ) {
            String type = (String)((DefaultTableModel)tableQuestionTemplates.getModel()).getValueAt(row, 0);
            String ref  = (String)((DefaultTableModel)tableQuestionTemplates.getModel()).getValueAt(row, 1);
            
            QuestionTemplate newQT = new QuestionTemplate();
            newQT.setType( QGRule.matchTypesFromString(type));
            newQT.setPattern(ref);
            
            selectedRule.addQuestionTemplate(newQT);
        }

        // Clear the current AnswerTemplates and read in the table rows
        selectedRule.getAnswerTemplates().clear();
        
        rowCount = ((DefaultTableModel)tableAnswerTemplates.getModel()).getRowCount();
        
        for (int row = 0; row < rowCount; row++ ) {
            String type = (String)((DefaultTableModel)tableAnswerTemplates.getModel()).getValueAt(row, 0);
            String ref  = (String)((DefaultTableModel)tableAnswerTemplates.getModel()).getValueAt(row, 1);
            
            AnswerTemplate newAT = new AnswerTemplate();
            newAT.setType( QGRule.matchTypesFromString(type));
            newAT.setPattern(ref);
            
            selectedRule.addAnswerTemplate(newAT);
        }
    
        saveRules();

        // Refresh the list
        panelRulesComponentShown (null);
        lstRules.setSelectedIndex(rowSelected);
    }
    
    /**
     * Update the selected rule from the table.
     */
    @Action
    public void updateSelectedRule() {
        tableToRule();
    }
   
    /**
     * Clone the selected rule,
     */
    @Action
    public void cloneRule() {
        int rowSelected = lstRules.getSelectedIndex();
        
        QGRule selectedRule = new QGRule (qgRules.get(rowSelected));
        selectedRule.setName("Copy of " + selectedRule.getName());
        qgRules.add(selectedRule);
        
        saveRules();
        
        // Refresh the list
        panelRulesComponentShown (null);
        lstRules.setSelectedIndex(rowSelected);
    }

    /**
     * Create a new rule.
     */
    @Action
    public void newRule() {
        int rowSelected = lstRules.getSelectedIndex();
        
        QGRule newRule = new QGRule("New Rule");
        qgRules.add(newRule);
        saveRules();
        
        // Refresh the list
        panelRulesComponentShown (null);
        lstRules.setSelectedIndex(rowSelected);
    }
    
    /**
     * Delete the currently selected rule.
     */
    @Action
    public void deleteSelectedRule() {
        int selectedRule = lstRules.getSelectedIndex();
        
        qgRules.remove(selectedRule);
        ((DefaultListModel)lstRules.getModel()).remove(selectedRule);
        
        saveRules();
    }

    /**
     * Write the rules to the rules file.
     */
    private void saveRules() {
        String rulesFile = mainApp.prefs.get ("RULES_FILE", "");
        QGRule.toXML(rulesFile, qgRules);
    }
    
    /**
     * Copies the currently selected rule up to
     * the main scratch pad for editing.
     */
    @Action
    public void applySelectedRuleAsPrimary () {
        int selectedRule = lstRules.getSelectedIndex();
        
        // Format rule to Regex Expression
        if (selectedRule >= 0 ) {
            QGRule rule = qgRules.get(selectedRule);
        
            System.out.println (rule.getMatchPatternExpression());
            
            txtCurrentPattern.setText   ( rule.getMatchPatternExpression());
            txtQuestionTemplate.setText ( rule.getQuestionTemplateExpression());
            txtAnswerTemplate.setText   ( rule.getAnswerTemplateExpression());
            txtCurrentRuleName.setText  ( rule.getName());
        }
    }

    /**
     *
     */
    @Action
    public void ScratchpadToRule () {
        
        String ruleName = txtCurrentRuleName.getText();
        
        if (ruleName.trim().length() == 0) {
            ruleName = "New Rule";
            txtCurrentRuleName.setText(ruleName);
        }
        
        QGRule newRule = new QGRule (ruleName);
        newRule.matchPatternFromExpression(txtCurrentPattern.getText());
        newRule.questionTemplateFromExpression(txtQuestionTemplate.getText());
        newRule.answerTemplateFromExpression(txtAnswerTemplate.getText());
        
        int rowSelected = lstRules.getSelectedIndex();
        
        boolean bFound = false;
        
        for (int i = 0; i < qgRules.size() && !bFound; i++) {
            if (qgRules.get(i).getName().equals(newRule.getName()) ) {
                System.err.println ("Already exists!");
                
                // If the rule is locked display a dialog box to
                // confirm overwriting it. Otherise just overwrite
                if (qgRules.get(i).getLocked()) {
                    this.yesNoBox("");
                    if (yesNoBox.bRespond)
                        qgRules.set(i, newRule);
                }
                else {
                    qgRules.set(i, newRule);
                }
                
                bFound = true;
            }
        }
        
        if (!bFound)
            qgRules.add(newRule);
        
        saveRules();
        
        loadRules();
        lstRules.setSelectedIndex(rowSelected);
    }

    @Action
    public void addTemplateRow () {
        int rowSelected = lstRules.getSelectedIndex();
    
        if (rowSelected < 0 )
            return;
        
        QGRule selectedRule = qgRules.get(rowSelected);
        
        if (tabTemplates.getTitleAt(tabTemplates.getSelectedIndex()).equals("Match Patterns")) {
            MatchPattern mp = new MatchPattern(1);
            ((DefaultTableModel)tableMatchPatterns.getModel()).addRow(mp.toTableRow());
            selectedRule.addMatchPattern(mp);
            saveRules();
        }    
    }

    /**
     * Begin a search
     */
    private void runSearch() {
    //setTregexState(true); Disable buttons while searching
   
        Thread searchThread = new Thread()
        {
            @Override
            public void run()
            {
                lblSearchStatus.setText("Searching...");
            
                // Initialise search patterns
                final TregexPattern primary =  MatchPattern.getMatchPattern (txtCurrentPattern);

                if (primary == null) {
                    lblSearchStatus.setText("Bad Pattern!");
                    return;
                }
            
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {

                        Treebank treebank = new MemoryTreebank();

                        // Add the test data set if selected and loaded
                        if ( dataSet.testData.isLoaded() && btnUseTestData.isSelected())
                            treebank.addAll(dataSet.testData.getTreebank());
              
                        // Add the development data set if selected and loaded
                        if ( dataSet.testData.isLoaded() && btnUseDevelopmentData.isSelected())
                            treebank.addAll(dataSet.devData.getTreebank());
                    
                        int treeCount = treebank.size();
                        int count = 0;

                        // Copy the current matches
                        diffTrees.clear();
                        diffTrees.addAll(matchedTrees);

                        if (!chkShowPreview.isSelected())
                        {
                            matchedTrees.clear();

                            // Clear the table
                            ((DefaultTableModel)tableMatches.getModel()).setRowCount(0);
                        }
              
                        for ( Tree testTree : treebank ) {
                            count++;
                            lblSearchStatus.setText(String.format ("Searching %d of %d", count, treeCount) );
                            TregexMatcher m = primary.matcher(testTree);
                        
                            //Tree lastMatchingRootNode = null;
                            boolean bMatchFound = false;
      
                            while (m.find() && !bMatchFound) {
                            
                                matchedTrees.add (testTree);
                            
                                if (chkShowTagged.isSelected())
                                    ((DefaultTableModel)tableMatches.getModel()).addRow(getMatcherTableRow(m, testTree, true));
                                else
                                    ((DefaultTableModel)tableMatches.getModel()).addRow(getMatcherTableRow(m, testTree, false));
                                bMatchFound = true;
                            }
                        }

                        if (matchedTrees.size() > 0 )
                            lblSearchStatus.setText(String.format ("Found %d matches.", matchedTrees.size()) );
                        else
                            lblSearchStatus.setText(String.format ("No matches found!") );
                    }
                });
            }
        };
        
        searchThread.start();
 }

    /**
     * Displays the match results in a table with the matched parts
     * formatted.
     *
     * @param m the matcher containing the match results
     * @param matchedTree the tree which was matched
     * @param showTagged whether to show POS tags or not
     * @return the HTML to be displayed in the table row
     */
    private String [] getMatcherTableRow (TregexMatcher m, Tree matchedTree, boolean showTagged ) {
        //List<Tree> allMatches = new ArrayList<Tree>();
        
        // Find matches for templates
        String strQuestion  = QuestionTemplate.getQuestionString ( m, txtQuestionTemplate.getText());
        String strAnswer    = AnswerTemplate.getAnswerString ( m, txtAnswerTemplate.getText());
        
        // Display the full tree in which the match was found
        String strMatchAll = "<html>";
        String lastRef = "";
        
        for (Tree t : matchedTree.getLeaves()) {
            String nodeValue = t.nodeString();
            
            if (nodeValue.startsWith("{Q")) {   // This is a match for the question string
                String ref = nodeValue.substring(2, nodeValue.indexOf("}"));
                nodeValue = nodeValue.substring(nodeValue.indexOf("}") + 1);
                t.setValue(nodeValue);
                
                if (!ref.equals(lastRef))
                    lastRef = ref;
                else
                    ref = "";
                
                if (!showTagged)
                    strMatchAll += "<sup>" + ref + "</sup><b><font color=green>" + nodeValue + "</font></b> " ;
                else
                    strMatchAll += "<sup>" + ref + "</sup><b><font color=green>" + nodeValue + "</font><font color=gray>/" + t.parent(matchedTree).nodeString() + "</font></b> ";
                
            }
            else if (nodeValue.startsWith("{A")) {  // This is a match for the answer string
                String ref = nodeValue.substring(2, nodeValue.indexOf("}"));
                nodeValue = nodeValue.substring(nodeValue.indexOf("}") + 1);
                t.setValue(nodeValue);

                if (!ref.equals(lastRef))
                    lastRef = ref;
                else
                    ref = "";

                if (!showTagged)
                    strMatchAll += "<sup>" + ref + "</sup><b>" + nodeValue + "</b> " ;
                else
                    strMatchAll += "<sup>" + ref + "</sup><b>" + nodeValue + "<font color=gray>/" + t.parent(matchedTree).nodeString() + "</font></b> ";
            }
            else {  // Normal unmatched text
                if (!showTagged)
                    strMatchAll += nodeValue + " " ;
                else
                    strMatchAll += nodeValue + "<font color=gray>/" + t.parent(matchedTree).nodeString() + "</font> ";
            }
        }
        
        strMatchAll += "</html>";
        
        return new String[] {strMatchAll, strQuestion, strAnswer};
        
    }
 
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAdd;
    private javax.swing.JButton btnFindMatches;
    private javax.swing.JButton btnRuleToScratchpad;
    private javax.swing.JButton btnSaveRule;
    private javax.swing.JButton btnScratchpadToRule;
    private javax.swing.JToggleButton btnUseDevelopmentData;
    private javax.swing.JToggleButton btnUseTestData;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JCheckBox chkRuleLocked;
    private javax.swing.JCheckBox chkShowPreview;
    private javax.swing.JCheckBox chkShowTagged;
    private javax.swing.JMenu dataMenu;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JLabel lblDevelopmentStatus;
    private javax.swing.JLabel lblSearchStatus;
    private javax.swing.JLabel lblTestStatus;
    public javax.swing.JList lstRules;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem mnuApplyPrimary;
    private javax.swing.JMenuItem mnuApplyPrimary1;
    private javax.swing.JMenuItem mnuCloneRule;
    private javax.swing.JMenuItem mnuCloneRule1;
    private javax.swing.JMenuItem mnuDeleteRule;
    private javax.swing.JMenuItem mnuDeleteRule1;
    private javax.swing.JMenuItem mnuNewMatchPattern;
    private javax.swing.JMenuItem mnuNewRule;
    private javax.swing.JPanel panelGroups;
    private javax.swing.JTabbedPane panelMainTabbed;
    private javax.swing.JPanel panelRules;
    private javax.swing.JPanel panelSearch;
    private javax.swing.JToolBar patternsToolbar;
    private javax.swing.JPopupMenu popupMatchPatterns;
    private javax.swing.JPopupMenu popupRulesList;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JMenuItem reloadData;
    private javax.swing.JMenuItem reloadRulesFile;
    private javax.swing.JMenuItem setRulesFile;
    private javax.swing.JMenuItem setTreeFolderMenuItem;
    private javax.swing.JMenuItem specifyDataMenuItem;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JTabbedPane tabTemplates;
    private javax.swing.JTable tableAnswerTemplates;
    private javax.swing.JTable tableMatchPatterns;
    private javax.swing.JTable tableMatches;
    private javax.swing.JTable tableQuestionTemplates;
    private javax.swing.JTextArea textInput;
    private javax.swing.JTextField txtAnswerTemplate;
    private javax.swing.JTextField txtCurrentPattern;
    private javax.swing.JTextField txtCurrentRuleName;
    private javax.swing.JTextField txtQuestionTemplate;
    private javax.swing.JTextField txtRuleName;
    // End of variables declaration//GEN-END:variables
    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;
    private JDialog aboutBox;
    private JDialog setTreeFolderBox;
    private JDialog specifyDataBox;
    private dlgTreeView treeViewBox;
    private dlgSelectFile selectFileBox;
    private dlgYesNo yesNoBox;
    private List<QGRule> qgRules;
    private dlgProgress progressBox;
}
