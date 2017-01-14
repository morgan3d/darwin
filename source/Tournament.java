/*
Tournament.java

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
import javax.swing.table.*;
import javax.swing.event.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.io.*;

/**
   Graphical display and management of Darwin Game tournaments.

   <pre>
     java Tournament <i>mapfile</i> Creature0 Creature1 ...
   </pre>

   If mapfile begins with "mz_" it is assumed to have one creature on
   it.  If mapfile begins with "ns_" it is assumed to have four
   creatures on it.

   <p> The Tournament class runs each trial with a fresh instance of
   the classes, intentionally reloading them from disk.  This prevents
   classes from sharing any information with themselves between runs
   using static variables.

   @see Darwin
   @see Simulator
 */
public class Tournament extends JFrame implements Icon {
    static final boolean NS_IS_3D = true;
    static final boolean MZ_IS_3D = false;

    /** Randomize the order of competition */
    static final boolean randomize = true;

    /** Frames per second.  Higher numbers look better, lower numbers allow faster simulation */
    static final int FPS   = 25;
        
    private static final int  NUM_MZ_REPEATS = 6;
    private static final int  NUM_NS_REPEATS = 1;

    static final long NS_INTRA_TURN_TIME = (long)(0.25 * Simulator.MILLISECONDS); // nanoseconds

    static final long MZ_INTRA_TURN_TIME = (long)(0.4 * Simulator.MILLISECONDS); // nanoseconds

    // Make the first lap longer for watching strategies
    static final long MZ_INTRA_TURN_TIME_FIRST_LAP = (long)(1.8 * Simulator.MILLISECONDS); // nanoseconds

    /** Description of a creature for the scoreboard. */
    private abstract class Description implements Comparable<Description> {
        public String className;
        public String shortName;
        public String authorName;
        public Icon   icon;

        public ArrayList<Long> times = new ArrayList<Long>();
        
        public Description(String cname) throws ClassNotFoundException, 
                                                InstantiationException, 
                                                IllegalAccessException,
                                                IOException {
            className = cname;
            shortName = Simulator.shortName(cname);
            final Class c = Simulator.loadClass(className, true);
            assert c != null;
            final Creature instance = (Creature)c.newInstance();
            authorName = instance.getAuthorName();

            icon = new ImageIcon(Simulator.getImage(c, Direction.SOUTH));
        }

        abstract public int compareTo(Description obj);

        protected Simulator.Result runTrial(String mapName, 
                                            Class[] creatures,
                                            boolean use3D,
                                            long intraTurnTime) {
            final Simulator simulator = new Simulator(mapName, creatures);

            simulator.setView3D(use3D);
            simulator.setIntraTurnTime(intraTurnTime);

            synchronized (Tournament.this) {
                currentSimulator = simulator;
            }
            Tournament.this.repaint();

            // Run until completion
            Simulator.Result result = simulator.getResult();
            final int sleeptime = (int)(1000.0 / FPS); // ms
            while (result == null) {
                try {
                    Thread.sleep(sleeptime);
                    Tournament.this.repaint();
                } catch (InterruptedException e) {}
                result = simulator.getResult();
            }

            // Force the icon to repaint, showing the result
            Tournament.this.repaint();
            simulator.stop();
            return result;
        }
    }

    
    /** For maze tournaments */
    private class MZDescription extends Description {
        public long    bestTime  = Long.MAX_VALUE;
        public long    worstTime = 0;
        public long    meanTime  = Long.MAX_VALUE;

        public ArrayList<Long> times = new ArrayList<Long>();
        
        public MZDescription(String cname) throws ClassNotFoundException, 
                                                InstantiationException, 
                                                IllegalAccessException,
                                                IOException {
            super(cname);
        }

        public int compareTo(Description obj) {
            final MZDescription that = (MZDescription)obj;
            // Returning the difference cast to an int caused
            // some kind of weird java wraparound bug.
            if (this.meanTime < that.meanTime) {
                return -1;
            } else if (this.meanTime == that.meanTime) {
                return 0;
            } else {
                return 1;
            }
        }
        
