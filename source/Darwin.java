/*
Darwin.java

Copyright (c) 2009-2012, Morgan McGuire
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Timer;
import java.io.File;
import java.io.InputStream;
import java.util.Scanner;
import java.util.Map;

// Tips for debugging threading problems:
//
// * http://java.sun.com/developer/technicalArticles/Programming/Stacktrace/
// * On Unix, C-\ generates a multithreaded stack trace.  On Windows, C-Break.

/**
   Graphical display of the Darwin Game 2.1 simulator for single matches.

   Simple command line:
   <pre>
     run <i>mapfile</i> <i>Creature0</i> <i>Creature1</i> ...
   </pre>


   Detailed command line:
   Run with:
   <pre>
    java -cp .:darwin.jar Darwin [-3D | -2D] [-nosecurity] <i>mapfile</i> <i>Creature0</i> <i>Creature1</i> ...
   </pre>

   e.g.,
   <pre>
    java -cp .:darwin.jar Darwin -3D ns_arena.txt Rover Pirate
   </pre>

   On Windows, change the colon (":") to a semi-colon (";").

   @see Tournament
   @see Simulator

   <p>Morgan McGuire
   <br>Williams College
   <br>http://cs.williams.edu/~morgan
 */
public class Darwin extends JFrame {

    public final static String SYNTAX_HELP = 
        "java Darwin [-3D | -2D] [-nosecurity] mapname Class0 Class1 ...";

    /** Delay time between turns in nanoseconds corresponding to each
        of the speedButtons. */
    final static private long[] delayTime = 
    {100000 * Simulator.MILLISECONDS,
     150 * Simulator.MILLISECONDS, 
     15 * Simulator.MILLISECONDS, 
     1 * Simulator.MILLISECONDS, 
     0};

    //////////////////////////////////////////////////////////////////////

    private static final java.text.DecimalFormat commaFormatter = 
        new java.text.DecimalFormat("###,###,###,###");

    private Simulator        simulator;

    /** GUI elements for setting delay time. */
    private JToggleButton[]  speedButton = new JToggleButton[delayTime.length];

    final private JLabel     timeDisplay = new JLabel("0");

    /** List of all current competitors */
    private ArrayList<Class> remainingCompetitors = new ArrayList<Class>();

    private PopulationGraph  populationGraph;

    private boolean          announcedTimeLimit = false;
    private boolean          announcedHalfTime  = false;

    private Inspector        inspector;
    private JFrame           populationWindow;

    private String           mapFilename;
    private String[]         creatureClassNames;

    final JToggleButton      view2DButton = makeToggleToolButton("2D.gif");
    final JToggleButton      view3DButton = makeToggleToolButton("3D.gif");

    private JLabel           display;

    /** Manages redraw */
    private Timer            timer;

    private boolean          isSecure;

    private boolean          promptIfClassIsOutOfDate = true;

    private boolean          compileIfClassIsOutOfDate = false;