        /** Run one trial and update this description with the result. */
        public void runTrial(String mapName, boolean use3D, long intraTurnTime) {
            try {
                showText(shortName + ": ");
                final Class[] creatures = {Simulator.loadClass(className, true)};
                Simulator.Result result = runTrial(mapName, creatures, use3D, intraTurnTime);

                if (result.species != creatures[0]) {
                    // This creature died or timed out
                    showText(formatTime(result.timeSteps) + " (timeout or suicide)\n");

                    // Force the worst-case time
                    result.timeSteps = Simulator.MZ_TIME_LIMIT;
                } else {
                    showText(formatTime(result.timeSteps) + "\n");
                }

                addTime(result.timeSteps);
                
                updateStats(result.speciesStats);

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /** Update times to reflect this change. */
        public void addTime(long time) {
            bestTime  = Math.min(bestTime, time);
            worstTime = Math.max(worstTime, time);
            times.add(time);
            meanTime = 0;
            // Take the mean of the best n-1 times
            for (long t : times) {
                meanTime += t;
            }
            if (times.size() > 1) {
                meanTime -= worstTime;
                meanTime /= (times.size() - 1);
            } else {
                meanTime /= times.size();
            }
        }
    }


    final static private Random rand = new Random();

    public static final int ASCENSION_SCORE               = 3;
    public static final int DOMINATION_SCORE              = 3;
    public static final int EXHAUSTION_MAJORITY_SCORE     = 2;
    public static final int EXHAUSTION_SURVIVAL_SCORE     = 1;
    public static final int LOSE_SCORE                    = 0;

    /** For natural selection tournaments */
    private class NSDescription extends Description {

        public long   bestTime  = Long.MAX_VALUE;
        public long   worstTime = 0;
        public long   meanTime  = Long.MAX_VALUE;

        // Number of times each of these occured:
        public int    ascension;
        public int    domination;
        public int    majority;
        public int    survival;
        public int    lose;

        // Net points:
        public int    score;
        
        public NSDescription(String cname) throws ClassNotFoundException, 
                                                  InstantiationException, 
                                                  IllegalAccessException,
                                                  IOException {
            super(cname);
        }


        public int compareTo(Description obj) {
            NSDescription that = (NSDescription)obj;
            int c = that.score - this.score;
            if (c == 0) {
                // Tie; see who has more wins
                return that.domination - this.domination;
            } else {
                return c;
            }
        }
        

        /** Run one trial for these four competitors (one of which is
            this creature, but that doesn't matter) and update their
            descriptions. */
        public void runTrial(String mapName, NSDescription[] competitors, boolean use3D, long intraTurnTime) {
            assert competitors.length == 4;
            System.out.println("_____________________________________________________");
            try {
                final Class[] creatures = new Class[4];

                for (int i = 0; i < competitors.length; ++i) {
                    creatures[i] = Simulator.loadClass(competitors[i].className, true);
                }

                final Simulator.Result result = runTrial(mapName, creatures, use3D, intraTurnTime);
                
                String outcome = result.result + " " + result.why + ".";

                System.err.println("-----------------------------------------\n");
                System.out.println(outcome);
                showText(outcome + "\n");

                for (int i = 0; i < creatures.length; ++i) {
                    competitors[i].processResult(result.speciesStats.get(creatures[i]).resultCode);
                }
                updateStats(result.speciesStats);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        /**
           Update score for this creature.
        */
        private void processResult(Simulator.Result.Code code) {
            System.err.println(className + " " + code);
            switch (code) {
            case ASCENSION:
                ++ascension;
                score += ASCENSION_SCORE;
                break;
                
            case DOMINATION:
                ++domination;
                score += DOMINATION_SCORE;
                break;
                
            case MAJORITY:
                ++majority;
                score += EXHAUSTION_MAJORITY_SCORE;
                break;

            case SURVIVAL:
                ++survival;
                score += EXHAUSTION_SURVIVAL_SCORE;
                break;

            case LOSE:
                ++lose;
                score += LOSE_SCORE;
                break;

            default:
                assert false;
            }

        }
    }

    private boolean           isMaze;
    private Description[]     creatureArray;
    private Description[]     sortedCreatureArray;
    private String            mapFilename;
    private ResultData        resultData;
    private JProgressBar      progressBar;
    private int               currentTrial;
    private int               numTrials;

    private Simulator         currentSimulator;
    private JTextArea         resultArea;
    private JScrollPane       historyScroll;

    /** To be rendered when the simulator is null */
    private Description       showDescription;

    /** Aggregate stats over the entire tournament.  Uses Strings
        instead of Classes as keys because each Simulator instance
        will load the classes separately. */
    private Map<String, Simulator.Species> speciesStats = new HashMap<String, Simulator.Species>();


    @SuppressWarnings("deprecation")
    private void setIcon() {
        final Image icon = new ImageIcon("Flytrap-E.png").getImage();
        // Windows
        setIconImage(icon);

        // OS X
        if (System.getProperty("os.name").equals("Mac OS X")) {
            //new com.apple.eawt.Application().setDockIconImage(icon);
        }
    }


    /** Called from runTrial to accumulate stats for all creatures involved in the trial. */
    private void updateStats(Map<Class, Simulator.Species> newData) {
        for (Map.Entry<Class, Simulator.Species> entry : newData.entrySet()) {
            final Class             c = entry.getKey();
            final String            n = c.getName();
            final Simulator.Species s = entry.getValue();

            if (! speciesStats.containsKey(n)) {
                speciesStats.put(n, new Simulator.Species(c, 0));
            }
            
            // Total for this species
            final Simulator.Species t = speciesStats.get(n);
            t.accumulateFrom(s);
        }
    }

    public Tournament(String mapfile, String[] creatureClassNames) {
        super("Darwin Game Tournament - " + Simulator.readMapTitle(mapfile));

        setIcon();

        Simulator.beginSecureExecution();

        isMaze = mapfile.contains("mz_");

        creatureArray = new Description[creatureClassNames.length];
        for (int i = 0; i < creatureArray.length; ++i) {
            try {
                
                if (isMaze) {
                    creatureArray[i] = new MZDescription(creatureClassNames[i]);
                } else {
                    creatureArray[i] = new NSDescription(creatureClassNames[i]);
                }

            } catch (Exception e) {

                System.err.println("Error while loading " + creatureClassNames[i] + ":\n" + e);
                e.printStackTrace();
                System.exit(-1);

            }
        }

        sortedCreatureArray = (Description[])creatureArray.clone();
        mapFilename = mapfile;

        // Must happen before GUI is created.
        currentTrial = 0;
        if (isMaze) {
            numTrials = NUM_MZ_REPEATS * creatureArray.length;
        } else {
            // Try every creature against every other (n choose k) = n! / (k! (n - k)!)
            numTrials = 
                NUM_NS_REPEATS * 
                (creatureArray.length * 
                 (creatureArray.length - 1) *
                 (creatureArray.length - 2) *
                 (creatureArray.length - 3)) /
                (4 * 3 * 2);

            System.out.println("Executing " + numTrials + " trials");

            // Update table column names
            columnNames[WLD_COL] = "A:D:m:s:L";
            columnNames[SCORE_COL] = "Score";
        }

        makeGUI();
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        if (isMaze) {
            runAllMZTrials(mapfile);
        } else {
            runAllNSTrials(mapfile);
        }

        progressBar.setVisible(false);
        System.out.println("Trials complete");

    }


    public int getIconHeight() {
        return 800;
    }


    public int getIconWidth() {
        return 1000;
    }


    public synchronized void paintIcon(Component c, Graphics _g, int tx, int ty) {
        Graphics2D g = (Graphics2D)_g;
        if (currentSimulator != null) {
            //final Graphics2D g2D = (Graphics2D)g;
            //final Rectangle rect = c.getBounds();
            // The graphical bounds are (0,0) -
            // (c.getBounds()getWidth() - 1, c.getBounds()getHeight()-1)
            currentSimulator.paintIcon(c, g, 0, 40);
        } else if (showDescription != null) {

            final float scale = 4.0f;
            g.scale(scale, scale);
            showDescription.icon.paintIcon
                (c, g, 
                 (int)(c.getBounds().getWidth() / (2.0f * scale) - showDescription.icon.getIconWidth() / 2), 
                 (int)(c.getBounds().getHeight() / (2.0f * scale) - showDescription.icon.getIconHeight() / 2));
        }
    }

    private void runAllNSTrials(String mapfile) {
        Vector<NSDescription[]> trial = new Vector<NSDescription[]>();

        for (int t = 0; t < NUM_NS_REPEATS; ++t) {

            // Choose the first competitor
            for (int i = 0; i < creatureArray.length; ++i) {

                // Second
                for (int j = i + 1; j < creatureArray.length; ++j) {

                    // Third
                    for (int k = j + 1; k < creatureArray.length; ++k) {

                        // Fourth
                        for (int L = k + 1; L < creatureArray.length; ++L) {

                            final NSDescription[] array = 
                                {(NSDescription)creatureArray[i],
                                 (NSDescription)creatureArray[j],
                                 (NSDescription)creatureArray[k],
                                 (NSDescription)creatureArray[L]};

                            trial.add(array);
                        } // L
                    } // k
                } // j
            } // i

        } // t

        // Randomize
        if (randomize) {
            java.util.Collections.shuffle(trial);
        }

        for (int t = 0; t < trial.size(); ++t) {
            NSDescription[] competitor = trial.get(t);

            // Shuffle the competitors' start positions
            if (randomize) {
                java.util.List<NSDescription> a = Arrays.asList(competitor);
                java.util.Collections.shuffle(a);
                competitor = a.toArray(competitor);
            }

            for (int i = 0; i < competitor.length; ++i) {
                System.out.print(competitor[i].className +  " ");
            }
            System.out.println();

            // Select the four creatures in the GUI
            resultTable.clearSelection();
            for (int j = 0; j < competitor.length; ++j) {
                final int row = findRank(competitor[j]);
                if (row >= 0) {
                    resultTable.addRowSelectionInterval(row, row);
                }
            }

            // Launch the trial
            competitor[0].runTrial(mapfile, competitor, NS_IS_3D, NS_INTRA_TURN_TIME);
            afterTrial(null);
        }

        resultTable.clearSelection();

        // Write out .csv file to disk
        writeStatsCSV();

        final NSDescription winner = (NSDescription)sortedCreatureArray[0];
        showText("\n" + winner.authorName + "'s " + winner.shortName + 
                 " wins with " + winner.score + " total points!\n\n");
        pause(0.5f);
        Sound.say(winner.shortName + " wins the tournament!");

        pause(5.0f);
    }


    /** Called from writeStatsCSV to write a row describing a creature*/
    private void writeStatsHeaderRow(BufferedWriter out) throws IOException {
        out.write("Rank,Creature,Author,Win Ascension,Win Domination,Win Majority,Survival," +
                  "Lose,Score,Size (Bytes),Compute," +
                  "Actions,Look,Move,Turn,Attack,Emit");
        for (Description d : sortedCreatureArray) {
            out.write(",Scored vs. " + d.className);
        }
        out.write("\n");
    }

    private static String escapeCommas(String s) {
        return "\"" + s + "\"";
    }

    /** Called from writeStatsCSV to write a row describing a creature*/
    private void writeStatsRow(int i, BufferedWriter out) throws IOException {
        NSDescription description = (NSDescription)sortedCreatureArray[i];
        out.write("" + (i + 1) + "," + description.className + "," + escapeCommas(description.authorName) + "," + 
                  description.ascension + "," + 
                  description.domination + "," + 
                  description.majority + "," +
                  description.survival + "," + 
                  description.lose + "," +
                  description.score + ",");

        out.write("\n");
    }


    private void writeStatsCSV() {
        try{
            FileWriter fstream = new FileWriter("tournament-stats.csv");
            BufferedWriter out = new BufferedWriter(fstream);

            writeStatsHeaderRow(out);
            for (int i = 0; i < sortedCreatureArray.length; ++i) {
                writeStatsRow(i, out);
            }

            out.close();
            System.out.println("Wrote tournament-stats.csv");
        }catch (Exception e){
            System.err.println("Error: " + e.getMessage());
        }
    }

    static void pause(double seconds) {
        try { Thread.sleep((int)(seconds * 1000.0)); } catch (Exception e) {}
    }

    private void runAllMZTrials(String mapfile) {
        for (Description d : creatureArray) {
            final int row = findRank(d);
            
            if (row >= 0) {
                resultTable.clearSelection();
                resultTable.setRowSelectionInterval(row, row);
            }

            // Display the new trial info
            currentSimulator = null;
            showDescription = d;
            Tournament.this.repaint();
            pause(1.0f);
            Sound.say(d.shortName + " by " + d.authorName);
            pause(3.0f);
            newTrialSound.play();
            pause(0.5f);

            for (int i = 0; i < NUM_MZ_REPEATS; ++i) {
                
                ((MZDescription)d).runTrial(mapfile, MZ_IS_3D, 
                                            (i == 0) ? 
                                            MZ_INTRA_TURN_TIME_FIRST_LAP :
                                            MZ_INTRA_TURN_TIME);


                afterTrial(d);
            }
        }
        showText("Done!\n\n");

        final MZDescription winner = (MZDescription)sortedCreatureArray[0];
        showText(winner.authorName + "'s " + winner.shortName + 
                 " wins with a mean time of " + 
                 formatTime(winner.meanTime) + "!\n\n");

        // Victory lap for the winner
        resultTable.clearSelection();
        resultTable.setRowSelectionInterval(0, 0);
        pause(1.0f);

        Sound.say(winner.shortName + " wins the tournament!");

        pause(3.5f);
        Sound.say("Victory Lap");
        pause(2.5f);
        victoryLapSound.play();
        ((MZDescription)sortedCreatureArray[0]).runTrial(mapfile, true, 15 * Simulator.MILLISECONDS);
    }


    private static Sound newTrialSound  = new Sound("up.wav");
    private static Sound victoryLapSound  = new Sound("victoryLap.wav");
    private static Sound winSound  = new Sound("up.wav");
    private static Sound loseSound = new Sound("down.wav");

    /** Returns the current rank of creature d, or -1 if not found */
    private int findRank(Description d) {
        int i = 0; 
        while (i < sortedCreatureArray.length) {
            if (sortedCreatureArray[i] == d) {
                return i;
            }
            ++i;
        }
        return -1;
    }


    private synchronized void afterTrial(Description d) {
        // Find the current creature
        final int oldRank = findRank(d);

        // Update the data
        Arrays.sort(sortedCreatureArray);
        
        final int newRank = findRank(d);

        /*
        if (oldRank > newRank) {
            // Went down
            loseSound.play();
        } else if (oldRank < newRank) {
            // Went up
            winSound.play();
        }
        */

        resultData.fireTableChanged(new TableModelEvent(resultData));
        ++currentTrial;
        progressBar.setValue(currentTrial);
    }

    private JTable resultTable;

    private void makeGUI() {
        resultData = new ResultData();


        resultTable = new JTable(resultData);
        //final DefaultTableCellRenderer smallFontRenderer = new DefaultTableCellRenderer();
        resultTable.setFont(new Font("Arial Narrow", Font.PLAIN, 12));

        resultTable.setColumnSelectionAllowed(false);
        resultTable.setRowSelectionAllowed(true);

        resultTable.setRowHeight(55);
        resultTable.setShowHorizontalLines(true);
        resultTable.getColumnModel().getColumn(RANK_COL).setPreferredWidth(10);
        resultTable.getColumnModel().getColumn(AUTHOR_COL).setPreferredWidth(90);
        resultTable.getColumnModel().getColumn(CREATURE_COL).setPreferredWidth(45);
        resultTable.getColumnModel().getColumn(IMAGE_COL).setPreferredWidth(40);
        resultTable.getColumnModel().getColumn(BEST_COL).setPreferredWidth(55);
        resultTable.getColumnModel().getColumn(SCORE_COL).setPreferredWidth(20);

        final DefaultTableCellRenderer timeRenderer = new DefaultTableCellRenderer();
        timeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        timeRenderer.setFont(new Font("Monospaced", Font.PLAIN, 14));

        resultTable.getColumnModel().getColumn(BEST_COL).setCellRenderer(timeRenderer);
        resultTable.getColumnModel().getColumn(WORST_COL).setCellRenderer(timeRenderer);
        if (isMaze) {
            resultTable.getColumnModel().getColumn(MEAN_COL).setCellRenderer(timeRenderer);
        }

        progressBar = new JProgressBar(currentTrial, numTrials);

        
        ///////////////////////////////////////////////////////////////////////
        // Right side
        final JPanel resultPane = new JPanel();
        resultPane.setLayout(new GridBagLayout());

        // History box 
        resultArea = new JTextArea(10, 1);
        final JScrollPane historyPane = new JScrollPane(resultArea);

        // Game map image
        final JComponent mapImage = new JLabel(this);

        /////////////////////////////////////////////////////////////////////////
        //
        // Layout:
        //
        //   [ rankingPane |           ] 
        //   [ historyPane |  mapImage ]
        //   [             |           ]
        //   [       progressBar       ]
        //

        resultTable.setPreferredScrollableViewportSize(new Dimension(280, 600));
        final JScrollPane rankingPane =
            new JScrollPane(resultTable);

        final JPanel sideBar = new JPanel(new BorderLayout());
        sideBar.add(rankingPane, BorderLayout.CENTER);
        sideBar.add(historyPane, BorderLayout.SOUTH);

        final Container pane = getContentPane();
        pane.setLayout(new BorderLayout());
        pane.add(sideBar, BorderLayout.WEST);
        pane.add(progressBar, BorderLayout.SOUTH);
        pane.add(mapImage, BorderLayout.CENTER);
    }
    

    void showText(String s) {
        resultArea.append(s);
        // Move the cursor to the beginning of the last line so that it scrolls nicely
        String contents = resultArea.getText();
        int i = contents.lastIndexOf('\n', contents.length() - 2) + 1;
        resultArea.setCaretPosition(Math.max(0, i));
    }


    // For ResultData
    static final private int RANK_COL     = 0;
    static final private int IMAGE_COL    = 1;
    static final private int CREATURE_COL = 2;
    static final private int AUTHOR_COL   = 3;
    static final private int BEST_COL     = 4;
    static final private int WORST_COL    = 5;
    static final private int MEAN_COL     = 6;

    static final private int WLD_COL      = 4;
    static final private int SCORE_COL    = 5;

    /** These are for maze maps; the natural selection map names are
     * set in the Tournament constructor.*/
    static final private String[] columnNames = 
    {"Rank", "Image", "Creature", "Author", "Best Time", "Worst Time", "Mean Time"};

    private class ResultData extends AbstractTableModel {
        public String getColumnName(int col) {
            return columnNames[col];
        }

        public int getColumnCount() {
            if (isMaze) {
                return columnNames.length; 
            } else {
                return columnNames.length - 1; 
            }
        }

        public Class getColumnClass(int col) {
            if (col == IMAGE_COL) {
                return Icon.class;
            } else {
                return String.class;
            }
        }

        public int getRowCount() { 
            return creatureArray.length;
        }

        public Object getValueAt(int row, int col) {
            final Description d = sortedCreatureArray[row];
            switch (col) {
            case RANK_COL:
                return "" + (row + 1);

            case CREATURE_COL:
                return d.className;

            case IMAGE_COL:
                return d.icon;

            case AUTHOR_COL:
                return d.authorName;

            case BEST_COL: // also WLD_COL
                if (isMaze) {
                    if (((MZDescription)d).bestTime == Long.MAX_VALUE) {
                        return "";
                    } else {
                        return formatTime(((MZDescription)d).bestTime);
                    }
                } else {
                    NSDescription n = (NSDescription)d;
                    return "" + n.ascension + ":" + n.domination + ":" + n.majority + ":" + n.survival + ":" + n.lose;
                }

            case WORST_COL: // also SCORE_COL
                if (isMaze) {
                    if (((MZDescription)d).bestTime == Long.MAX_VALUE) {
                        return "";
                    } else {
                        return formatTime(((MZDescription)d).worstTime);
                    }
                } else {
                    return "" + ((NSDescription)d).score;
                }

            case MEAN_COL:
                if (((MZDescription)d).bestTime == Long.MAX_VALUE) {
                    return "";
                } else {
                    return formatTime(((MZDescription)d).meanTime);
                }
                
            }

            return "";
        }
    }

    
    private static final java.text.DecimalFormat commaFormatter = 
        new java.text.DecimalFormat("###,###,###,###");


    /** Formats a time in nanoseconds */
    static String formatTime(long time) {
        return commaFormatter.format(time / Simulator.MILLISECONDS) + "ms";
    }


    public static void main(String[] arg) {
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Hello World!");

        String mapfile = arg[0];
        String[] creatures = new String[arg.length - 1];
        for (int c = 1; c < arg.length; ++c) {
            creatures[c - 1] = arg[c];
        }

        new Tournament(mapfile, creatures);
    }

    
}