    /** Construct a new instance of the Darwin GUI. */
    public Darwin(boolean view3D, String mapFilename, 
                  String[] creatureClassNames, boolean isSecure) {

        System.out.println("Starting Darwin");
        this.mapFilename = mapFilename;
        this.creatureClassNames = creatureClassNames;
        this.isSecure = isSecure;

        inspector = new Inspector();

        setIconImage(Toolkit.getDefaultToolkit().getImage("icon.png"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Display the simulator
        makeGUI();

        if (view3D) {
            view3DButton.setSelected(true);
        }
        
        if (isSecure) {
            Simulator.beginSecureExecution();
        }
        reload();
    }


    public boolean isSecure() {
        return isSecure;
    }


    private void startTimer() {
        stopTimer();
        // Render at most desiredFPS
        final float desiredFPS = 20;
        timer = new Timer();
        timer.schedule(new java.util.TimerTask() {
                public void run() {
                    tick();
                }}, 0, (int)(1000 / desiredFPS));
    }


    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }


    private synchronized void reload() {
        if (simulator != null) {
            if (simulator.isRunning()) {
                simulator.stop();
            }
            populationWindow.getContentPane().removeAll();
            simulator = null;
            display.setIcon(null);
        }

        stopTimer();

        remainingCompetitors.clear();
        Class[] creatureClasses = new Class[creatureClassNames.length];
        
        for (int i = 0; i < creatureClasses.length; ++i) {
            try {

                final Class c = loadCreatureClass(creatureClassNames[i]);
                
                if (! remainingCompetitors.contains(c)) {
                    remainingCompetitors.add(c);
                }
                creatureClasses[i] = c;
            } catch (Exception e) {
                System.err.println("Warning: while loading " +
                                   creatureClassNames[i]);
                e.printStackTrace();
                creatureClasses[i] = null;
            }

        }

        System.out.println("_________________________________________");
        System.out.println("Creating Simulator");
        simulator = new Simulator(mapFilename, creatureClasses);

        final String maybeSecure = isSecure ? "" : " [NO SECURITY]";
        setTitle("The Darwin Game - " + simulator.getTitle() + maybeSecure);

        simulator.setView3D(view3DButton.isSelected());
        display.setIcon(simulator);

        populationGraph = new PopulationGraph(simulator, creatureClasses);
        populationWindow.getContentPane().add(populationGraph);
        populationWindow.pack();

        speedButton[0].setSelected(true);
        startTimer();
    }


    /** Loads a creature's .class file from disk based on the basename
        (part of the filename before ".class") */
    private Class loadCreatureClass(String basename) 
        throws ClassNotFoundException, java.io.IOException {

        final File classFile     = new File(basename + ".class");
        final File sourceFile    = new File(basename + ".java");

        // Note that lastModified returns 0 if a file does not exist,
        // so a non-existent source file cannot be newer than a class
        // file and an existing source file is always newer than a
        // non-existent class file.
        if (sourceFile.lastModified() > classFile.lastModified()) {

            System.out.println("Warning: " + classFile + " is out of date.");
        
            if (promptIfClassIsOutOfDate) {
                showRecompileDialog(basename);
            }

            if (compileIfClassIsOutOfDate) {
                compile(basename);
            }
        }
        
        return Simulator.loadClass(basename, isSecure);
    }


    /** Called from loadCreatureClass.  Sets compileIfClassIsOutOfDate
        and promptIfClassIsOutOfDate */
    private void showRecompileDialog(String basename) {

        // Build the GUI
        final JDialog d = new JDialog(this, "Darwin: Loading " + basename, true);
        d.setSize(360, 220);
        final JPanel p = new JPanel();
        final JPanel iconPane = new JPanel();
        iconPane.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        iconPane.add(new JLabel(new ImageIcon(basename + "-E.png")));
        final JTextArea label = 
            new JTextArea(basename + 
                          " has been modified since it was compiled.  " +
                          "Do you want to compile it now?");
        label.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
        label.setBackground(p.getBackground());
        label.setEditable(false);
        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setSize(250, 100);
        iconPane.add(label);
        p.add(iconPane);

        final JPanel buttonPane = new JPanel();
        final JButton ignoreButton = new JButton("Ignore");
        ignoreButton.setMnemonic(KeyEvent.VK_CANCEL);
        buttonPane.add(ignoreButton);
        ignoreButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    d.setVisible(false);
                    Darwin.this.compileIfClassIsOutOfDate = false;
                }});
                
        final JButton recompileButton = new JButton("Compile");
        buttonPane.add(recompileButton);
        recompileButton.setMnemonic(KeyEvent.VK_ACCEPT);
        p.add(buttonPane);
        recompileButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    d.setVisible(false);
                    Darwin.this.compileIfClassIsOutOfDate = true;
                }});

        final JCheckBox alwaysCheckbox = 
            new JCheckBox("Always use this answer");

        p.add(alwaysCheckbox);
        d.add(p);

        d.getRootPane().setDefaultButton(recompileButton);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Center and show the dialog
        d.setLocationRelativeTo(null);
        d.setVisible(true);

        promptIfClassIsOutOfDate = ! alwaysCheckbox.isSelected();
    }


    static private String streamToString(InputStream s) {
        try {
            // http://stackoverflow.com/questions/309424/in-java-how-do-a-read-convert-an-inputstream-in-to-a-string
            return new Scanner(s, "UTF-8").useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            return "";
        }
    }

    
    /** Recompiles the file in the current directory whose class name
        is basename.  Prompts the user if there is an error. 
        Called from loadCreatureClass. */
    private void compile(String basename) {
        try {
            boolean ok = false;
            while (! ok) {
                final String cmd = "./compile " + basename + ".java";
                System.out.println(cmd);
                
                final Process javac = Runtime.getRuntime().exec(cmd); 
                
                // Read the child's output
                final String output = 
                    streamToString(javac.getInputStream()) + 
                    streamToString(javac.getErrorStream());

                if (output.length() == 0) {
                    System.out.println("Compiled " + basename + " successfully");
                } else {
                    System.out.println(output);
                }

                // Prompt the user if there was an error
                final int exitValue = javac.exitValue();
                
                ok = (exitValue == 0);

                if (! ok) {
                    final int RECOMPILE = 2, IGNORE = 1, QUIT = 0;
                    final Object[] options = {"Quit", "Ignore", "Recompile"};
                    switch (JOptionPane.showOptionDialog
                            (null,
                             output,
                             "Darwin: Compiling " + basename,
                             JOptionPane.YES_NO_CANCEL_OPTION,
                             JOptionPane.ERROR_MESSAGE,
                             null,
                             options,
                             options[RECOMPILE])) {
                    case RECOMPILE: break;
                    case IGNORE:    ok = true; break;
                    case QUIT:      System.exit(exitValue); break;
                    }
                }
            }

            
        } catch (SecurityException e) {
            // From exec
            System.err.println(e);
        } catch (java.io.IOException e) {
            // From exec
            System.err.println(e);
        }
    }


    /** Reports the simulator version number. */
    static public String getVersion() {
        return Simulator.getVersion();
    }


    /** Called by the timer thread to force repaint of the screen. */
    private void tick() {
        assert (simulator != null):
        "Timer thread running when simulator is null.";

        final long t = simulator.getTime();

        timeDisplay.setText(" " + (t / Simulator.MILLISECONDS) + " ms");

        repaint();

        inspector.tick();

        populationGraph.tick();

        maybeAnnounceTime(t);

        Simulator.Result result = simulator.getResult();
        
        if (result != null) {
            // Game over!
            stopTimer();

            // Pause:
            speedButton[0].setSelected(true);
            simulator.setIntraTurnTime(delayTime[0]);

            if (result.species != null) {
                // Play the victory sound
                final Sound s = 
                    simulator.getSound(result.species, 
                                       Simulator.Condition.VICTORY);
                s.play();
            }

            Sound.say(result.result);

            // Print stats for all creatures
            for (Map.Entry<Class, Simulator.Species> entry : result.speciesStats.entrySet()) {
                final String n = entry.getKey().getName();
                if (! n.equals("Flytrap") && ! n.equals("Apple") && ! n.equals("Treasure")) {
                    System.out.println(entry.getValue());
                    System.out.println();
                }
            }


            // Show dialog
            String message = result.result + "\n" + result.why + "\nin " + 
                (result.timeSteps / Simulator.MILLISECONDS) + " ms";
            System.out.println(message);

            if (result.icon == null) {
                JOptionPane.showMessageDialog
                    (this, message, result.result, JOptionPane.PLAIN_MESSAGE);
            } else {
                JOptionPane.showMessageDialog
                    (this, message, result.result, JOptionPane.PLAIN_MESSAGE, 
                     new ImageIcon(result.icon));
            }
            return;
        }

        // Run elimination messages after checking for a winner so
        // that we don't announce a final elimination
        maybeAnnounceElimination();
    }


    private void maybeAnnounceTime(long t) {
        if (! announcedHalfTime && (t > simulator.timeLimit() * 0.5)) {
            Sound.say("Half-time!");
            announcedHalfTime = true;
        }

        if (! announcedTimeLimit && (t > simulator.timeLimit() * 0.9)) {
            Sound.say("Time limit approaching!");
            announcedTimeLimit = true;
        }
    }


    /** Check to see if a species was eliminated. */
    private void maybeAnnounceElimination() {
        for (int i = 0; i < remainingCompetitors.size(); ++i) {
            Class c = remainingCompetitors.get(i);
            int n = simulator.getCreatureCount(c);
            if (n == 0) {
                Sound.say(c.getName() + " was eliminated.");
                remainingCompetitors.remove(i);
                --i;
            }
        }
    }


    /** Called from constructor. */
    private void makeGUI() {
        Container pane = getContentPane();
        
        JToolBar controls = new JToolBar("Darwin Controls");

        JButton reloadButton = makeToolButton("Redo24.gif");
        controls.add(reloadButton);
        controls.addSeparator(new Dimension(24, 24));
        reloadButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        reload();
                    }});

        {
            view2DButton.setSelected(true);
            view3DButton.setSelected(false);
            ActionListener L = new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        simulator.setView3D(view3DButton.isSelected());
                        Darwin.this.repaint();
                    }};
            view2DButton.addActionListener(L);
            view3DButton.addActionListener(L);
            ButtonGroup group = new ButtonGroup();
            group.add(view2DButton);
            group.add(view3DButton);
            
            controls.add(view2DButton);
            controls.add(view3DButton);
        }

        controls.addSeparator(new Dimension(24, 24));

        {
            ButtonGroup speedGroup = new ButtonGroup();
            ActionListener L = new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        for (int i = 0; i < speedButton.length; ++i) {
                            if (speedButton[i].isSelected()) {
                                simulator.setIntraTurnTime(delayTime[i]);
                            }
                        }
                    }};
            for (int i = 0; i < delayTime.length; ++i) {
                speedButton[i] = makeToggleToolButton("speed" + i + ".gif");
                speedButton[i].addActionListener(L);
                speedGroup.add(speedButton[i]);
                controls.add(speedButton[i]);
            }
            speedButton[0].setSelected(true);
        }

        controls.addSeparator(new Dimension(24, 24));
        JButton inspectorButton = makeToolButton("Information24.gif");
        inspectorButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    inspector.setVisible(true);
                }});
        controls.add(inspectorButton);

        {
            populationWindow = new JFrame("Population");
            populationWindow.setAlwaysOnTop(true);
            populationWindow.setVisible(false);

            final JButton viewPopGraph = makeToolButton("Graph.gif");
            viewPopGraph.setSelected(false);
            ActionListener L = new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        populationWindow.setVisible(true);
                    }};
            viewPopGraph.addActionListener(L);
            controls.add(viewPopGraph);
        }


        controls.addSeparator(new Dimension(40, 24));
        JButton helpButton = makeToolButton("Help24.gif");
        helpButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {

                    JOptionPane.showMessageDialog
                        (Darwin.this, 
                         SYNTAX_HELP + "\n\n" + simulator.getInfo(), "About The Darwin Game", 
                         JOptionPane.PLAIN_MESSAGE, new ImageIcon("Apple.png"));
                }});
        controls.add(helpButton);
        
        
        controls.addSeparator(new Dimension(24, 24));

        final Font fixedFont = new Font("Monospaced", Font.PLAIN, 16);
        timeDisplay.setFont(fixedFont);
        controls.add(new JLabel("Elapsed Time:"));
        controls.add(timeDisplay);


        pane.setLayout(new BorderLayout());
        pane.add(controls, BorderLayout.PAGE_START);
        
        // Map view in the center
        display = new JLabel() {
                public String getToolTipText(MouseEvent event) {
                    return Darwin.this.getDisplayToolTipText(event);
                }};

        // The tool tip must be non-null to trigger a call to
        // getToolTipText
        display.setToolTipText("Darwin");
                    
        pane.add(display, BorderLayout.CENTER);
        display.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    click(e.getX(), e.getY());
                }});

        setSize(1024, 768);
    }


    /** Called by the display */
    private String getDisplayToolTipText(MouseEvent event) {
        final Point p = simulator.screenPointToLocation(event.getX(), event.getY());
        if (p == null) {
            return null;
        } else {
            return simulator.getToolTip(p);
        }
    }


    /** Called when the user clicks on the display */
    private void click(int x, int y) {

        final Point p = simulator.screenPointToLocation(x, y);
        // System.out.println("(" + x + ", " + y + ")");
            
        if (p != null) {
            inspector.setCreature(simulator.getCreature(p.x, p.y));
        } else {
            inspector.setCreature(null);
        }

        repaint();
    }


    static protected JToggleButton makeToggleToolButton(String icon) {
        JToggleButton b = new JToggleButton(new ImageIcon(ClassLoader.getSystemResource(icon)));
        b.setSize(24, 24);
        return b;
    }


    static protected JButton makeToolButton(String icon) {
        JButton b = new JButton(new ImageIcon(ClassLoader.getSystemResource(icon)));
        b.setSize(24, 24);
        return b;
    }


    public static void main(String[] arg) {
        if (arg.length < 1) {
            // Default arguments
            System.out.println(getVersion());
            System.out.println();
            System.out.println("Syntax:");
            System.out.println("     " + SYNTAX_HELP);
            System.out.println();

            String[] s = {"-3D", "ns_faceoff", "Rover", "Pirate"};
            arg = s;
        }
        
        int a = 0;
        boolean view3D = false;
        if (arg[a].toUpperCase().equals("-3D")) {
            view3D = true;
            ++a;
        } else if (arg[a].toUpperCase().equals("-2D")) {
            view3D = false;
            ++a;
        }

        boolean isSecure = true;
        if (arg[a].toLowerCase().equals("-nosecurity")) {
            isSecure = false;
            ++a;
        }

        String mapname = arg[a];
        ++a;
        if ((mapname.length() > 1) && (mapname.charAt(0) == '-')) {
            System.err.println("Illegal option: " + mapname);
            System.err.println("Syntax: " + SYNTAX_HELP);
            return;
        }

        String[] creatureClassNames = new String[arg.length - a];
        System.arraycopy(arg, a, creatureClassNames, 0, creatureClassNames.length);

        new Darwin(view3D, mapname, creatureClassNames, isSecure).setVisible(true);
    }


    private class Inspector extends JFrame {
        private Creature  creature;
        private JLabel    authorLabel;
        private JLabel    classLabel;
        private JLabel    idLabel;
        private JTextArea descriptionDisplay;
        private JLabel    gameTimeLabel;
        private JLabel    iconLabel;
        private JLabel    positionLabel;
        private JLabel    turnLabel;
        private JLabel    timePerTurnLabel;
        private JLabel    computePercentPerTurnLabel;
        private JTextArea stringDisplay;

        public Inspector() {
            super("Creature Inspector");

            setAlwaysOnTop(true);

            JPanel pane = new JPanel();
            getContentPane().add(new JScrollPane(pane));

            pane.setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.FIRST_LINE_START;

            iconLabel = new JLabel();
            
            c.gridx = 0; c.gridy = 0;

            classLabel = new JLabel();
            pane.add(new JLabel("Class:"), c); ++c.gridx;
            pane.add(classLabel, c); ++c.gridx;
            c.gridheight = 4;
            c.anchor = GridBagConstraints.LINE_END;
            pane.add(iconLabel, c);
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            c.gridheight = 1;

            c.gridx = 0; ++c.gridy;
            idLabel = new JLabel();
            pane.add(new JLabel("ID:"), c); ++c.gridx;
            pane.add(idLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            authorLabel = new JLabel();
            pane.add(new JLabel("Author:"), c); ++c.gridx;
            pane.add(authorLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            positionLabel = new JLabel();
            pane.add(new JLabel("Location:"), c); ++c.gridx;
            pane.add(positionLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            gameTimeLabel = new JLabel();
            pane.add(new JLabel("Game Time:"), c); ++c.gridx;
            pane.add(gameTimeLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            turnLabel = new JLabel();
            pane.add(new JLabel("My Turns:"), c); ++c.gridx;
            pane.add(turnLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            timePerTurnLabel = new JLabel();
            pane.add(new JLabel("My Time/Turn:"), c); ++c.gridx;
            pane.add(timePerTurnLabel, c);

            c.gridx = 0; ++c.gridy;
            c.gridwidth = 1;
            computePercentPerTurnLabel = new JLabel();
            pane.add(new JLabel("My Compute Pct:"), c); ++c.gridx;
            pane.add(computePercentPerTurnLabel, c);

            int W = 50;
            int H = 35;

            c.gridx = 0; ++c.gridy; c.gridwidth = 1;
            descriptionDisplay = new JTextArea(3, W/2);
            descriptionDisplay.setEditable(false);
            descriptionDisplay.setLineWrap(true);
            descriptionDisplay.setWrapStyleWord(true);
            pane.add(new JLabel("Description:"), c); ++c.gridx;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            pane.add(new JScrollPane(descriptionDisplay), c);
            c.fill = GridBagConstraints.NONE;

            c.gridx = 0; ++c.gridy; c.gridwidth = 1;
            stringDisplay = new JTextArea(H, W);
            stringDisplay.setFont(new Font("Monospaced", Font.PLAIN, 11));
            pane.add(new JLabel("toString():"), c);  ++c.gridx;
            c.gridwidth = 2;
            JScrollPane scroll = new JScrollPane(stringDisplay);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            pane.add(scroll, c);
            stringDisplay.setEditable(false);

            c.gridx = 1; ++c.gridy; c.gridwidth = 2;
            pane.add(new JLabel("Click on a creature in 2D mode to debug"), c);
            pack();
            setSize(new Dimension(500, 680));
        }


        public synchronized void setCreature(Creature c) {
            creature = c;
            simulator.setSelectedCreature(c);

            if (c != null) {
                setTitle("Inspector - " + c.getClassName() + " " + c.getId());
                
                classLabel.setText(c.getClassName());
                idLabel.setText("" + c.getId());
                authorLabel.setText(c.getAuthorName());
                descriptionDisplay.setText(c.getDescription());
                Image im = simulator.getImage(c, Direction.SOUTH);
                if (im == null) {
                    iconLabel.setIcon(null);
                } else {
                    iconLabel.setIcon(new ImageIcon(im));
                }
                tick();

                setVisible(true);
            } else {
                setTitle("Inspector");
                classLabel.setText("");
                idLabel.setText("");
                authorLabel.setText("");
                descriptionDisplay.setText("");
                stringDisplay.setText("");
                positionLabel.setText("");
                turnLabel.setText("");
                gameTimeLabel.setText("");
                timePerTurnLabel.setText("");
                computePercentPerTurnLabel.setText("");
                iconLabel.setIcon(null);
            }
        }


        public synchronized Creature getCreature() {
            return creature;
        }


        public synchronized void tick() {
            if (creature != null) {
                
                boolean isAlive = simulator.isAlive(creature);

                if (isAlive) {
                    String s = "";
                    try {
                        s = creature.toString();
                    } catch (Exception e) {
                        s = "Exception during toString method:\n\n" + e.toString();
                    }
                    
                    stringDisplay.setText(s);
                    
                    final Point     pos         = simulator.getPosition(creature);
                    final Direction dir         = simulator.getDirection(creature);
                    final long      time        = simulator.getTotalTimeSinceSpawn(creature);
                    final long      compute     = simulator.getComputeTimeSinceSpawn(creature);
                    final long      gameTime    = simulator.getTime();
                    final int       turns       = simulator.getTurns(creature);
                    final long      timePerTurn = time / Math.max(turns, 1);

                    // For the first few turns, timing isn't reliable
                    final int       computePercentPerTurn =
                        ((time <= 0) || (turns < 3)) ? 0 :
                        (int)(100 * compute / time);

                    isAlive = (pos != null && dir != null);

                    if (isAlive) {
                        positionLabel.setText("(" + pos.x + ", " + pos.y + ") " + dir);
                        gameTimeLabel.setText(commaFormatter.format(gameTime) + " ns");
                        turnLabel.setText("" + turns);
                        timePerTurnLabel.setText(commaFormatter.format(timePerTurn) + " ns");
                        computePercentPerTurnLabel.setText(computePercentPerTurn + "%");
                    }
                }

                if (! isAlive) {
                    positionLabel.setText("Not alive.");
                    turnLabel.setText("");
                    gameTimeLabel.setText("");
                    timePerTurnLabel.setText("");
                    computePercentPerTurnLabel.setText("");
                }
            }
        }
    }

}
